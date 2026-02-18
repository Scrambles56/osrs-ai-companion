package com.osrsaicompanion;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class OsrsAiCompanionPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(OsrsAiCompanionPlugin.class);
		RuneLite.main(args);
	}
}
