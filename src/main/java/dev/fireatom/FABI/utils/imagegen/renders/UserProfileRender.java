package dev.fireatom.FABI.utils.imagegen.renders;

import dev.fireatom.FABI.utils.RandomUtil;
import dev.fireatom.FABI.utils.file.lang.LocaleUtil;
import dev.fireatom.FABI.utils.imagegen.Fonts;
import dev.fireatom.FABI.utils.imagegen.UserBackground;
import dev.fireatom.FABI.utils.message.MessageUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@SuppressWarnings("UnusedReturnValue")
public class UserProfileRender extends Renderer {
	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final String globalName, userName, avatarUrl;
	private final OffsetDateTime timeCreated, timeJoined;

	private long textLevel = -1;
	private long textExperience = -1;
	private long textLevelXp = -1;
	private long textXpDiff = -1;
	private double textPercentage = -1;
	private String textRank = null;

	private long voiceLevel = -1;
	private long voiceExperience = -1;
	private long voiceLevelXp = -1;
	private long voiceXpDiff = -1;
	private double voicePercentage = -1;
	private String voiceRank = null;

	private long globalExperience = -1;

	final private boolean minimized = true;

	private LocaleUtil lu;
	private DiscordLocale locale;
	private UserBackground background = null;

	public UserProfileRender(@NotNull Member member) {
		this.globalName = member.getEffectiveName().replaceAll("[\\p{So}\\p{Cn}]", "").strip(); // Remove emojis
		this.userName = member.getUser().getName();
		this.avatarUrl = member.getEffectiveAvatarUrl();
		this.timeCreated = member.getUser().getTimeCreated();
		this.timeJoined = member.getTimeJoined();
	}

	public UserProfileRender setBackground(@Nullable UserBackground background) {
		this.background = background;
		return this;
	}

	public UserProfileRender setLocale(@NotNull LocaleUtil lu, @NotNull DiscordLocale locale) {
		this.lu = lu;
		this.locale = locale;
		return this;
	}

	public UserProfileRender setLevel(long textLevel, long voiceLevel) {
		this.textLevel = textLevel;
		this.voiceLevel = voiceLevel;
		return this;
	}

	public UserProfileRender setXpDiff(long textMaxXp, long voiceMaxXp) {
		this.textXpDiff = textMaxXp;
		this.voiceXpDiff = voiceMaxXp;
		return this;
	}

	public UserProfileRender setTotalExperience(long textExperience, long voiceExperience) {
		this.textExperience = textExperience;
		this.voiceExperience = voiceExperience;
		return this;
	}

	public UserProfileRender setCurrentLevelExperience(long textLevelXp, long voiceLevelXp) {
		this.textLevelXp = textLevelXp;
		this.voiceLevelXp = voiceLevelXp;
		return this;
	}

	public UserProfileRender setPercentage(double textPercentage, double voicePercentage) {
		this.textPercentage = textPercentage;
		this.voicePercentage = voicePercentage;
		return this;
	}

	public UserProfileRender setServerRank(String textRank, String voiceRank) {
		this.textRank = textRank;
		this.voiceRank = voiceRank;
		return this;
	}

	public UserProfileRender setGlobalExperience(long globalExperience) {
		this.globalExperience = globalExperience;
		return this;
	}

	@Override
	public boolean canRender() {
		return background != null
			&& globalExperience > -1
			&& textRank != null
			&& voiceRank != null;
	}

	@Override
	protected BufferedImage handleRender() throws IOException {
		URLConnection connection = URI.create(avatarUrl).toURL().openConnection();
		connection.setRequestProperty("user-Agent", "VOTL-Discord-Bot");

		BufferedImage backgroundImage = loadAndBuildBackground();

		// Creates our graphics and prepares it for use.
		Graphics2D g = backgroundImage.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		createAvatar(g, ImageIO.read(connection.getInputStream()));
		createUserInfo(g);

		createXpBar(g);
		createLevelAndRank(g);
		createXpText(g);

		createAdditional(g); // BETA label

		return backgroundImage;
	}

