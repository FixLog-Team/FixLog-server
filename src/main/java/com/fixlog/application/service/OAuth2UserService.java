package com.fixlog.application.service;

import com.fixlog.application.repository.UserOauthRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.UserOauthEntity;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;

@Service
public class OAuth2UserService implements org.springframework.security.oauth2.client.userinfo.OAuth2UserService<OAuth2UserRequest, OAuth2User> {

	private final UserRepository userRepository;
	private final UserOauthRepository userOauthRepository;

	public OAuth2UserService(UserRepository userRepository, UserOauthRepository userOauthRepository) {
		this.userRepository = userRepository;
		this.userOauthRepository = userOauthRepository;
	}

	@Override
	@Transactional
	public OAuth2User loadUser(OAuth2UserRequest userRequest) {
		DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
		OAuth2User oAuth2User = delegate.loadUser(userRequest);

		Map<String, Object> attributes = oAuth2User.getAttributes();
		String provider = userRequest.getClientRegistration().getRegistrationId();
		String providerId = (String) attributes.get("sub");
		String email = (String) attributes.get("email");
		String name = (String) attributes.get("name");

		userOauthRepository.findByProviderAndProviderId(provider, providerId)
			.ifPresentOrElse(
				oauth -> oauth.getUser().updateLoginInfo(name),
				() -> {
					UserEntity user = userRepository.findByEmail(email)
						.orElseGet(() -> userRepository.save(new UserEntity(name, email)));
					userOauthRepository.save(new UserOauthEntity(user, provider, providerId));
				}
			);

		return new DefaultOAuth2User(
			Collections.emptyList(),
			attributes,
			userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName());
	}
}
