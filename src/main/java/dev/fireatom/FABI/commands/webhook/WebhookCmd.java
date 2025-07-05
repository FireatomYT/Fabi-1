package dev.fireatom.FABI.commands.webhook;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.Limits;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.WebhookType;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

public class WebhookCmd extends SlashCommand {

	public WebhookCmd() {
		this.name = "webhook";
		this.path = "bot.webhook";
		this.children = new SlashCommand[]{
			new ShowList(), new Create(), new Select(),
			new Remove(), new Move(), new Here()
		};
		this.botPermissions = new Permission[]{Permission.MANAGE_WEBHOOKS};
		this.category = CmdCategory.WEBHOOK;
		this.module = CmdModule.WEBHOOK;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event)	{}

	private class ShowList extends SlashCommand {
		public ShowList() {
			this.name = "list";
			this.path = "bot.webhook.list";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "all", lu.getText(path+".all.help"))
			);
			this.ephemeral = true;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());

			boolean listAll = event.optBoolean("all", false);

			EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getGuildText(event, path+".embed.title"));
			
			// Retrieves every webhook in server
			guild.retrieveWebhooks().queue(webhooks -> {
				// Remove FOLLOWER type webhooks
				webhooks = webhooks.stream().filter(wh -> wh.getType().equals(WebhookType.INCOMING)).collect(Collectors.toList());

				// If there is any webhook and only saved in DB are to be shown
				if (!listAll) {
					// Keeps only saved in DB type Webhook objects
					List<Long> regWebhookIDs = bot.getDBUtil().webhook.getWebhookIds(guild.getIdLong());
						
					webhooks = webhooks.stream().filter(wh -> regWebhookIDs.contains(wh.getIdLong())).collect(Collectors.toList());
				}

				if (webhooks.isEmpty()) {
					embedBuilder.setDescription(
						lu.getGuildText(event, (listAll ? path+".embed.none_found" : path+".embed.none_registered"))
					);
				} else {
					String title = lu.getGuildText(event, path+".embed.value");
					StringBuilder text = new StringBuilder();
					for (Webhook wh : webhooks) {
						if (text.length() > 790) { // max characters for field value = 1024, and max for each line = ~226, so at least 4.5 lines fits in one field
							embedBuilder.addField(title, text.toString(), false);
							title = "\u200b";
							text.setLength(0);
						}
						text.append(String.format("%s | `%s` | %s\n", wh.getName(), wh.getId(), wh.getChannel().getAsMention()));
					}

					embedBuilder.addField(title, text.toString(), false);
				}

				editEmbed(event, embedBuilder.build());
			});
		}
	}

	private class Create extends SlashCommand {
		public Create() {
			this.name = "create";
			this.path = "bot.webhook.add.create";
			this.options = List.of(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true)
					.setRequiredLength(2, 100),
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"))
					.setChannelTypes(ChannelType.TEXT)
			);
			this.subcommandGroup = new SubcommandGroupData("add", lu.getText("bot.webhook.add.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			if (bot.getDBUtil().webhook.countWebhooks(event.getGuild().getIdLong()) >= Limits.WEBHOOKS) {
				editErrorLimit(event, "webhooks", Limits.WEBHOOKS);
				return;
			}

			String setName = event.optString("name", "Default name").trim();
			GuildChannel channel = event.optGuildChannel("channel", event.getGuildChannel());

			if (setName.isEmpty() || setName.length() > 100) {
				editError(event, path+".invalid_range");
				return;
			}

			try {
				// DYK, guildChannel doesn't have WebhookContainer! no shit
				event.getGuild().getTextChannelById(channel.getId()).createWebhook(setName).reason("By "+event.getUser().getName()).queue(
					webhook -> {
						try {
							bot.getDBUtil().webhook.add(webhook.getIdLong(), webhook.getGuild().getIdLong(), webhook.getToken());
						} catch (SQLException ex) {
							editErrorDatabase(event, ex, "add created webhook");
							return;
						}
						editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
							.setDescription(lu.getGuildText(event, path+".done", webhook.getName()))
							.build()
						);
					}
				);
			} catch (PermissionException ex) {
				editPermError(event, ex.getPermission(), true);
			}
		}
	}

	private class Select extends SlashCommand {
		public Select() {
			this.name = "select";
			this.path = "bot.webhook.add.select";
			this.options = List.of(
				new OptionData(OptionType.STRING, "id", lu.getText(path+".id.help"), true)
					.setRequiredLength(10, 30)
			);
			this.subcommandGroup = new SubcommandGroupData("add", lu.getText("bot.webhook.add.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			if (bot.getDBUtil().webhook.countWebhooks(event.getGuild().getIdLong()) >= Limits.WEBHOOKS) {
				editErrorLimit(event, "webhooks", Limits.WEBHOOKS);
				return;
			}

			long webhookId = Long.parseLong(event.optString("id"));

			try {
				event.getJDA().retrieveWebhookById(webhookId).queue(
					webhook -> {
						if (bot.getDBUtil().webhook.exists(webhookId)) {
							editError(event, path+".error_registered");
						} else {
							try {
								bot.getDBUtil().webhook.add(webhook.getIdLong(), webhook.getGuild().getIdLong(), webhook.getToken());
							} catch (SQLException ex) {
								editErrorDatabase(event, ex, "add selected webhook");
								return;
							}
							editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
								.setDescription(lu.getGuildText(event, path+".done", webhook.getName()))
								.build()
							);
						}
					}, failure -> editError(event, path+".error_not_found", failure.getMessage())
				);
			} catch (IllegalArgumentException ex) {
				editError(event, path+".error_not_found", ex.getMessage());
			}
		}
	}

	private class Remove extends SlashCommand {
		public Remove() {
			this.name = "remove";
			this.path = "bot.webhook.remove";
			this.options = List.of(
				new OptionData(OptionType.STRING, "id", lu.getText(path+".id.help"), true)
					.setRequiredLength(10, 30),
				new OptionData(OptionType.BOOLEAN, "delete", lu.getText(path+".delete.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			long webhookId = Long.parseLong(event.optString("id"));
			boolean delete = event.optBoolean("delete", false);

			try {
				event.getJDA().retrieveWebhookById(webhookId).queue(
					webhook -> {
						if (!bot.getDBUtil().webhook.exists(webhookId)) {
							editError(event, path+".error_not_registered");
						} else {
							if (webhook.getGuild().equals(event.getGuild())) {
								if (delete) {
									webhook.delete(webhook.getToken()).reason("By "+event.getUser().getName()).queue();
								}
								try {
									bot.getDBUtil().webhook.remove(webhookId);
								} catch (SQLException ex) {
									editErrorDatabase(event, ex, "delete webhook");
									return;
								}
								editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
									.setDescription(lu.getGuildText(event, path+".done", webhook.getName()))
									.build()
								);
							} else {
								editError(event, path+".error_not_guild",
									String.format("Selected webhook guild: %s", webhook.getGuild().getName()));
							}
						}
					},
					failure -> editError(event, path+".error_not_found", failure.getMessage())
				);
			} catch (IllegalArgumentException ex) {
				editError(event, path+".error_not_found", ex.getMessage());
			}
		}
	}

	private class Move extends SlashCommand {
		public Move() {
			this.name = "move";
			this.path = "bot.webhook.move";
			this.options = List.of(
				new OptionData(OptionType.STRING, "id", lu.getText(path+".id.help"), true)
					.setRequiredLength(10, 30),
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
			);
			this.ephemeral = true;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			long webhookId = Long.parseLong(event.optString("id"));
			GuildChannel channel = event.optGuildChannel("channel");

			if (!channel.getType().equals(ChannelType.TEXT)) {
				editError(event, path+".error_channel", "Selected channel is not Text Channel.");
				return;
			}

			TextChannel textChannel = guild.getTextChannelById(channel.getId());
			if (textChannel == null) {
				editError(event, path+".error_channel", "Selected channel not found in this server.\nChannel ID: `%s`".formatted(channel.getId()));
				return;
			}

			event.getJDA().retrieveWebhookById(webhookId).queue(
				webhook -> {
					if (bot.getDBUtil().webhook.exists(webhookId)) {
						try {
							bot.getDBUtil().guildSettings.setLastWebhookId(guild.getIdLong(), webhookId);
						} catch (SQLException ex) {
							editErrorDatabase(event, ex, "set last webhook");
							return;
						}
						webhook.getManager().setChannel(textChannel).reason("By "+event.getUser().getName()).queue(
							wm -> {
								editEmbed(event,bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
									.setDescription(lu.getGuildText(event, path+".done",
										webhook.getName(), channel.getAsMention()
									))
									.build()
								);
							},
							failure -> editErrorOther(event, failure.getMessage())
						);
					} else {
						editError(event, path+".error_not_registered");
					}
				}, failure -> editError(event, path+".error_not_found", failure.getMessage())
			);
		}
	}

	private class Here extends SlashCommand {
		public Here() {
			this.name = "here";
			this.path = "bot.webhook.here";
			this.ephemeral = true;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();

			Long webhookId = bot.getDBUtil().getGuildSettings(guild).getLastWebhookId();
			if (webhookId == null) {
				editError(event, path+".id_null");
				return;
			}
			
			GuildChannel channel = event.getGuildChannel();
			if (!channel.getType().equals(ChannelType.TEXT)) {
				editError(event, path+".error_channel", "Selected channel is not Text Channel");
				return;
			}

			event.getJDA().retrieveWebhookById(webhookId).queue(
				webhook -> {
					if (bot.getDBUtil().webhook.exists(webhookId)) {
						webhook.getManager().setChannel(guild.getTextChannelById(channel.getId())).reason("By "+event.getUser().getName()).queue(
							wm -> {
								editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
									.setDescription(lu.getGuildText(event, path+".done",
										webhook.getName(), channel.getAsMention()
									))
									.build()
								);
							},
							failure -> editErrorOther(event, failure.getMessage())
						);
					} else {
						editError(event, path+".error_not_registered");
					}
				}, failure -> editError(event, path+".error_not_found", failure.getMessage())
			);
		}
	}

}