	private BufferedImage loadAndBuildBackground() throws IOException {
		final int MAX_WIDTH = 900;
		final int WIDTH = minimized ? 420 : 900;
		final int HEIGHT = 360;
		BufferedImage backgroundImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

		// Create graphics for background
		Graphics2D g = backgroundImage.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		// Round
		RoundRectangle2D roundRectangle = new RoundRectangle2D.Float(0, 0, WIDTH, HEIGHT, 40, 40);
		g.setClip(roundRectangle);

		if (background.getBackgroundFile() != null) {
			Image scaledInstance = ImageIO.read(new File(background.getBackgroundPath()))
				.getScaledInstance(MAX_WIDTH, HEIGHT, Image.SCALE_SMOOTH);
			int x = minimized ? -RandomUtil.getInteger(MAX_WIDTH-WIDTH) : WIDTH; // Move image left to random amount
			g.drawImage(scaledInstance, x, 0, null);
		} else {
			g.setColor(background.getColors().getBackgroundColor());
			g.fillRect(0, 0, WIDTH, HEIGHT);
		}

		// Draw the border
		RoundRectangle2D borderRectangle = new RoundRectangle2D.Float(3, 3, WIDTH-7, HEIGHT-7, 36, 36);
		g.setColor(new Color(0, 0, 0, 128)); // Semi-transparent black
		g.setStroke(new BasicStroke(10));
		g.draw(borderRectangle);

		g.dispose();

		return backgroundImage;
	}

	private void createAvatar(Graphics2D g, BufferedImage image) {
		int x = 20;
		int y = 20;
		int size = 140;
		// Draw avatar shadow
		g.setColor(background.getColors().getCardColor());
		g.drawOval(x, y, size, size);
		// Draws the avatar image on top of the background.
		g.drawImage(resizedCircle(image, size-8), x+4, y+4, null);
		// Draw circle
		g.setColor(new Color(50, 50, 50));
		g.setStroke(new BasicStroke(4));
		g.drawOval(x+3, y+3, size-6, size-6);
	}

	private void createUserInfo(Graphics2D g) {
		int x = 160;
		int y = 50;

		String text = globalName==null ? "USER" : globalName;
		drawFittingText(
			g, Fonts.Montserrat.bold, text,
			x, y,
			250, 700,
			18, 30
		);

		y += 30;
		g.setFont(Fonts.Montserrat.medium.deriveFont(Font.PLAIN, 16F));
		text = "@"+ MessageUtil.limitString(userName, 22);

		g.setColor(background.getColors().getShadowColor());
		g.drawString(text, x+12, y+2);
		g.setColor(background.getColors().getSecondaryTextColor());
		g.drawString(text, x+10, y);

		y += 15;
		g.setFont(Fonts.Montserrat.medium.deriveFont(Font.PLAIN, 20F));
		String formattedTime = "     "+timeCreated.format(formatter);
		g.setColor(background.getColors().getCardColor());
		FontMetrics fontMetrics = g.getFontMetrics();
		g.fillRoundRect(
			x+10, y,
			fontMetrics.stringWidth(formattedTime)+22, fontMetrics.getHeight()+6,
			30, 30
		);

		g.setColor(background.getColors().getShadowColor());
		g.drawString(formattedTime, x+22, y+25);
		g.setColor(background.getColors().getMainTextColor());
		g.drawString(formattedTime, x+20, y+23);

		y += 35;

		formattedTime = "     "+timeJoined.format(formatter);
		g.setColor(background.getColors().getCardColor());
		fontMetrics = g.getFontMetrics();
		g.fillRoundRect(
			x, y,
			fontMetrics.stringWidth(formattedTime)+22, fontMetrics.getHeight()+6,
			30, 30
		);

		g.setColor(background.getColors().getShadowColor());
		g.drawString(formattedTime, x+12, y+25);
		g.setColor(background.getColors().getMainTextColor());
		g.drawString(formattedTime, x+10, y+23);

		// Draw emojis
		g.setFont(Fonts.NotoEmoji.monochrome.deriveFont(Font.PLAIN, 14F));
		g.setColor(background.getColors().getShadowColor());
		g.drawString("\uD83D\uDC64", x+22, y-13);
		g.drawString("\uD83D\uDC4B", x+12, y+22);
		g.setColor(background.getColors().getMainTextColor());
		g.drawString("\uD83D\uDC64", x+20, y-15);
		g.drawString("\uD83D\uDC4B", x+10, y+20);
	}

