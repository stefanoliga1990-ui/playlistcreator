package com.application.playlistcreator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ApiSpotifySessionFilter apiSpotifySessionFilter)
			throws Exception {
		CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfRepository.setCookiePath("/");

		CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
		csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

		http
			.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
			.addFilterBefore(apiSpotifySessionFilter, CsrfFilter.class)
			.csrf(csrf -> csrf
					.csrfTokenRepository(csrfRepository)
					.csrfTokenRequestHandler(csrfRequestHandler))
			.headers(headers -> headers
					.contentSecurityPolicy(csp -> csp.policyDirectives(
							"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
									+ "connect-src 'self'; object-src 'none'; base-uri 'self'; "
									+ "frame-ancestors 'none'; form-action 'self'"))
					.frameOptions(frame -> frame.deny())
					.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
					.httpStrictTransportSecurity(hsts -> hsts
							.includeSubDomains(true)
							.maxAgeInSeconds(31536000)));

		return http.build();
	}

	@Bean
	FilterRegistrationBean<ApiSpotifySessionFilter> disableContainerRegistration(
			ApiSpotifySessionFilter apiSpotifySessionFilter) {
		FilterRegistrationBean<ApiSpotifySessionFilter> registration =
				new FilterRegistrationBean<>(apiSpotifySessionFilter);
		registration.setEnabled(false);
		return registration;
	}
}
