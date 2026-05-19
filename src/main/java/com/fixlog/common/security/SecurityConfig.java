package com.fixlog.common.security;

import com.fixlog.application.service.OAuth2UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final OAuth2UserService oAuth2UserService;

	@Value("#{'${cors.allowed-origins}'.split(',')}")
	private List<String> allowedOrigins;

	@Value("${oauth2.success-redirect-url}")
	private String successRedirectUrl;

	public SecurityConfig(OAuth2UserService oAuth2UserService) {
		this.oAuth2UserService = oAuth2UserService;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/", "/login/**", "/css/**", "/js/**", "/images/**",
					"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/api/**").permitAll()
				.anyRequest().authenticated()
			).oauth2Login(oauth2 -> oauth2
				.defaultSuccessUrl(successRedirectUrl, true)
				.userInfoEndpoint(userInfo -> userInfo
					.userService(oAuth2UserService)
				)
			);
		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(allowedOrigins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
