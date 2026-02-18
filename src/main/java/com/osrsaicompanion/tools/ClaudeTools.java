package com.osrsaicompanion.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;

/**
 * Defines and executes the tools exposed to Claude via the Anthropic tool use API.
 * All execute* methods must be called on the client thread.
 */
@Slf4j
@RequiredArgsConstructor
public class ClaudeTools
{
	// VarPlayer IDs for bit-packed individual diary task completion.
	// Each region uses two ints storing 64 bits total (one bit per task).
	private static final int VARP_ARDOUGNE_1   = 1196, VARP_ARDOUGNE_2   = 1197;
	private static final int VARP_DESERT_1     = 1198, VARP_DESERT_2     = 1199;
	private static final int VARP_FALADOR_1    = 1186, VARP_FALADOR_2    = 1187;
	private static final int VARP_FREMENNIK_1  = 1184, VARP_FREMENNIK_2  = 1185;
	private static final int VARP_KANDARIN_1   = 1178, VARP_KANDARIN_2   = 1179;
	private static final int VARP_KARAMJA_1    = 1188, VARP_KARAMJA_2    = 1189;
	private static final int VARP_KOUREND_1    = 2085, VARP_KOUREND_2    = 2086;
	private static final int VARP_LUMBRIDGE_1  = 1194, VARP_LUMBRIDGE_2  = 1195;
	private static final int VARP_MORYTANIA_1  = 1180, VARP_MORYTANIA_2  = 1181;
	private static final int VARP_VARROCK_1    = 1176, VARP_VARROCK_2    = 1177;
	private static final int VARP_WESTERN_1    = 1182, VARP_WESTERN_2    = 1183;
	private static final int VARP_WILDERNESS_1 = 1192, VARP_WILDERNESS_2 = 1193;

	// VarPlayer IDs for per-tier task counts across all diaries combined.
	// Each stores the number of tasks completed in that tier globally.
	private static final int VARP_TASKCOUNT_EASY        = 1719;
	private static final int VARP_TASKCOUNT_MEDIUM      = 1720;
	private static final int VARP_TASKCOUNT_HARD        = 1721;
	private static final int VARP_TASKCOUNT_ELITE       = 1722;

	// Task totals per tier across all diaries (from the wiki / game data)
	private static final int TOTAL_EASY   = 72;
	private static final int TOTAL_MEDIUM = 60;
	private static final int TOTAL_HARD   = 57;
	private static final int TOTAL_ELITE  = 43;

	private final Client client;

	// -------------------------------------------------------------------------
	// Tool definitions (sent to Claude in every API request)
	// -------------------------------------------------------------------------

	public static JsonArray buildToolDefinitions()
	{
		JsonArray tools = new JsonArray();

		JsonObject diaryTool = new JsonObject();
		diaryTool.addProperty("name", "get_achievement_diary_status");
		diaryTool.addProperty("description",
			"Returns the player's achievement diary completion status. " +
			"For each of the 12 diary regions it shows which tiers (Easy/Medium/Hard/Elite) are fully complete, " +
			"how many individual tasks the player has completed in each tier across all diaries, " +
			"and the raw bit-mask of completed tasks per region so you can determine exactly which tasks remain. " +
			"Use this when the player asks about diary progress, what tasks they still need, or what rewards they can claim.");
		JsonObject diarySchema = new JsonObject();
		diarySchema.addProperty("type", "object");
		diarySchema.add("properties", new JsonObject());
		diaryTool.add("input_schema", diarySchema);
		tools.add(diaryTool);

		JsonObject caTool = new JsonObject();
		caTool.addProperty("name", "get_combat_achievement_status");
		caTool.addProperty("description",
			"Returns the player's Combat Achievement tier completion status " +
			"(Easy, Medium, Hard, Elite, Master, Grandmaster). " +
			"Use this when the player asks about combat achievements or what CA tier rewards they can claim.");
		JsonObject caSchema = new JsonObject();
		caSchema.addProperty("type", "object");
		caSchema.add("properties", new JsonObject());
		caTool.add("input_schema", caSchema);
		tools.add(caTool);

		return tools;
	}

