package dev.fireatom.FABI.commands.owner;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.ExpType;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.sql.SQLException;
import java.util.List;

public class ExperienceCmd extends SlashCommand {
	public ExperienceCmd() {
		this.name = "experience";
		this.path = "bot.owner.experience";
		this.category = CmdCategory.OWNER;
		this.accessLevel = CmdAccessLevel.DEV;
		this.options = List.of(
			new OptionData(OptionType.INTEGER, "action", lu.getText(path+".action.help"), true)
				.addChoice("Add TEXT", 0)
				.addChoice("Add VOICE", 1)
				.addChoice("Remove TEXT", 2)
				.addChoice("Remove VOICE", 3)
				.addChoice("Clear member text+voice", 4)
				.addChoice("Clear member full", 5)
				.addChoice("Delete user", 6)
				.addChoice("Delete guild", 7),
			new OptionData(OptionType.STRING, "server", lu.getText(path+".server.help")),
			new OptionData(OptionType.STRING, "user", lu.getText(path+".user.help")),
			new OptionData(OptionType.INTEGER, "amount", lu.getText(path+".amount.help"))
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		long userId = event.optLong("user");

		int action = event.optInteger("action");
		switch (action) {
			case 0,1,2,3 -> {
				// Change exp values
				long guildId;
				try {
					guildId = event.optLong("server");
				} catch (Exception ex) {
					editErrorOther(event, ex.getMessage());
					return;
				}

				Guild guild = event.getJDA().getGuildById(guildId);
				if (guild == null) {
					editErrorOther(event, "Guild not found");
					return;
				}
				guild.retrieveMemberById(userId).queue(member -> {
					int amount;
					try {
						amount = event.optInteger("amount");
					} catch (Exception ex) {
						editErrorOther(event, ex.getMessage());
						return;
					}

					ExpType expType = switch (action) {
						case 0,2 -> ExpType.TEXT;
						case 1,3 -> ExpType.VOICE;
						default -> null;
					};

					switch (action) {
						case 0,1 -> {
							// Add exp
							bot.getLevelUtil().giveExperience(member, amount, expType);

							editMsg(event, "Added `%s` %s exp to user `%s` in guild `%s`".formatted(amount, expType.name(), userId, guildId));
						}
						case 2,3 -> {
							// Remove exp
							bot.getLevelUtil().removeExperience(member, amount, expType);

							editMsg(event, "Removed `%s` %s exp from user `%s` in guild `%s`".formatted(amount, expType.name(), userId, guildId));
						}
					}
				}, failure -> {
					editErrorOther(event, "Member not found");
				});
			}
			case 4 -> {
				// Clear text+voice exp for guild user
				long guildId;
				try {
					guildId = event.optLong("server");
				} catch (Exception ex) {
					editErrorOther(event, ex.getMessage());
					return;
				}

				Guild guild = event.getJDA().getGuildById(guildId);
				if (guild == null) {
					editErrorOther(event, "Guild not found");
					return;
				}
				guild.retrieveMemberById(userId).queue(member -> {
					bot.getLevelUtil().clearExperience(member);

					editMsg(event, "Removed all (text and voice) exp from user `%s` in guild `%s`".formatted(userId, guildId));
				}, failure -> {
					editErrorOther(event, "Member not found");
				});
			}
			case 5 -> {
				// Delete guild user
				long guildId;
				try {
					guildId = event.optLong("server");
				} catch (Exception ex) {
					editErrorOther(event, ex.getMessage());
					return;
				}

				try {
					bot.getDBUtil().levels.deleteUser(guildId, userId);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "level delete member");
					return;
				}

				editMsg(event, "Deleted user `%s` in guild `%s`".formatted(userId, guildId));
			}
			case 6 -> {
				// Delete all user values
				try {
					bot.getDBUtil().levels.deleteUser(userId);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "level delete user");
					return;
				}

				editMsg(event, "Deleted user `%s`".formatted(userId));
			}
			case 7 -> {
				long guildId;
				try {
					guildId = event.optLong("server");
				} catch (Exception ex) {
					editErrorOther(event, ex.getMessage());
					return;
				}
				// Delete all guild values
				try {
					bot.getDBUtil().levels.deleteGuild(guildId);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "level delete guild");
					return;
				}

				editMsg(event, "Deleted guild `%s`".formatted(guildId));
			}
		}
	}
}