	private void createXpBar(Graphics2D g) {
		final String textXpBarText = formatXp(textLevelXp, textXpDiff);
		final String voiceXpBarText = formatXp(voiceLevelXp, voiceXpDiff);

		final int xpBarLength = 390;
		final int heightDiff = 75; // between both bars
		int startX = 14;
		int startY = 185;

		g.setColor(background.getColors().getExperienceBackgroundColor());
		g.fillRect(startX, startY+10, xpBarLength, 50);
		g.fillRect(startX, startY+10+heightDiff, xpBarLength, 50);

		// Create the current XP bar for the background
		g.setColor(background.getColors().getExperienceForegroundColor());
		g.fillRect(startX+5, startY+15, (int) Math.min(xpBarLength - 10, (xpBarLength - 10) * (textPercentage / 100)), 40);
		g.fillRect(startX+5, startY+15+heightDiff, (int) Math.min(xpBarLength - 10, (xpBarLength - 10) * (voicePercentage / 100)), 40);

		// Create a 3 pixel width bar that's just at the end of our "current xp bar"
		g.setColor(background.getColors().getExperienceSeparatorColor());
		g.fillRect(startX+5+ (int) Math.min(xpBarLength - 10, (xpBarLength - 10) * (textPercentage / 100)), startY+15, 3, 40);
		g.fillRect(startX+5+ (int) Math.min(xpBarLength - 10, (xpBarLength - 10) * (voicePercentage / 100)), startY+15+heightDiff, 3, 40);

		// Create the text that should be displayed in the middle of the XP bar
		g.setColor(background.getColors().getExperienceTextColor());

		Font smallText = Fonts.Montserrat.medium.deriveFont(Font.PLAIN, 19F);
		g.setFont(smallText);

		FontMetrics fontMetrics = g.getFontMetrics(smallText);
		g.drawString(textXpBarText, startX+15+ ((xpBarLength - fontMetrics.stringWidth(textXpBarText)) / 2), startY+42);
		g.drawString(voiceXpBarText, startX+15+ ((xpBarLength - fontMetrics.stringWidth(voiceXpBarText)) / 2), startY+42+heightDiff);
	}

	private void createLevelAndRank(Graphics2D g) {
		final int heightDiff = 75; // between both bars
		int startX = 20;
		int startY = 190;

		// Create bar titles
		g.setFont(Fonts.Montserrat.medium.deriveFont(Font.PLAIN, 20));

		String text = lu.getLocalized(locale, "imagegen.profile.text");
		g.setColor(background.getColors().getShadowColor());
		g.drawString(text, startX+2, startY+2);
		g.setColor(background.getColors().getMainTextColor());
		g.drawString(text, startX, startY);

		text = lu.getLocalized(locale, "imagegen.profile.voice");
		g.setColor(background.getColors().getShadowColor());
		g.drawString(text, startX+2, startY+2+heightDiff);
		g.setColor(background.getColors().getMainTextColor());
		g.drawString(text, startX, startY+heightDiff);

		g.setColor(background.getColors().getExperienceTextColor()); // On bar
		// Level text
		g.setFont(Fonts.Montserrat.medium.deriveFont(Font.PLAIN, 16));

		g.drawString("lvl", startX+96, startY+40);
		g.drawString("lvl", startX+96, startY+40+heightDiff);

		// Level number
		g.setFont(Fonts.Montserrat.extraBold.deriveFont(Font.PLAIN, 28));

		FontMetrics infoTextGraphicsFontMetricsBold = g.getFontMetrics();
		text = String.valueOf(textLevel);
		g.drawString(text, startX+90-infoTextGraphicsFontMetricsBold.stringWidth(text), startY+40);
		text = String.valueOf(voiceLevel);
		g.drawString(text, startX+90-infoTextGraphicsFontMetricsBold.stringWidth(text), startY+40+heightDiff);

		// Create Score Text
		g.setFont(Fonts.Montserrat.medium.deriveFont(Font.PLAIN, 16));
		g.drawString("#", startX+290, startY+40);
		g.drawString("#", startX+290, startY+40+heightDiff);
		g.setFont(Fonts.Montserrat.extraBold.deriveFont(Font.PLAIN, 28));
		g.drawString(textRank, startX+305, startY+40);
		g.drawString(voiceRank, startX+305, startY+40+heightDiff);
	}

