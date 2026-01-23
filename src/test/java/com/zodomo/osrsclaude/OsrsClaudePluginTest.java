package com.zodomo.osrsclaude;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class OsrsClaudePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(OsrsClaudePlugin.class);
		RuneLite.main(args);
	}
}
