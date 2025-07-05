package dev.fireatom.FABI.middleware;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.file.lang.LocaleUtil;
import dev.fireatom.FABI.utils.message.MessageUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.Objects;

public class MessageFactory {

	private final LocaleUtil localeUtil;
	private final GenericCommandInteractionEvent event;

	private String path;
	private String message;
	private MessageType type;
	private boolean asEmbed = false;
	private boolean temp = false;

	public MessageFactory(App bot, GenericCommandInteractionEvent event) {
		this.localeUtil = bot.getLocaleUtil();
		this.event = event;
	}

	public MessageFactory setPath(String path) {
		this.path = path;
		return this;
	}

	public MessageFactory setMessage(String message) {
		this.message = message;
		return this;
	}

	public MessageFactory setType(MessageType type) {
		this.type = type;
		return this;
	}

	public MessageFactory asEmbed(boolean asEmbed) {
		this.asEmbed = asEmbed;
		return this;
	}

	public MessageFactory temp(boolean temp) {
		this.temp = temp;
		return this;
	}

	public MessageEditData build() throws IllegalStateException {
		if (asEmbed) {
			EmbedBuilder builder = new EmbedBuilder();
			builder.setColor(Objects.requireNonNullElse(type, MessageType.INFO).getColor());

			if (path != null) {
				builder.setDescription(localeUtil.getText(event, path));
				if (message != null) {
					builder.addField(
						localeUtil.getText(event, "errors.additional"),
						MessageUtil.limitString(message, 1024),
						false
					);
				}
			} else if (message != null) {
				builder.setDescription(MessageUtil.limitString(message, 2048));
			} else {
				throw new IllegalStateException("Missing path or message.");
			}

			String tempMsg = temp ? localeUtil.getText(event, "misc.temp_msg") : null;
			return new MessageEditBuilder()
				.setContent(tempMsg)
				.setEmbeds(builder.build())
				.build();
		} else {
			StringBuilder builder = new StringBuilder();
			if (temp) {
				builder.append(localeUtil.getText(event, "misc.temp_msg"))
					.append("\n");
			}
			if (type != null && type.getEmoji() != null) {
				builder.append(type.getEmoji())
					.append(" ");
			}

			if (path != null) {
				builder.append(localeUtil.getText(event, path));
			} else if (message != null) {
				builder.append(MessageUtil.limitString(message, 2048));
			} else {
				throw new IllegalStateException("Missing path or message.");
			}

			return new MessageEditBuilder()
				.setContent(builder.toString())
				.build();
		}
	}

	public enum MessageType {
		ERROR(Constants.COLOR_FAILURE, Constants.FAILURE),
		WARNING(Constants.COLOR_WARNING, Constants.WARNING),
		SUCCESS(Constants.COLOR_SUCCESS, Constants.SUCCESS),
		INFO(Constants.COLOR_DEFAULT);

		private final int color;
		private final String emoji;

		MessageType(int color) {
			this.color = color;
			this.emoji = null;
		}

		MessageType(int color, String emoji) {
			this.color = color;
			this.emoji = emoji;
		}

		public int getColor() {
			return color;
		}

		public String getEmoji() {
			return emoji;
		}
	}

}
