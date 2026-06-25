package com.application.playlistcreator.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class SongNormalizer {

	private static final Pattern BRACKETED_TEXT = Pattern.compile("\\(([^)]*)\\)|\\[([^]]*)]");
	private static final Set<String> DESCRIPTIVE_WORDS = Set.of(
			"live", "acoustic", "remix", "remastered", "remaster", "version", "edit", "reprise",
			"intro", "outro", "snippet", "medley", "mono", "stereo");

	public String normalizeTitle(String value) {
		if (value == null) {
			return "";
		}
		String withoutDecorations = removeDescriptiveBracketedText(value);
		return normalizePlainText(withoutDecorations);
	}

	public String normalizeArtist(String value) {
		return normalizePlainText(value);
	}

	public boolean hasPenaltyTerm(String value) {
		String normalized = normalizePlainText(value);
		return normalized.contains(" live ")
				|| normalized.endsWith(" live")
				|| normalized.contains(" remix")
				|| normalized.contains(" karaoke")
				|| normalized.contains(" tribute")
				|| normalized.contains(" instrumental")
				|| normalized.contains(" cover");
	}

	private String removeDescriptiveBracketedText(String value) {
		Matcher matcher = BRACKETED_TEXT.matcher(value);
		StringBuffer result = new StringBuffer();
		while (matcher.find()) {
			String content = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
			if (isDescriptive(content)) {
				matcher.appendReplacement(result, " ");
			}
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private boolean isDescriptive(String value) {
		String normalized = normalizePlainText(value);
		for (String word : DESCRIPTIVE_WORDS) {
			if (normalized.contains(word)) {
				return true;
			}
		}
		return false;
	}

	private String normalizePlainText(String value) {
		if (value == null) {
			return "";
		}
		String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT)
				.replace("&", " and ")
				.replaceAll("[^a-z0-9]+", " ")
				.trim()
				.replaceAll("\\s+", " ");
		return normalized;
	}
}
