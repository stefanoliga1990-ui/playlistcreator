package com.application.playlistcreator.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

	@Bean
	public RestClient.Builder restClientBuilder(PlaylistCreatorProperties properties) {
		var httpProperties = properties.httpClient();
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(httpProperties.connectTimeout())
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(httpProperties.readTimeout());
		return RestClient.builder().requestFactory(requestFactory);
	}
}
