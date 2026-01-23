package com.zodomo.osrsclaude;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("osrsclaude")
public interface OsrsClaudeConfig extends Config
{
	@ConfigItem(
		keyName = "apiKey",
		name = "Claude API Key",
		description = "Your Anthropic API key for Claude",
		secret = true,
		position = 1
	)
	default String apiKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "batchNumber",
		name = "Response Batches",
		description = "Number of 200-character message batches for responses (max chars = 200 * this value)",
		position = 2
	)
	@Range(min = 1, max = 10)
	default int batchNumber()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "model",
		name = "Model",
		description = "Which Claude model to use",
		position = 3
	)
	default ClaudeModel model()
	{
		return ClaudeModel.SONNET;
	}
}
