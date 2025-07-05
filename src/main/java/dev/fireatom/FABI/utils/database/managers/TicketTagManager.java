package dev.fireatom.FABI.utils.database.managers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.internal.interactions.component.ButtonImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static dev.fireatom.FABI.utils.CastUtil.getOrDefault;
import static dev.fireatom.FABI.utils.CastUtil.requireNonNull;

public class TicketTagManager extends LiteBase {
	
	public TicketTagManager(ConnectionUtil cu) {
		super(cu, "ticketTag");
	}

	public int createTag(long guildId, int panelId, int tagType, String buttonText, String emoji, Long categoryId, String message, String supportRoleIds, String ticketName, int buttonStyle) throws SQLException {
		List<String> keys = new ArrayList<>(10);
		List<String> values = new ArrayList<>(10);
		keys.addAll(List.of("guildId", "panelId", "tagType", "buttonText", "ticketName", "buttonStyle"));
		values.addAll(List.of(String.valueOf(guildId), String.valueOf(panelId), String.valueOf(tagType), quote(buttonText), quote(ticketName), String.valueOf(buttonStyle)));
		if (emoji != null) {
			keys.add("emoji");
			values.add(quote(emoji));
		}
		if (categoryId != null) {
			keys.add("location");
			values.add(String.valueOf(categoryId));
		}
		if (message != null) {
			keys.add("message");
			values.add(replaceNewline(message));
		}
		if (supportRoleIds != null) {
			keys.add("supportRoles");
			values.add(quote(supportRoleIds));
		}
		return executeWithRow("INSERT INTO %s(%s) VALUES (%s)".formatted(table, String.join(", ", keys), String.join(", ", values)));
	}

	public void deleteTag(int tagId) throws SQLException {
		execute("DELETE FROM %s WHERE (tagId=%d)".formatted(table, tagId));
	}

	public void deleteAll(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public void updateTag(int tagId, Integer tagType, String buttonText, String emoji, Long categoryId, String message, String supportRoleIds, String ticketName, Integer buttonStyle) throws SQLException {
		List<String> values = new ArrayList<>();
		if (tagType != null) 
			values.add("tagType="+tagType);
		if (buttonText != null) 
			values.add("buttonText="+quote(buttonText));
		if (emoji != null) 
			values.add("emoji="+quote(emoji));
		if (categoryId != null) 
			values.add("location="+categoryId);
		if (message != null) 
			values.add("message="+replaceNewline(message));
		if (supportRoleIds != null) 
			values.add("supportRoles="+quote(supportRoleIds));
		if (ticketName != null) 
			values.add("ticketName="+quote(ticketName));
		if (buttonStyle != -1) 
			values.add("buttonStyle="+buttonStyle);
		
		if (!values.isEmpty())
			execute("UPDATE %s SET %s WHERE (tagId=%d)".formatted(table, String.join(", ", values), tagId));
	}

	public Long getGuildId(int tagId) {
		return selectOne("SELECT guildId FROM %s WHERE (tagId=%d)".formatted(table, tagId), "guildId", Long.class);
	}

	public int countPanelTags(int panelId) {
		return count("SELECT COUNT(*) FROM %s WHERE (panelId=%d)".formatted(table, panelId));
	}

	public String getTagText(int tagId) {
		return selectOne("SELECT buttonText FROM %s WHERE (tagId=%d)".formatted(table, tagId), "buttonText", String.class);
	}

	public Map<Integer, String> getTagsText(long guildId) {
		List<Map<String, Object>> data = select("SELECT tagId, buttonText FROM %s WHERE (guildId=%s)".formatted(table, guildId), Set.of("tagId", "buttonText"));
		if (data.isEmpty()) return Collections.emptyMap();
		return data.stream().limit(25).collect(Collectors.toMap(s -> (Integer) s.get("tagId"), s -> (String) s.get("buttonText")));
	}

	public List<Button> getPanelTags(int panelId) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (panelId=%s)".formatted(table, panelId),
			Set.of("tagId", "buttonText", "buttonStyle", "emoji")
		);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(Tag::createButton).toList();
	}

	public Tag getTagFull(int tagId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (tagId=%s)".formatted(table, tagId),
			Set.of("buttonText", "buttonStyle", "emoji", "tagType", "location", "message", "supportRoles", "ticketName")
		);
		if (data==null) return null;
		return new Tag(data, true);
	}

	public Tag getTagInfo(int tagId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (tagId=%s)".formatted(table, tagId),
			Set.of("tagType", "location", "message", "supportRoles", "ticketName")
		);
		if (data==null) return null;
		return new Tag(data, false);
	}

	@NotNull
	public String getSupportRolesString(int tagId) {
		String data = selectOne("SELECT supportRoles FROM %s WHERE (tagId=%s)".formatted(table, tagId), "supportRoles", String.class);
		return data==null ? "" : data;
	}

	// TOOLS
	private String replaceNewline(final String text) {
		return quote(text).replace("\\n", "<br>");
	}

	public static class Tag {
		private final int tagType;
		private final String buttonText, ticketName, location, message, supportRoles;
		private final ButtonStyle buttonStyle;
		private final Emoji emoji;

		public Tag(Map<String, Object> map, boolean includeButton) {
			this.tagType = requireNonNull(map.get("tagType"));
			this.ticketName = requireNonNull(map.get("ticketName"));
			this.location = getOrDefault(map.get("location"), null);
			this.message = setNewline(getOrDefault(map.get("message"), null));
			this.supportRoles = getOrDefault(map.get("supportRoles"), null);
			if (includeButton) {
				this.buttonText = requireNonNull(map.get("buttonText"));
				this.buttonStyle = ButtonStyle.fromKey(getOrDefault(map.get("buttonStyle"), 0));
				this.emoji = Optional.ofNullable((String) map.get("emoji")).map(Emoji::fromFormatted).orElse(null);
			} else {
				this.buttonText = null;
				this.buttonStyle = null;
				this.emoji = null;
			}
		}
		
		private String setNewline(String text) {
			if (text==null) return null;
			return text.replaceAll("<br>", "\n");
		}

		public EmbedBuilder getPreviewEmbed(Function<String, String> locale, Integer tagId) {
			return new EmbedBuilder()
				.setColor(Constants.COLOR_DEFAULT)
				.setTitle("Tag ID: %d".formatted(tagId))
				.addField(locale.apply(".type"), (tagType > 1 ? "Channel" : "Thread"), true)
				.addField(locale.apply(".name"), "`"+ticketName+"`", true);
		}

		public Button previewButton() {
			return new ButtonImpl("tag_preview", buttonText, buttonStyle, true, emoji);
		}

		public static Button createButton(Map<String, Object> map) {
			int tagId = requireNonNull(map.get("tagId"));
			String buttonText = requireNonNull(map.get("buttonText"));
			ButtonStyle style = ButtonStyle.fromKey(requireNonNull(map.get("buttonStyle")));
			Emoji emoji = Optional.ofNullable((String) map.get("emoji")).map(Emoji::fromFormatted).orElse(null);
			return new ButtonImpl("tag:"+tagId, buttonText, style, false, emoji);
		}

		@NotNull
		public String getTicketName() {
			return ticketName;
		}

		@Nullable
		public Integer getTagType() {
			return tagType;
		}

		@Nullable
		public String getLocation() {
			return location;
		}

		@Nullable
		public String getMessage() {
			return message;
		}

		@NotNull
		public List<String> getSupportRoles() {
			if (supportRoles==null) return List.of();
			return Arrays.asList(supportRoles.split(";"));
		}
	}
}
