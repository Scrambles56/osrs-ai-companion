package com.osrsaicompanion.handlers;

import com.osrsaicompanion.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Varbits;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;

import javax.swing.SwingUtilities;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class DiaryCompletionEventHandler
{
	private final Client client;
	private final ClaudeClient claudeClient;
	private final PlayerContextBuilder contextBuilder;
	private final OsrsAiCompanionConfig config;
	private final Supplier<AiCompanionPanel> panelSupplier;

	// Ordered map of diary name -> int[]{easy, medium, hard, elite} varbit IDs
	private static final Map<String, int[]> DIARIES = new LinkedHashMap<>();
	static
	{
		DIARIES.put("Ardougne",   new int[]{Varbits.DIARY_ARDOUGNE_EASY,   Varbits.DIARY_ARDOUGNE_MEDIUM,   Varbits.DIARY_ARDOUGNE_HARD,   Varbits.DIARY_ARDOUGNE_ELITE});
		DIARIES.put("Desert",     new int[]{Varbits.DIARY_DESERT_EASY,     Varbits.DIARY_DESERT_MEDIUM,     Varbits.DIARY_DESERT_HARD,     Varbits.DIARY_DESERT_ELITE});
		DIARIES.put("Falador",    new int[]{Varbits.DIARY_FALADOR_EASY,    Varbits.DIARY_FALADOR_MEDIUM,    Varbits.DIARY_FALADOR_HARD,    Varbits.DIARY_FALADOR_ELITE});
		DIARIES.put("Fremennik",  new int[]{Varbits.DIARY_FREMENNIK_EASY,  Varbits.DIARY_FREMENNIK_MEDIUM,  Varbits.DIARY_FREMENNIK_HARD,  Varbits.DIARY_FREMENNIK_ELITE});
		DIARIES.put("Kandarin",   new int[]{Varbits.DIARY_KANDARIN_EASY,   Varbits.DIARY_KANDARIN_MEDIUM,   Varbits.DIARY_KANDARIN_HARD,   Varbits.DIARY_KANDARIN_ELITE});
		DIARIES.put("Karamja",    new int[]{Varbits.DIARY_KARAMJA_EASY,    Varbits.DIARY_KARAMJA_MEDIUM,    Varbits.DIARY_KARAMJA_HARD,    Varbits.DIARY_KARAMJA_ELITE});
		DIARIES.put("Kourend",    new int[]{Varbits.DIARY_KOUREND_EASY,    Varbits.DIARY_KOUREND_MEDIUM,    Varbits.DIARY_KOUREND_HARD,    Varbits.DIARY_KOUREND_ELITE});
		DIARIES.put("Lumbridge",  new int[]{Varbits.DIARY_LUMBRIDGE_EASY,  Varbits.DIARY_LUMBRIDGE_MEDIUM,  Varbits.DIARY_LUMBRIDGE_HARD,  Varbits.DIARY_LUMBRIDGE_ELITE});
		DIARIES.put("Morytania",  new int[]{Varbits.DIARY_MORYTANIA_EASY,  Varbits.DIARY_MORYTANIA_MEDIUM,  Varbits.DIARY_MORYTANIA_HARD,  Varbits.DIARY_MORYTANIA_ELITE});
		DIARIES.put("Varrock",    new int[]{Varbits.DIARY_VARROCK_EASY,    Varbits.DIARY_VARROCK_MEDIUM,    Varbits.DIARY_VARROCK_HARD,    Varbits.DIARY_VARROCK_ELITE});
		DIARIES.put("Western",    new int[]{Varbits.DIARY_WESTERN_EASY,    Varbits.DIARY_WESTERN_MEDIUM,    Varbits.DIARY_WESTERN_HARD,    Varbits.DIARY_WESTERN_ELITE});
		DIARIES.put("Wilderness", new int[]{Varbits.DIARY_WILDERNESS_EASY, Varbits.DIARY_WILDERNESS_MEDIUM, Varbits.DIARY_WILDERNESS_HARD, Varbits.DIARY_WILDERNESS_ELITE});
	}

	private static final String[] TIER_NAMES = {"Easy", "Medium", "Hard", "Elite"};

	// Cache: "DiaryName-TierIndex" -> 0 or 1
	private final Map<String, Integer> diaryCache = new LinkedHashMap<>();
	private volatile boolean cacheReady = false;
	private volatile boolean needsCacheInit = false;
	private volatile boolean varbitChanged = false;

	public void clearCache()
	{
		diaryCache.clear();
		cacheReady = false;
		needsCacheInit = false;
		varbitChanged = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			needsCacheInit = true;
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			clearCache();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (cacheReady)
		{
			varbitChanged = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (needsCacheInit)
		{
			needsCacheInit = false;
			for (Map.Entry<String, int[]> entry : DIARIES.entrySet())
			{
				int[] varbits = entry.getValue();
				for (int tier = 0; tier < varbits.length; tier++)
				{
					diaryCache.put(entry.getKey() + "-" + tier, client.getVarbitValue(varbits[tier]));
				}
			}
			cacheReady = true;
			return;
		}

		if (!cacheReady || !varbitChanged)
		{
			return;
		}
		varbitChanged = false;

		for (Map.Entry<String, int[]> entry : DIARIES.entrySet())
		{
			String diaryName = entry.getKey();
			int[] varbits = entry.getValue();

			for (int tier = 0; tier < varbits.length; tier++)
			{
				String key = diaryName + "-" + tier;
				int cached = diaryCache.getOrDefault(key, 0);
				int current = client.getVarbitValue(varbits[tier]);
				diaryCache.put(key, current);

				if (cached == 0 && current == 1)
				{
					if (!config.celebrateDiaryCompletions() || claudeClient.apiCallInProgress)
					{
						continue;
					}

					String tierName = TIER_NAMES[tier];
					String prompt = "I just completed the " + tierName + " " + diaryName
						+ " Achievement Diary! Please congratulate me and mention any notable rewards.";

					String playerName = contextBuilder.getPlayerName();
					String eventText = playerName + " completed " + diaryName + " Diary (" + tierName + ")";

					AiCompanionPanel panel = panelSupplier.get();
					SwingUtilities.invokeLater(() -> {
						if (panel != null)
						{
							panel.appendEventMessage(eventText);
						}
					});

					claudeClient.sendMessage(prompt, panel);
					return;
				}
			}
		}
	}
}
