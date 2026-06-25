package com.application.playlistcreator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.application.playlistcreator.client.lastfm.LastFmClient.Tag;
import org.junit.jupiter.api.Test;

class GenreTagMatcherTest {

	private final GenreTagMatcher matcher = new GenreTagMatcher();

	@Test
	void treatsHyphenAndSpaceAsEquivalentWithoutMergingRelatedGenres() {
		assertThat(matcher.normalize("Post-Punk")).isEqualTo("post punk");
		assertThat(matcher.normalize("Post Punk")).isEqualTo("post punk");
		assertThat(matcher.normalize("Punk Rock")).isNotEqualTo(matcher.normalize("Punk"));
	}

	@Test
	void acceptsGenreOnlyWhenItIsAmongFirstThreeMusicalTags() {
		var accepted = matcher.evaluate("post-punk", List.of(
				tag("seen live"),
				tag("british"),
				tag("Post Punk"),
				tag("alternative")), 3);
		var rejected = matcher.evaluate("post-punk", List.of(
				tag("alternative"),
				tag("indie"),
				tag("new wave"),
				tag("post-punk")), 3);

		assertThat(accepted.accepted()).isTrue();
		assertThat(accepted.genreRank()).isEqualTo(1);
		assertThat(rejected.accepted()).isFalse();
	}

	private Tag tag(String name) {
		return new Tag(name, "100", "https://last.fm/tag/" + name);
	}
}
