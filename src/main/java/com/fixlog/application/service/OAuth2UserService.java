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
	private final WorkspaceService workspaceService;

	public OAuth2UserService(UserRepository userRepository,
							 UserOauthRepository userOauthRepository,
							 WorkspaceService workspaceService) {
		this.userRepository = userRepository;
		this.userOauthRepository = userOauthRepository;
		this.workspaceService = workspaceService;
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

		UserEntity user = userOauthRepository.findByProviderAndProviderId(provider, providerId)
			.map(oauth -> {
				oauth.getUser().updateLoginInfo(name, email);
				return oauth.getUser();
			})
			.orElseGet(() -> {
				UserEntity created = userRepository.findByEmail(email)
					.orElseGet(() -> userRepository.save(new UserEntity(name, email)));
				userOauthRepository.save(new UserOauthEntity(created, provider, providerId));
				return created;
			});

		// 권한 트리는 루트가 없으면 성립하지 않는다. 로그인마다 확인하며, 이미 있으면 그대로 쓴다.
		workspaceService.ensurePersonalWorkspace(user);

		return new DefaultOAuth2User(
			Collections.emptyList(),
			attributes,
			userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName());
	}
}
