package com.application.playlistcreator.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.application.playlistcreator.client.lastfm.LastFmClient.Tag;
import org.springframework.stereotype.Component;

@Component
public class GenreTagMatcher {

	private static final Pattern DECADE_TAG = Pattern.compile("^(?:19|20)?\\d0s$");
	private static final Set<String> NON_GENRE_TAGS = Set.of(
			"seen live",
			"favorite",
			"favorites",
			"favourite",
			"favourites",
			"albums i own",
			"male vocalists",
			"female vocalists",
			"american",
			"british",
			"canadian",
			"australian",
			"german",
			"french",
			"italian",
			"swedish",
			"norwegian",
			"finnish",
			"japanese");

	public MatchResult evaluate(String requestedGenre, List<Tag> tags, int maxRank) {
		String requestedKey = normalize(requestedGenre);
		List<String> consideredTags = new ArrayList<>();
		if (requestedKey.isBlank() || tags == null) {
			return new MatchResult(false, 0, consideredTags);
		}
		for (Tag tag : tags) {
			if (tag == null || tag.name() == null || tag.name().isBlank() || isNonGenreTag(tag.name())) {
				continue;
			}
			consideredTags.add(tag.name());
			int rank = consideredTags.size();
			if (normalize(tag.name()).equals(requestedKey)) {
				return new MatchResult(rank <= maxRank, rank, List.copyOf(consideredTags));
			}
			if (rank >= maxRank) {
				break;
			}
		}
		return new MatchResult(false, 0, List.copyOf(consideredTags));
	}

	public String normalize(String value) {
		if (value == null) {
			return "";
		}
		String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "")
				.toLowerCase(Locale.ROOT)
				.replace('’', '\'')
				.replaceAll("['\"]", "")
				.replaceAll("[_-]+", " ")
				.replaceAll("\\s+", " ")
				.trim();
		return normalized;
	}

	private boolean isNonGenreTag(String value) {
		String normalized = normalize(value);
		return NON_GENRE_TAGS.contains(normalized) || DECADE_TAG.matcher(normalized).matches();
	}

	public record MatchResult(
			boolean accepted,
			int genreRank,
			List<String> consideredTags) {
	}
}
