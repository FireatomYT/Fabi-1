package dev.fireatom.FABI;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

import dev.fireatom.FABI.base.command.CommandClient;
import dev.fireatom.FABI.base.command.CommandClientBuilder;
import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.waiter.EventWaiter;
import dev.fireatom.FABI.blacklist.Blacklist;
import dev.fireatom.FABI.contracts.scheduler.Job;
import dev.fireatom.FABI.listeners.*;
import dev.fireatom.FABI.listeners.*;
import dev.fireatom.FABI.menus.ActiveModlogsMenu;
import dev.fireatom.FABI.menus.ModlogsMenu;
import dev.fireatom.FABI.menus.ReportMenu;
import dev.fireatom.FABI.middleware.MiddlewareHandler;
import dev.fireatom.FABI.middleware.PermissionsCheck;
import dev.fireatom.FABI.middleware.ThrottleMiddleware;
import dev.fireatom.FABI.middleware.HasAccess;
import dev.fireatom.FABI.objects.ExitCodes;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.objects.constants.Names;
import dev.fireatom.FABI.scheduler.ScheduleHandler;
import dev.fireatom.FABI.utils.*;
import dev.fireatom.FABI.utils.*;
import dev.fireatom.FABI.utils.database.DBUtil;
import dev.fireatom.FABI.utils.file.FileManager;
import dev.fireatom.FABI.utils.file.lang.LocaleUtil;
import dev.fireatom.FABI.utils.imagegen.UserBackgroundHandler;
import dev.fireatom.FABI.utils.level.LevelUtil;
import dev.fireatom.FABI.utils.logs.GuildLogger;
import dev.fireatom.FABI.utils.logs.LogEmbedUtil;
import dev.fireatom.FABI.utils.message.EmbedUtil;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import net.dv8tion.jda.internal.utils.Checks;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;

import static java.lang.Long.parseLong;

public class App {
	protected static App instance;
	private final Settings settings;
	
	private static final Logger LOG = (Logger) LoggerFactory.getLogger(App.class);

	public final JDA JDA;
	private final CommandClient commandClient;
	private final EventWaiter eventWaiter;

	private final FileManager fileManager = new FileManager();

	private final GuildLogger guildLogger;
	private final LogEmbedUtil logEmbedUtil;

	private final DBUtil dbUtil;
	private final EmbedUtil embedUtil;
	private final CheckUtil checkUtil;
	private final LocaleUtil localeUtil;
	private final TicketUtil ticketUtil;
	private final GroupHelper groupHelper;
	private final ModerationUtil moderationUtil;
	private final LevelUtil levelUtil;

	private final Blacklist blacklist;

	private Instant shutdownTime = null;
	private ExitCodes shutdownCode = ExitCodes.RESTART;

