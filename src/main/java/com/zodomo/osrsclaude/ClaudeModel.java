package com.zodomo.osrsclaude;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClaudeModel
{
	HAIKU("claude-haiku-4-5-20251001", "Haiku 4.5"),
	SONNET("claude-sonnet-4-5-20250929", "Sonnet 4.5"),
	OPUS("claude-opus-4-5-20251124", "Opus 4.5");

	private final String modelId;
	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