	// -------------------------------------------------------------------------
	// Tool execution
	// -------------------------------------------------------------------------

	public String execute(String toolName)
	{
		switch (toolName)
		{
			case "get_achievement_diary_status":
				return executeGetAchievementDiaryStatus();
			case "get_combat_achievement_status":
				return executeGetCombatAchievementStatus();
			default:
				return "Unknown tool: " + toolName;
		}
	}

	private String executeGetAchievementDiaryStatus()
	{
		// Overall task counts per tier
		int easyDone   = client.getVarpValue(VARP_TASKCOUNT_EASY);
		int mediumDone = client.getVarpValue(VARP_TASKCOUNT_MEDIUM);
		int hardDone   = client.getVarpValue(VARP_TASKCOUNT_HARD);
		int eliteDone  = client.getVarpValue(VARP_TASKCOUNT_ELITE);

		StringBuilder sb = new StringBuilder();
		sb.append("Overall task progress across all diaries:\n");
		sb.append("  Easy:   ").append(easyDone).append("/").append(TOTAL_EASY).append(" tasks complete\n");
		sb.append("  Medium: ").append(mediumDone).append("/").append(TOTAL_MEDIUM).append(" tasks complete\n");
		sb.append("  Hard:   ").append(hardDone).append("/").append(TOTAL_HARD).append(" tasks complete\n");
		sb.append("  Elite:  ").append(eliteDone).append("/").append(TOTAL_ELITE).append(" tasks complete\n");
		sb.append("\nPer-region tier completion and task bits:\n");

		appendRegion(sb, "Ardougne",
			Varbits.DIARY_ARDOUGNE_EASY, Varbits.DIARY_ARDOUGNE_MEDIUM, Varbits.DIARY_ARDOUGNE_HARD, Varbits.DIARY_ARDOUGNE_ELITE,
			VARP_ARDOUGNE_1, VARP_ARDOUGNE_2);
		appendRegion(sb, "Desert",
			Varbits.DIARY_DESERT_EASY, Varbits.DIARY_DESERT_MEDIUM, Varbits.DIARY_DESERT_HARD, Varbits.DIARY_DESERT_ELITE,
			VARP_DESERT_1, VARP_DESERT_2);
		appendRegion(sb, "Falador",
			Varbits.DIARY_FALADOR_EASY, Varbits.DIARY_FALADOR_MEDIUM, Varbits.DIARY_FALADOR_HARD, Varbits.DIARY_FALADOR_ELITE,
			VARP_FALADOR_1, VARP_FALADOR_2);
		appendRegion(sb, "Fremennik",
			Varbits.DIARY_FREMENNIK_EASY, Varbits.DIARY_FREMENNIK_MEDIUM, Varbits.DIARY_FREMENNIK_HARD, Varbits.DIARY_FREMENNIK_ELITE,
			VARP_FREMENNIK_1, VARP_FREMENNIK_2);
		appendRegion(sb, "Kandarin",
			Varbits.DIARY_KANDARIN_EASY, Varbits.DIARY_KANDARIN_MEDIUM, Varbits.DIARY_KANDARIN_HARD, Varbits.DIARY_KANDARIN_ELITE,
			VARP_KANDARIN_1, VARP_KANDARIN_2);
		appendRegion(sb, "Karamja",
			Varbits.DIARY_KARAMJA_EASY, Varbits.DIARY_KARAMJA_MEDIUM, Varbits.DIARY_KARAMJA_HARD, Varbits.DIARY_KARAMJA_ELITE,
			VARP_KARAMJA_1, VARP_KARAMJA_2);
		appendRegion(sb, "Kourend",
			Varbits.DIARY_KOUREND_EASY, Varbits.DIARY_KOUREND_MEDIUM, Varbits.DIARY_KOUREND_HARD, Varbits.DIARY_KOUREND_ELITE,
			VARP_KOUREND_1, VARP_KOUREND_2);
		appendRegion(sb, "Lumbridge",
			Varbits.DIARY_LUMBRIDGE_EASY, Varbits.DIARY_LUMBRIDGE_MEDIUM, Varbits.DIARY_LUMBRIDGE_HARD, Varbits.DIARY_LUMBRIDGE_ELITE,
			VARP_LUMBRIDGE_1, VARP_LUMBRIDGE_2);
		appendRegion(sb, "Morytania",
			Varbits.DIARY_MORYTANIA_EASY, Varbits.DIARY_MORYTANIA_MEDIUM, Varbits.DIARY_MORYTANIA_HARD, Varbits.DIARY_MORYTANIA_ELITE,
			VARP_MORYTANIA_1, VARP_MORYTANIA_2);
		appendRegion(sb, "Varrock",
			Varbits.DIARY_VARROCK_EASY, Varbits.DIARY_VARROCK_MEDIUM, Varbits.DIARY_VARROCK_HARD, Varbits.DIARY_VARROCK_ELITE,
			VARP_VARROCK_1, VARP_VARROCK_2);
		appendRegion(sb, "Western",
			Varbits.DIARY_WESTERN_EASY, Varbits.DIARY_WESTERN_MEDIUM, Varbits.DIARY_WESTERN_HARD, Varbits.DIARY_WESTERN_ELITE,
			VARP_WESTERN_1, VARP_WESTERN_2);
		appendRegion(sb, "Wilderness",
			Varbits.DIARY_WILDERNESS_EASY, Varbits.DIARY_WILDERNESS_MEDIUM, Varbits.DIARY_WILDERNESS_HARD, Varbits.DIARY_WILDERNESS_ELITE,
			VARP_WILDERNESS_1, VARP_WILDERNESS_2);

		sb.append("\nNote: task bits are bit-packed integers. " +
			"Cross-reference with the OSRS wiki diary task lists to map bit positions to specific task names. " +
			"Bit 0 is the least-significant bit of the first int.");

		return sb.toString();
	}