	@SuppressWarnings("BusyWait")
	public App(Settings settings) throws IOException {
		App.instance = this;
		this.settings = settings;

		System.out.println(AppInfo.getVersionInfo());

		LOG.debug("Starting VOTL instance with debug logging enabled!\n");

		fileManager.addFile("config", "/config.example.json", Constants.DATA_PATH + "config.json")
			.addFile("database", "/server.db", Constants.DATA_PATH + "server.db")
			.addFileUpdate("backgrounds", "/backgrounds/index.json", Constants.DATA_PATH + "backgrounds" + Constants.SEPAR + "main.json")
			.addLang("en-GB")
			.addLang("ru");

		Checks.notBlank(fileManager.getNullableString("config", "bot-token"), "Token inside config.example.json");
		final long ownerId = parseLong(fileManager.getNullableString("config", "owner-id"));
		
		// Define for default
		dbUtil		= new DBUtil(getFileManager());
		localeUtil	= new LocaleUtil(this);
		embedUtil	= new EmbedUtil(localeUtil);
		checkUtil	= new CheckUtil(this, ownerId);
		ticketUtil	= new TicketUtil(this);
		moderationUtil = new ModerationUtil(dbUtil, localeUtil);
		levelUtil	= new LevelUtil(this);

		logEmbedUtil	= new LogEmbedUtil();
		guildLogger		= new GuildLogger(this, logEmbedUtil);
		groupHelper		= new GroupHelper(this);

		eventWaiter = new EventWaiter();

		CommandListener commandListener = new CommandListener(localeUtil);
		InteractionListener interactionListener = new InteractionListener(this, eventWaiter);

		GuildListener guildListener = new GuildListener(this);
		VoiceListener voiceListener = new VoiceListener(this);
		ModerationListener moderationListener = new ModerationListener(this);
		AuditListener auditListener = new AuditListener(dbUtil, guildLogger);
		MemberListener memberListener = new MemberListener(this);
		MessageListener messageListener = new MessageListener(this);
		EventListener eventListener = new EventListener(dbUtil);

		LOG.info("Preparing blacklist");
		blacklist = new Blacklist(this);
		blacklist.syncBlacklistWithDatabase();

		// Define a command client
		CommandClientBuilder commandClientBuilder = new CommandClientBuilder()
			.setOwnerId(ownerId)
			.setStatus(OnlineStatus.ONLINE)
			.setActivity(Activity.customStatus("/help"))
			.addContextMenus(
				new ReportMenu(),
				new ModlogsMenu(),
				new ActiveModlogsMenu()
			)
			.setListener(commandListener)
			.setBlacklist(blacklist)
			.setDevGuildIds(fileManager.getStringList("config", "dev-servers").toArray(new String[0]));

		LOG.info("Registering default middlewares");
		MiddlewareHandler.initialize(this);
		MiddlewareHandler.register("throttle", new ThrottleMiddleware(this));
		MiddlewareHandler.register("hasAccess", new HasAccess(this));
		MiddlewareHandler.register("permissions", new PermissionsCheck(this));

		LOG.info("Registering commands");
		AutoloaderUtil.load(Names.PACKAGE_COMMAND_PATH, command -> commandClientBuilder.addSlashCommands((SlashCommand) command), false);

		commandClient = commandClientBuilder.build();
		// Build
		AutoCompleteListener acListener = new AutoCompleteListener(commandClient, dbUtil);

		final Set<GatewayIntent> intents = Set.of(
			GatewayIntent.GUILD_EXPRESSIONS,
			GatewayIntent.GUILD_INVITES,
			GatewayIntent.GUILD_MEMBERS,
			GatewayIntent.GUILD_MESSAGES,
			GatewayIntent.GUILD_MODERATION,
			GatewayIntent.GUILD_VOICE_STATES,
			GatewayIntent.GUILD_WEBHOOKS,
			GatewayIntent.MESSAGE_CONTENT
		);
		final Set<CacheFlag> enabledCacheFlags = Set.of(
			CacheFlag.EMOJI,
			CacheFlag.MEMBER_OVERRIDES,
			CacheFlag.STICKER,
			CacheFlag.ROLE_TAGS,
			CacheFlag.VOICE_STATE
		);
		final Set<CacheFlag> disabledCacheFlags = Set.of(
			CacheFlag.ACTIVITY,
			CacheFlag.CLIENT_STATUS,
			CacheFlag.ONLINE_STATUS,
			CacheFlag.SCHEDULED_EVENTS
		);

		JDABuilder mainBuilder = JDABuilder.create(fileManager.getNullableString("config", "bot-token"), intents)
			.setMemberCachePolicy(MemberCachePolicy.ALL)	// cache all members
			.setChunkingFilter(ChunkingFilter.ALL)			// enable chunking
			.enableCache(enabledCacheFlags)
			.disableCache(disabledCacheFlags)
			.setBulkDeleteSplittingEnabled(false)
			.addEventListeners(
				commandClient, eventWaiter, acListener, interactionListener,
				guildListener, voiceListener, moderationListener, messageListener,
				auditListener, memberListener, eventListener
			);
			
		JDA tempJda;

		// try to log in
		int retries = 4; // how many times will it try to build
		int cooldown = 8; // in seconds; cooldown amount, will doubles after each retry
		while (true) {
			try {
				tempJda = mainBuilder.build();
				break;
			} catch (IllegalArgumentException | InvalidTokenException ex) {
				LOG.error("Login failed due to Token", ex);
				System.exit(ExitCodes.ERROR.code);
			} catch (ErrorResponseException ex) { // Tries to reconnect to discord x times with some delay, else exits
				if (retries > 0) {
					retries--;
					LOG.info("Retrying connecting in {} seconds... {} more attempts", cooldown, retries);
					try {
						Thread.sleep(cooldown*1000L);
					} catch (InterruptedException e) {
						LOG.error("Thread sleep interrupted", e);
					}
					cooldown*=2;
				} else {
					LOG.error("No network connection or couldn't connect to DNS", ex);
					System.exit(ExitCodes.ERROR.code);
				}
			}
		}

		this.JDA = tempJda;

		// logger
		createWebhookAppender();


		AutoloaderUtil.load(Names.PACKAGE_JOB_PATH, job -> ScheduleHandler.registerJob((Job) job));
		LOG.info("Registered {} jobs successfully!", ScheduleHandler.entrySet().size());

		LOG.info("Loading user backgrounds");
		UserBackgroundHandler.getInstance().start();

		LOG.info("Completed building\n");
	}

