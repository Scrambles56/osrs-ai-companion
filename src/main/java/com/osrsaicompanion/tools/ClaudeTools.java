package com.osrsaicompanion.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

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

	private static final String WIKI_API = "https://oldschool.runescape.wiki/api.php";

	private static final String GE_API = "https://prices.runescape.wiki/api/v1/osrs";

	private final Client client;
	private final OkHttpClient httpClient;
	private final Gson gson;
	private final ItemManager itemManager;

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

		JsonObject geTool = new JsonObject();
		geTool.addProperty("name", "get_ge_price");
		geTool.addProperty("description",
			"Looks up the current Grand Exchange price for an OSRS item by name. " +
			"Returns the latest buy and sell prices from the OSRS GE. " +
			"Use this when the player asks how much something costs, whether something is worth buying or selling, " +
			"or when giving money-making or shopping advice that depends on current market prices. " +
			"When pricing multiple items (e.g. a full gear set), call this tool for ALL items in parallel in a single response rather than one at a time.");
		JsonObject geProperties = new JsonObject();
		JsonObject geItemProp = new JsonObject();
		geItemProp.addProperty("type", "string");
		geItemProp.addProperty("description", "The exact or approximate item name, e.g. 'Overload', 'Dragon bones', 'Abyssal whip'");
		geProperties.add("item_name", geItemProp);
		JsonObject geSchema = new JsonObject();
		geSchema.addProperty("type", "object");
		geSchema.add("properties", geProperties);
		JsonArray geRequired = new JsonArray();
		geRequired.add("item_name");
		geSchema.add("required", geRequired);
		geTool.add("input_schema", geSchema);
		tools.add(geTool);

		JsonObject wikiTool = new JsonObject();
		wikiTool.addProperty("name", "search_wiki");
		wikiTool.addProperty("description",
			"Searches the Old School RuneScape wiki and returns the most relevant page content. " +
			"Use this whenever the player asks about specific game mechanics, item stats, quest requirements, " +
			"monster weaknesses, skill training methods, or anything that requires accurate up-to-date game information. " +
			"Prefer this over your training data for OSRS-specific facts.");
		JsonObject wikiProperties = new JsonObject();
		JsonObject queryProp = new JsonObject();
		queryProp.addProperty("type", "string");
		queryProp.addProperty("description", "The search term to look up on the OSRS wiki, e.g. 'Overload', 'Mithril battleaxe', 'Dragon Slayer quest'");
		wikiProperties.add("query", queryProp);
		JsonObject wikiSchema = new JsonObject();
		wikiSchema.addProperty("type", "object");
		wikiSchema.add("properties", wikiProperties);
		JsonArray wikiRequired = new JsonArray();
		wikiRequired.add("query");
		wikiSchema.add("required", wikiRequired);
		wikiTool.add("input_schema", wikiSchema);
		tools.add(wikiTool);

		return tools;
	}

	// -------------------------------------------------------------------------
	// Tool execution
	// -------------------------------------------------------------------------

	public String execute(String toolName)
	{
		return execute(toolName, null);
	}

	public String execute(String toolName, JsonObject input)
	{
		switch (toolName)
		{
			case "get_achievement_diary_status":
				return executeGetAchievementDiaryStatus();
			case "get_combat_achievement_status":
				return executeGetCombatAchievementStatus();
			case "get_ge_price":
				String itemName = input != null && input.has("item_name") ? input.get("item_name").getAsString() : "";
				return executeGetGePrice(itemName);
			case "search_wiki":
				String query = input != null && input.has("query") ? input.get("query").getAsString() : "";
				return executeSearchWiki(query);
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

	private String executeGetGePrice(String itemName)
	{
		if (itemName == null || itemName.trim().isEmpty())
		{
			return "No item name provided.";
		}

		// Resolve item name to ID via RuneLite's ItemManager search.
		// If not found, ask the wiki — it understands that e.g. "Iron helmet" → "Iron full helm".
		java.util.List<ItemPrice> results = itemManager.search(itemName);
		String wikiTitle = null;
		if (results == null || results.isEmpty())
		{
			wikiTitle = resolveItemNameViaWiki(itemName);
			if (wikiTitle != null)
			{
				results = itemManager.search(wikiTitle);
			}
		}
		if (results == null || results.isEmpty())
		{
			// Item exists on the wiki but isn't tradeable on the GE — fetch a short
			// description so Claude understands why and doesn't keep retrying.
			String title = wikiTitle != null ? wikiTitle : itemName;
			String snippet = fetchWikiSnippet(title);
			if (snippet != null)
			{
				return "\"" + itemName + "\" is not tradeable on the GE. " + snippet;
			}
			return "\"" + itemName + "\" is not available on the Grand Exchange.";
		}

		// Always use the best match — RuneLite's search handles fuzzy matching.
		// Include the resolved name so Claude knows exactly what was looked up.
		ItemPrice match = results.get(0);
		int itemId = match.getId();
		String resolvedName = match.getName();
		String nameNote = resolvedName.equalsIgnoreCase(itemName.trim()) ? ""
			: " (best match for \"" + itemName.trim() + "\")";

		try
		{
			okhttp3.HttpUrl url = okhttp3.HttpUrl.get(GE_API).newBuilder()
				.addPathSegment("latest")
				.addQueryParameter("id", String.valueOf(itemId))
				.build();

			Request request = new Request.Builder()
				.url(url)
				.header("User-Agent", "osrs-ai-companion/1.0 (RuneLite plugin)")
				.build();

			try (Response response = httpClient.newCall(request).execute())
			{
				if (!response.isSuccessful() || response.body() == null)
				{
					return "GE price lookup failed: HTTP " + response.code();
				}

				JsonObject body = gson.fromJson(response.body().string(), JsonObject.class);
				JsonObject data = body.has("data") ? body.getAsJsonObject("data") : null;
				if (data == null || !data.has(String.valueOf(itemId)))
				{
					return "No price data available for: " + resolvedName;
				}

				JsonObject priceData = data.getAsJsonObject(String.valueOf(itemId));
				long high = priceData.has("high") && !priceData.get("high").isJsonNull()
					? priceData.get("high").getAsLong() : -1;
				long low = priceData.has("low") && !priceData.get("low").isJsonNull()
					? priceData.get("low").getAsLong() : -1;

				StringBuilder sb = new StringBuilder();
				sb.append("GE prices for ").append(resolvedName).append(nameNote).append(":\n");
				sb.append("  Sell price (high): ").append(high >= 0 ? String.format("%,d", high) + " gp" : "N/A").append("\n");
				sb.append("  Buy price (low):   ").append(low >= 0 ? String.format("%,d", low) + " gp" : "N/A");
				return sb.toString();
			}
		}
		catch (Exception e)
		{
			log.error("GE price lookup failed for: {}", itemName, e);
			return "GE price lookup failed: " + e.getMessage();
		}
	}

	/**
	 * Fetches a short plain-text extract from an OSRS wiki page by exact title.
	 * Returns null on failure.
	 */
	private String fetchWikiSnippet(String pageTitle)
	{
		try
		{
			okhttp3.HttpUrl url = okhttp3.HttpUrl.get(WIKI_API).newBuilder()
				.addQueryParameter("action", "query")
				.addQueryParameter("prop", "extracts")
				.addQueryParameter("titles", pageTitle)
				.addQueryParameter("exintro", "true")
				.addQueryParameter("explaintext", "true")
				.addQueryParameter("exchars", "200")
				.addQueryParameter("format", "json")
				.build();

			Request request = new Request.Builder()
				.url(url)
				.header("User-Agent", "osrs-ai-companion/1.0 (RuneLite plugin)")
				.build();

			try (Response response = httpClient.newCall(request).execute())
			{
				if (!response.isSuccessful() || response.body() == null) return null;
				JsonObject result = gson.fromJson(response.body().string(), JsonObject.class);
				JsonObject pages = result.getAsJsonObject("query").getAsJsonObject("pages");
				JsonObject page = pages.entrySet().iterator().next().getValue().getAsJsonObject();
				String extract = page.has("extract") ? page.get("extract").getAsString().trim() : null;
				return (extract != null && !extract.isEmpty()) ? extract : null;
			}
		}
		catch (Exception e)
		{
			log.warn("Wiki snippet fetch failed for: {}", pageTitle, e);
			return null;
		}
	}

	/**
	 * Queries the OSRS wiki search API and returns the title of the best-matching page.
	 * Used to resolve informal item names (e.g. "Iron gloves") to their canonical OSRS
	 * names (e.g. "Iron gauntlets") before looking up a GE price.
	 * Returns null if no match is found or the request fails.
	 */
	private String resolveItemNameViaWiki(String itemName)
	{
		try
		{
			okhttp3.HttpUrl url = okhttp3.HttpUrl.get(WIKI_API).newBuilder()
				.addQueryParameter("action", "query")
				.addQueryParameter("list", "search")
				.addQueryParameter("srsearch", itemName)
				.addQueryParameter("srnamespace", "0")
				.addQueryParameter("srlimit", "1")
				.addQueryParameter("format", "json")
				.build();

			Request request = new Request.Builder()
				.url(url)
				.header("User-Agent", "osrs-ai-companion/1.0 (RuneLite plugin)")
				.build();

			try (Response response = httpClient.newCall(request).execute())
			{
				if (!response.isSuccessful() || response.body() == null) return null;
				JsonObject result = gson.fromJson(response.body().string(), JsonObject.class);
				JsonArray hits = result.getAsJsonObject("query").getAsJsonArray("search");
				if (hits.size() == 0) return null;
				return hits.get(0).getAsJsonObject().get("title").getAsString();
			}
		}
		catch (Exception e)
		{
			log.warn("Wiki name resolution failed for: {}", itemName, e);
			return null;
		}
	}

	private String executeSearchWiki(String query)
	{
		if (query == null || query.trim().isEmpty())
		{
			return "No search query provided.";
		}

		try
		{
			// First: search for the best matching page title
			okhttp3.HttpUrl searchHttpUrl = okhttp3.HttpUrl.get(WIKI_API).newBuilder()
				.addQueryParameter("action", "query")
				.addQueryParameter("list", "search")
				.addQueryParameter("srsearch", query)
				.addQueryParameter("srnamespace", "0")
				.addQueryParameter("srlimit", "1")
				.addQueryParameter("format", "json")
				.build();

			Request searchRequest = new Request.Builder()
				.url(searchHttpUrl)
				.header("User-Agent", "osrs-ai-companion/1.0 (RuneLite plugin)")
				.build();

			String pageTitle;
			try (Response searchResponse = httpClient.newCall(searchRequest).execute())
			{
				if (!searchResponse.isSuccessful() || searchResponse.body() == null)
				{
					return "Wiki search failed: HTTP " + searchResponse.code();
				}
				JsonObject searchResult = gson.fromJson(searchResponse.body().string(), JsonObject.class);
				JsonArray results = searchResult
					.getAsJsonObject("query")
					.getAsJsonArray("search");
				if (results.size() == 0)
				{
					return "No wiki page found for: " + query;
				}
				pageTitle = results.get(0).getAsJsonObject().get("title").getAsString();
			}

			// Second: fetch section 1 (Details/infobox) as wikitext — quest/item pages
			// store requirements, stats etc. there, which the plain extract API omits.
			okhttp3.HttpUrl section1Url = okhttp3.HttpUrl.get(WIKI_API).newBuilder()
				.addQueryParameter("action", "parse")
				.addQueryParameter("page", pageTitle)
				.addQueryParameter("prop", "wikitext")
				.addQueryParameter("section", "1")
				.addQueryParameter("format", "json")
				.build();

			Request section1Request = new Request.Builder()
				.url(section1Url)
				.header("User-Agent", "osrs-ai-companion/1.0 (RuneLite plugin)")
				.build();

			String section1Text = null;
			try (Response section1Response = httpClient.newCall(section1Request).execute())
			{
				if (section1Response.isSuccessful() && section1Response.body() != null)
				{
					JsonObject parsed = gson.fromJson(section1Response.body().string(), JsonObject.class);
					if (parsed.has("parse"))
					{
						String wikitext = parsed.getAsJsonObject("parse")
							.getAsJsonObject("wikitext")
							.get("*").getAsString().trim();
						if (!wikitext.isEmpty())
						{
							section1Text = wikitext;
						}
					}
				}
			}
			catch (Exception e)
			{
				log.debug("Section 1 fetch failed for {}, falling back to extract", pageTitle);
			}

			// Third: fetch a plain-text intro for general context
			okhttp3.HttpUrl extractUrl = okhttp3.HttpUrl.get(WIKI_API).newBuilder()
				.addQueryParameter("action", "query")
				.addQueryParameter("prop", "extracts")
				.addQueryParameter("titles", pageTitle)
				.addQueryParameter("exintro", "true")
				.addQueryParameter("explaintext", "true")
				.addQueryParameter("exchars", "1000")
				.addQueryParameter("format", "json")
				.build();

			Request extractRequest = new Request.Builder()
				.url(extractUrl)
				.header("User-Agent", "osrs-ai-companion/1.0 (RuneLite plugin)")
				.build();

			try (Response extractResponse = httpClient.newCall(extractRequest).execute())
			{
				String introText = "";
				if (extractResponse.isSuccessful() && extractResponse.body() != null)
				{
					JsonObject extractResult = gson.fromJson(extractResponse.body().string(), JsonObject.class);
					JsonObject pages = extractResult.getAsJsonObject("query").getAsJsonObject("pages");
					JsonObject page = pages.entrySet().iterator().next().getValue().getAsJsonObject();
					introText = page.has("extract") ? page.get("extract").getAsString().trim() : "";
				}

				if (introText.isEmpty() && section1Text == null)
				{
					return "Wiki page '" + pageTitle + "' has no content.";
				}

				StringBuilder result = new StringBuilder("OSRS Wiki \u2014 ").append(pageTitle).append(":\n\n");
				if (!introText.isEmpty())
				{
					result.append(introText).append("\n\n");
				}
				if (section1Text != null)
				{
					result.append("--- Details/Infobox ---\n").append(section1Text);
				}
				return result.toString();
			}		}
		catch (Exception e)
		{
			log.error("Wiki search failed for query: {}", query, e);
			return "Wiki search failed: " + e.getMessage();
		}
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