	private void appendRegion(StringBuilder sb, String name,
		int easyVarbit, int mediumVarbit, int hardVarbit, int eliteVarbit,
		int varp1, int varp2)
	{
		boolean easyDone   = client.getVarbitValue(easyVarbit)   == 1;
		boolean mediumDone = client.getVarbitValue(mediumVarbit) == 1;
		boolean hardDone   = client.getVarbitValue(hardVarbit)   == 1;
		boolean eliteDone  = client.getVarbitValue(eliteVarbit)  == 1;

		int bits1 = client.getVarpValue(varp1);
		int bits2 = client.getVarpValue(varp2);

		sb.append(name).append(":\n");
		sb.append("  Tiers: Easy=").append(easyDone ? "complete" : "incomplete")
			.append(", Medium=").append(mediumDone ? "complete" : "incomplete")
			.append(", Hard=").append(hardDone ? "complete" : "incomplete")
			.append(", Elite=").append(eliteDone ? "complete" : "incomplete").append("\n");
		sb.append("  Task bits: [").append(Integer.toBinaryString(bits1))
			.append("][").append(Integer.toBinaryString(bits2)).append("]\n");
	}

	private String executeGetCombatAchievementStatus()
	{
		boolean easyDone        = client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_EASY)        == 1;
		boolean mediumDone      = client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_MEDIUM)      == 1;
		boolean hardDone        = client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_HARD)        == 1;
		boolean eliteDone       = client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_ELITE)       == 1;
		boolean masterDone      = client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_MASTER)      == 1;
		boolean grandmasterDone = client.getVarbitValue(Varbits.COMBAT_ACHIEVEMENT_TIER_GRANDMASTER) == 1;

		return "Combat Achievement tier status:\n" +
			"Easy=" + (easyDone ? "complete" : "incomplete") + "\n" +
			"Medium=" + (mediumDone ? "complete" : "incomplete") + "\n" +
			"Hard=" + (hardDone ? "complete" : "incomplete") + "\n" +
			"Elite=" + (eliteDone ? "complete" : "incomplete") + "\n" +
			"Master=" + (masterDone ? "complete" : "incomplete") + "\n" +
			"Grandmaster=" + (grandmasterDone ? "complete" : "incomplete");
	}
}