	private void createXpText(Graphics2D g) {
		final int heightDiff = 75; // between both bars
		int xRight = 395;
		int y = 190;

		drawXpText(
			g, xRight, y,
			"XP:",
			String.valueOf(textExperience),
			18
		);

		y+=heightDiff;
		drawXpText(
			g, xRight, y,
			"XP:",
			String.valueOf(voiceExperience),
			18
		);

		y+=heightDiff;
		drawXpText(
			g, xRight, y,
			lu.getLocalized(locale, "imagegen.profile.global_xp")+":",
			String.valueOf(globalExperience),
			20
		);
	}

	private void drawXpText(Graphics2D g, int xRight, int y, String text, String number, int fontSize) {
		g.setFont(Fonts.Montserrat.medium.deriveFont(Font.PLAIN, fontSize));
		int numberWidth = g.getFontMetrics(g.getFont()).stringWidth(number);

		g.setColor(background.getColors().getShadowColor());
		g.drawString(number, xRight-numberWidth+2, y+2);
		g.setColor(background.getColors().getSecondaryTextColor());
		g.drawString(number, xRight-numberWidth, y);

		g.setFont(Fonts.Montserrat.medium.deriveFont(Font.PLAIN, fontSize-4));
		int textWidth = g.getFontMetrics(g.getFont()).stringWidth(text);

		g.setColor(background.getColors().getShadowColor());
		g.drawString(text, xRight-textWidth-numberWidth-8, y+2);
		g.setColor(background.getColors().getMainTextColor());
		g.drawString(text, xRight-textWidth-numberWidth-10, y);
	}

	private void createAdditional(Graphics2D g) {
		int x = 20;
		int y = 340;

		g.setFont(Fonts.Montserrat.medium.deriveFont(Font.PLAIN, 20F));
		String text = "BETA v2502";

		g.setColor(background.getColors().getShadowColor());
		g.drawString(text, x+2, y+2);
		g.setColor(background.getColors().getSecondaryTextColor());
		g.drawString(text, x, y);
	}

	private String formatXp(long currentLevelXp, long xpInLevel) {
		if (String.valueOf(currentLevelXp).length()+String.valueOf(xpInLevel).length() > 10) {
			return "%s xp".formatted(currentLevelXp);
		} else {
			return "%s / %s xp".formatted(currentLevelXp, xpInLevel);
		}
	}

	@SuppressWarnings("SameParameterValue")
	private void drawFittingText(Graphics2D g, Font baseFont, String text, int x, int y, int w1, int w2, int minFontSize, int maxFontSize) {
		int maxWidth = minimized ? w1 : w2; // Adjust width based on 'minimized' flag

		Font font = baseFont.deriveFont(Font.PLAIN, maxFontSize);

		// Reduce font size until it fits or reaches the minimum size
		int fontSize = maxFontSize;
		while (fontSize >= minFontSize) {
			g.setFont(font);
			FontMetrics fm = g.getFontMetrics();
			int textWidth = fm.stringWidth(text);

			if (textWidth <= maxWidth) {
				break;
			}

			fontSize--; // Reduce font size
			font = baseFont.deriveFont(Font.PLAIN, fontSize);
		}

		// Final font set
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();

		// If text is still too wide at minimum font size, truncate it
		if (fm.stringWidth(text) > maxWidth) {
			String dots = "...";
			int dotWidth = fm.stringWidth(dots);
			int maxTextWidth = maxWidth - dotWidth;
			StringBuilder resultText = new StringBuilder();

			for (char c : text.toCharArray()) {
				if (fm.stringWidth(resultText.toString() + c) > maxTextWidth) {
					break;
				}
				resultText.append(c);
			}
			resultText.append(dots);
			text = resultText.toString();
		}

		// Draw
		g.setColor(background.getColors().getShadowColor());
		g.drawString(text, x + 2, y + 2);
		g.setColor(background.getColors().getMainTextColor());
		g.drawString(text, x, y);
	}
}
