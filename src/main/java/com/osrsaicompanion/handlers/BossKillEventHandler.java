package com.osrsaicompanion.handlers;

import com.osrsaicompanion.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;

import javax.swing.SwingUtilities;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class BossKillEventHandler
{
	// Matches: "Your Zulrah kill count is: 42."
	// Also matches: "Your Chambers of Xeric kill count is: 100."
	private static final Pattern KC_PATTERN =
		Pattern.compile("Your (.+?) kill count is: (\\d+)\\.");

	// Milestones to celebrate
	private static final int[] MILESTONES = {1, 50, 100, 250, 500, 1000, 2000, 5000};

	private final ClaudeClient claudeClient;
	private final PlayerContextBuilder contextBuilder;
	private final OsrsAiCompanionConfig config;
	private final Supplier<AiCompanionPanel> panelSupplier;

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		Matcher matcher = KC_PATTERN.matcher(event.getMessage());
		if (!matcher.matches())
		{
			return;
		}

		String bossName = matcher.group(1);
		int killCount;
		try
		{
			killCount = Integer.parseInt(matcher.group(2));
		}
		catch (NumberFormatException e)
		{
			return;
		}

		if (!isMilestone(killCount))
		{
			return;
		}

		if (!config.celebrateBossKills() || claudeClient.apiCallInProgress)
		{
			return;
		}

		String playerName = contextBuilder.getPlayerName();
		String prompt = killCount == 1
			? "I just killed " + bossName + " for the first time! React to my first kill."
			: "I just reached " + killCount + " kills at " + bossName + "! Celebrate this milestone with me.";

		String eventText = killCount == 1
			? playerName + " has slain " + bossName + " for the first time!"
			: playerName + " has reached " + killCount + " " + bossName + " kills!";

		AiCompanionPanel panel = panelSupplier.get();
		SwingUtilities.invokeLater(() -> {
			if (panel != null)
			{
				panel.appendEventMessage(eventText);
			}
		});

		claudeClient.sendMessage(prompt, panel);
	}

	private static boolean isMilestone(int kc)
	{
		for (int milestone : MILESTONES)
		{
			if (kc == milestone)
			{
				return true;
			}
		}
		return false;
	}
}
