package com.osrsaicompanion;

import com.google.gson.Gson;
import com.osrsaicompanion.tools.ClaudeTools;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests the wiki-based item name resolution fallback in get_ge_price.
 * Both ItemManager and OkHttpClient are mocked — no RuneLite or network required.
 */
public class GePriceSuggestionTest
{
	private ItemManager itemManager;
	private OkHttpClient httpClient;
	private ClaudeTools tools;

	@Before
	public void setUp()
	{
		itemManager = mock(ItemManager.class);
		httpClient = mock(OkHttpClient.class);
		tools = new ClaudeTools(mock(Client.class), httpClient, new Gson(), itemManager);
	}

	@Test
	public void testWikiFallbackResolvesIronGlovesToGauntlets() throws IOException
	{
		when(itemManager.search("Iron gloves")).thenReturn(Collections.emptyList());
		when(itemManager.search("Iron gauntlets")).thenReturn(
			Collections.singletonList(item(9, "Iron gauntlets"))
		);
		mockSequentialResponses(
			"{\"query\":{\"search\":[{\"title\":\"Iron gauntlets\"}]}}",  // wiki
			"{\"data\":{\"9\":{\"high\":150,\"low\":100}}}"               // GE price
		);

		String result = tools.execute("get_ge_price", input("Iron gloves"));

		assertTrue("Should return a price, not a not-found error", result.contains("GE prices for"));
		assertTrue("Should show the resolved name", result.contains("Iron gauntlets"));
	}

	@Test
	public void testWikiFallbackResolvesIronHelmetToFullHelm() throws IOException
	{
		when(itemManager.search("Iron helmet")).thenReturn(Collections.emptyList());
		when(itemManager.search("Iron full helm")).thenReturn(
			Collections.singletonList(item(8, "Iron full helm"))
		);
		mockSequentialResponses(
			"{\"query\":{\"search\":[{\"title\":\"Iron full helm\"}]}}",  // wiki
			"{\"data\":{\"8\":{\"high\":200,\"low\":150}}}"               // GE price
		);

		String result = tools.execute("get_ge_price", input("Iron helmet"));

		assertTrue("Should return a price", result.contains("GE prices for"));
		assertTrue("Should show the resolved name", result.contains("Iron full helm"));
	}

	@Test
	public void testWikiNoResultReturnsNotFound() throws IOException
	{
		when(itemManager.search("Unobtainium sword")).thenReturn(Collections.emptyList());
		mockSequentialResponses("{\"query\":{\"search\":[]}}");

		String result = tools.execute("get_ge_price", input("Unobtainium sword"));

		assertTrue("Should return not-found message", result.contains("not available on the Grand Exchange"));
	}

	@Test
	public void testWikiNetworkErrorReturnsNotFound() throws IOException
	{
		when(itemManager.search("Broken item")).thenReturn(Collections.emptyList());
		Call call = mock(Call.class);
		when(httpClient.newCall(any())).thenReturn(call);
		when(call.execute()).thenThrow(new IOException("network error"));

		String result = tools.execute("get_ge_price", input("Broken item"));

		assertTrue("Should return not-found when wiki is unreachable", result.contains("not available on the Grand Exchange"));
	}

	@Test
	public void testEmptyItemNameReturnsError()
	{
		String result = tools.execute("get_ge_price", input(""));
		assertTrue(result.contains("No item name provided"));
	}

	// --- helpers ---

	/** Stubs httpClient to return each JSON body in order across successive newCall().execute() calls. */
	@SuppressWarnings("unchecked")
	private void mockSequentialResponses(String... jsonBodies) throws IOException
	{
		Call[] calls = new Call[jsonBodies.length];
		for (int i = 0; i < jsonBodies.length; i++)
		{
			calls[i] = mock(Call.class);
			Response response = new Response.Builder()
				.request(new Request.Builder().url("https://oldschool.runescape.wiki/api.php").build())
				.protocol(Protocol.HTTP_1_1)
				.code(200)
				.message("OK")
				.body(ResponseBody.create(null, jsonBodies[i]))
				.build();
			when(calls[i].execute()).thenReturn(response);
		}
		if (calls.length == 1)
		{
			when(httpClient.newCall(any())).thenReturn(calls[0]);
		}
		else
		{
			Call first = calls[0];
			Call[] rest = java.util.Arrays.copyOfRange(calls, 1, calls.length);
			when(httpClient.newCall(any())).thenReturn(first, rest);
		}
	}

	private static ItemPrice item(int id, String name)
	{
		ItemPrice ip = new ItemPrice();
		ip.setId(id);
		ip.setName(name);
		return ip;
	}

	private static com.google.gson.JsonObject input(String itemName)
	{
		com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
		obj.addProperty("item_name", itemName);
		return obj;
	}
}
