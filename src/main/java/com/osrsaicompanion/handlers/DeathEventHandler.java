package com.osrsaicompanion.handlers;

import com.osrsaicompanion.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;

import javax.swing.SwingUtilities;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class DeathEventHandler
{
	private static final String DEATH_MESSAGE = "Oh dear, you are dead!";

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

		if (!DEATH_MESSAGE.equals(event.getMessage()))
		{
			return;
		}

		if (!config.commiserateDeath() || claudeClient.apiCallInProgress)
		{
			return;
		}

		String playerName = contextBuilder.getPlayerName();
		String prompt = "I just died in OSRS! Commiserate with me.";

		AiCompanionPanel panel = panelSupplier.get();
		SwingUtilities.invokeLater(() -> {
			if (panel != null)
			{
				panel.appendEventMessage(playerName + " has died!");
			}
		});

		claudeClient.sendMessage(prompt, panel);
	}
}