	public static App getInstance() {
		return instance;
	}

	public Settings getSettings() {
		return settings;
	}

	public CommandClient getClient() {
		return commandClient;
	}

	public static Logger getLogger() {
		return LOG;
	}

	public FileManager getFileManager() {
		return fileManager;
	}

	public DBUtil getDBUtil() {
		return dbUtil;
	}

	public EmbedUtil getEmbedUtil() {
		return embedUtil;
	}

	public CheckUtil getCheckUtil() {
		return checkUtil;
	}

	public LocaleUtil getLocaleUtil() {
		return localeUtil;
	}

	public GuildLogger getGuildLogger() {
		return guildLogger;
	}

	public LogEmbedUtil getLogEmbedUtil() {
		return logEmbedUtil;
	}

	public TicketUtil getTicketUtil() {
		return ticketUtil;
	}

	public GroupHelper getHelper() {
		return groupHelper;
	}

	public ModerationUtil getModerationUtil() {
		return moderationUtil;
	}

	public LevelUtil getLevelUtil() {
		return levelUtil;
	}

	public Blacklist getBlacklist() {
		return blacklist;
	}

	public EventWaiter getEventWaiter() {
		return eventWaiter;
	}

	public void scheduleShutdown(Instant time, ExitCodes exitCode) {
		shutdownTime = time;
		shutdownCode = exitCode;
	}

	public Instant getShutdownTime() {
		return shutdownTime;
	}

	public ExitCodes getShutdownCode() {
		return shutdownCode;
	}

	public void shutdown() {
		shutdown(ExitCodes.RESTART);
	}

	public void shutdown(ExitCodes exitCode) {
		getLogger().info("Shutting down instance with exit code {}", exitCode.code);

		JDA.shutdown();

		for (var future : ScheduleHandler.entrySet()) {
			future.cancel(false);
		}

		try {
			Thread.sleep(3000L);
		} catch (InterruptedException e) {
			getLogger().error("Thread sleep interrupted", e);
		}

		JDA.shutdownNow();

		for (var future : ScheduleHandler.entrySet()) {
			future.cancel(true);
		}

		System.exit(exitCode.code);
	}

	private void createWebhookAppender() {
		String url = getFileManager().getNullableString("config", "webhook");
		if (url == null) return;
		
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
		PatternLayoutEncoder ple = new PatternLayoutEncoder();
		ple.setPattern("%d{dd.MM.yyyy HH:mm:ss} [%thread] [%logger{0}] %ex{10}%n");
		ple.setContext(lc);
		ple.start();
		WebhookAppender webhookAppender = new WebhookAppender();
		webhookAppender.setUrl(url);
		webhookAppender.setEncoder(ple);
		webhookAppender.setContext(lc);
		webhookAppender.start();

		Logger logbackLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		logbackLogger.addAppender(webhookAppender);
		logbackLogger.setAdditive(false);
	}
}
