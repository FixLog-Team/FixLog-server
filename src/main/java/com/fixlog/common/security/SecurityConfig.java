package com.fixlog.common.security;

import com.fixlog.application.service.OAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final OAuth2UserService oAuth2UserService;

	public SecurityConfig(OAuth2UserService oAuth2UserService) {
		this.oAuth2UserService = oAuth2UserService;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/", "/login/**", "/css/**", "/js/**", "/images/**",
					"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/api/**").permitAll()
				.anyRequest().authenticated()
			).oauth2Login(oauth2 -> oauth2
				.defaultSuccessUrl("/main", true)
				.userInfoEndpoint(userInfo -> userInfo
					.userService(oAuth2UserService)
				)
			);
		return http.build();
	}
}
