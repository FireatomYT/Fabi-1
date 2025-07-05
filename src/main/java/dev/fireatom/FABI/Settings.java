package dev.fireatom.FABI;

import org.apache.commons.cli.CommandLine;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Arrays;
import java.util.List;

public class Settings {

	private final int shardCount;
	private final int[] shards;
	private final boolean useColors;
	private final boolean useDebugging;

	private final List<String> jarArgs;
	private final List<String> runtimeArgs;

	Settings(CommandLine cmd, String[] args) {
		shardCount = Integer.parseInt(cmd.getOptionValue("shardCount", "0"));
		shards = parseShardIds(cmd);
		useColors = !cmd.hasOption("no-colors");
		useDebugging = cmd.hasOption("debug");

		RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
		runtimeArgs = runtimeMXBean.getInputArguments();
		jarArgs = Arrays.asList(args);
	}

	public int getShardCount() {
		return shardCount < 1 ? -1 : shardCount;
	}

	@Nullable
	public int[] getShards() {
		return shards;
	}

	public boolean useColors() {
		return useColors;
	}

	public boolean useDebugging() {
		return useDebugging;
	}

	public List<String> getJarArgs() {
		return jarArgs;
	}

	public List<String> getRuntimeArgs() {
		return runtimeArgs;
	}

	private int[] parseShardIds(CommandLine cmd) {
		if (getShardCount() == -1 || !cmd.hasOption("shards")) {
			return null;
		}

		try {
			String[] parts = cmd.getOptionValue("shards").split("-");
			if (parts.length == 1) {

				return new int[]{
					getBetween(
						Integer.parseInt(parts[0]), 0, getShardCount()
					)
				};
			}

			if (parts.length != 2) {
				return null;
			}

			int min = getBetween(Integer.parseInt(parts[0]), 0, getShardCount());
			int max = getBetween(Integer.parseInt(parts[1]), 0, getShardCount());

			if (min == max) {
				return new int[]{min};
			}

			// If the min value is higher than the max value, we'll swap around
			// the variables so min becomes max, and max comes min.
			if (min > max) {
				max = max + min;
				min = max - min;
				max = max - min;
			}

			int range = max - min + 1;
			int[] shards = new int[range];
			for (int i = 0; i < range; i++) {
				shards[i] = min++;
			}

			return shards;
		} catch (NumberFormatException e) {
			App.getLogger().error("Failed to parse shard range for the \"--shards\" flag, error: {}", e.getMessage(), e);
			return null;
		}
	}

	private int getBetween(int v, int min, int max) {
		if (v < min) {
			return min;
		}
		return Math.min(v, max);
	}
}
