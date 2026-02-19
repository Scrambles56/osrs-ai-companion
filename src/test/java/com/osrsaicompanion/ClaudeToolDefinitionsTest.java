package com.osrsaicompanion;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.osrsaicompanion.tools.ClaudeTools;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class ClaudeToolDefinitionsTest
{
	@Test
	public void testToolDefinitionsAreWellFormed()
	{
		JsonArray tools = ClaudeTools.buildToolDefinitions();

		assertNotNull(tools);
		assertTrue("Should have at least one tool", tools.size() > 0);

		for (int i = 0; i < tools.size(); i++)
		{
			JsonObject tool = tools.get(i).getAsJsonObject();
			assertTrue("Tool " + i + " must have a name", tool.has("name"));
			assertTrue("Tool " + i + " must have a description", tool.has("description"));
			assertTrue("Tool " + i + " must have an input_schema", tool.has("input_schema"));

			String name = tool.get("name").getAsString();
			assertFalse("Tool name must not be empty", name.isEmpty());

			JsonObject schema = tool.getAsJsonObject("input_schema");
			assertTrue("Schema must have a type", schema.has("type"));
			assertEquals("Schema type must be object", "object", schema.get("type").getAsString());
		}
	}

	@Test
	public void testToolNamesAreUnique()
	{
		JsonArray tools = ClaudeTools.buildToolDefinitions();
		Set<String> names = new HashSet<>();

		for (int i = 0; i < tools.size(); i++)
		{
			String name = tools.get(i).getAsJsonObject().get("name").getAsString();
			assertTrue("Duplicate tool name: " + name, names.add(name));
		}
	}

	@Test
	public void testExpectedToolsArePresent()
	{
		JsonArray tools = ClaudeTools.buildToolDefinitions();
		Set<String> names = new HashSet<>();

		for (int i = 0; i < tools.size(); i++)
		{
			names.add(tools.get(i).getAsJsonObject().get("name").getAsString());
		}

		assertTrue(names.contains("get_achievement_diary_status"));
		assertTrue(names.contains("get_combat_achievement_status"));
		assertTrue(names.contains("get_ge_price"));
		assertTrue(names.contains("search_wiki"));
	}

	@Test
	public void testWikiToolHasRequiredQueryParameter()
	{
		JsonArray tools = ClaudeTools.buildToolDefinitions();

		JsonObject wikiTool = null;
		for (int i = 0; i < tools.size(); i++)
		{
			JsonObject tool = tools.get(i).getAsJsonObject();
			if ("search_wiki".equals(tool.get("name").getAsString()))
			{
				wikiTool = tool;
				break;
			}
		}

		assertNotNull("search_wiki tool should exist", wikiTool);
		JsonObject schema = wikiTool.getAsJsonObject("input_schema");
		assertTrue("search_wiki schema must have properties", schema.has("properties"));
		assertTrue("search_wiki schema must have query property",
			schema.getAsJsonObject("properties").has("query"));
		assertTrue("search_wiki schema must require query",
			schema.has("required"));
	}

	@Test
	public void testGePriceToolHasRequiredItemNameParameter()
	{
		JsonArray tools = ClaudeTools.buildToolDefinitions();

		JsonObject geTool = null;
		for (int i = 0; i < tools.size(); i++)
		{
			JsonObject tool = tools.get(i).getAsJsonObject();
			if ("get_ge_price".equals(tool.get("name").getAsString()))
			{
				geTool = tool;
				break;
			}
		}

		assertNotNull("get_ge_price tool should exist", geTool);
		JsonObject schema = geTool.getAsJsonObject("input_schema");
		assertTrue("get_ge_price schema must have properties", schema.has("properties"));
		assertTrue("get_ge_price schema must have item_name property",
			schema.getAsJsonObject("properties").has("item_name"));
		assertTrue("get_ge_price schema must require item_name",
			schema.has("required"));
	}

	@Test
	public void testUnknownToolReturnsErrorMessage()
	{
		ClaudeTools tools = new ClaudeTools(
			mock(Client.class), null, new Gson(), mock(ItemManager.class)
		);
		String result = tools.execute("nonexistent_tool", null);
		assertTrue("Should return error for unknown tool", result.startsWith("Unknown tool:"));
	}
}
