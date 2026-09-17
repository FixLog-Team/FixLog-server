package com.fixlog;

import com.fixlog.application.repository.AIConversationRepository;
import com.fixlog.application.repository.AIMessageRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.service.AIConversationService;
import com.fixlog.application.service.AIMessagePersistenceService;
import com.fixlog.application.service.AIMessageQueryService;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.AIConversationEntity;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.domain.model.WorkspaceMemberEntity;
import com.fixlog.domain.model.WorkspaceRole;
import com.fixlog.presentation.dto.request.AIConversationCreateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AIWorkspaceIsolationTest {
    @Autowired AIConversationRepository conversations;
    @Autowired AIMessageRepository messages;
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired WorkspaceMemberRepository members;

    private AIConversationService conversationService;
    private AIMessagePersistenceService persistenceService;
    private AIMessageQueryService queryService;
    private UserEntity user;
    private UUID personalId;
    private UUID sharedId;

    @BeforeEach
    void setUp() {
        user = users.save(new UserEntity("tester", "workspace-chat@example.com"));
        personalId = workspaces.save(WorkspaceEntity.personalFor(user)).getWorkspaceId();
        sharedId = workspaces.save(WorkspaceEntity.shared("team")).getWorkspaceId();
        members.save(new WorkspaceMemberEntity(personalId, user.getUserId(), WorkspaceRole.OWNER));
        members.save(new WorkspaceMemberEntity(sharedId, user.getUserId(), WorkspaceRole.OWNER));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
        WorkspaceContext context = new WorkspaceContext(workspaces, members);
        conversationService = new AIConversationService(conversations, context);
        persistenceService = new AIMessagePersistenceService(conversations, messages, context);
        queryService = new AIMessageQueryService(conversationService, messages);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void createsAndListsOnlyCurrentUsersActiveConversationsInCurrentWorkspace() {
        AIConversationEntity personal = conversationService.create(new AIConversationCreateRequest("personal"));
        assertThat(personal.getWorkspaceId()).isEqualTo(personalId);
        selectWorkspace(sharedId.toString());
        AIConversationEntity shared = conversationService.create(new AIConversationCreateRequest("shared"));
        assertThat(shared.getWorkspaceId()).isEqualTo(sharedId);
        AIConversationEntity deleted = conversationService.create(new AIConversationCreateRequest("deleted"));
        conversationService.delete(deleted.getConversationId());
        UserEntity other = users.save(new UserEntity("other", "other-chat@example.com"));
        conversations.save(new AIConversationEntity(other.getUserId(), sharedId, "other"));

        var page = conversationService.list(PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).containsExactly(shared);
        RequestContextHolder.resetRequestAttributes();
        assertThat(conversationService.list(PageRequest.of(0, 10)).getContent()).containsExactly(personal);
    }

    @Test
    void rejectsCrossWorkspaceReadDeleteMessageHistoryAndSendWithoutMutatingData() {
        AIConversationEntity personal = conversationService.create(new AIConversationCreateRequest("personal"));
        UUID id = personal.getConversationId();
        persistenceService.prepare(id, "existing message");
        long messageCount = messages.count();
        int sequence = personal.getNextMessageSequence();
        selectWorkspace(sharedId.toString());

        assertNotFound(() -> conversationService.get(id));
        assertNotFound(() -> conversationService.delete(id));
        assertNotFound(() -> queryService.list(id, null, 20));
        assertNotFound(() -> persistenceService.prepare(id, "forbidden message"));
        assertThat(personal.getUsable()).isEqualTo(1);
        assertThat(personal.getNextMessageSequence()).isEqualTo(sequence);
        assertThat(messages.count()).isEqualTo(messageCount);
    }

    @Test
    void allowsMessageSendAndHistoryInSelectedWorkspace() {
        selectWorkspace(sharedId.toString());
        AIConversationEntity conversation = conversationService.create(new AIConversationCreateRequest("shared"));
        var prepared = persistenceService.prepare(conversation.getConversationId(), "hello");
        assertThat(queryService.list(conversation.getConversationId(), null, 20).getContent())
                .containsExactly(prepared.assistantMessage(), prepared.userMessage());
    }

    @Test
    void rejectsAnotherUsersConversationInSameWorkspace() {
        selectWorkspace(sharedId.toString());
        UserEntity other = users.save(new UserEntity("other", "other-owner@example.com"));
        AIConversationEntity conversation = conversations.save(
                new AIConversationEntity(other.getUserId(), sharedId, "private chat"));
        UUID id = conversation.getConversationId();

        assertNotFound(() -> conversationService.get(id));
        assertNotFound(() -> conversationService.delete(id));
        assertNotFound(() -> queryService.list(id, null, 20));
        assertNotFound(() -> persistenceService.prepare(id, "forbidden"));
        assertThat(messages.count()).isZero();
    }

    @Test
    void rejectsOwnConversationAfterWorkspaceMembershipIsRemoved() {
        selectWorkspace(sharedId.toString());
        AIConversationEntity conversation = conversationService.create(new AIConversationCreateRequest("shared"));
        members.delete(members.findByWorkspaceIdAndUserId(sharedId, user.getUserId()).orElseThrow());
        members.flush();

        assertNotFound(() -> conversationService.list(PageRequest.of(0, 20)));
        assertNotFound(() -> conversationService.get(conversation.getConversationId()));
        assertNotFound(() -> persistenceService.prepare(conversation.getConversationId(), "forbidden"));
    }

    @Test
    void rejectsNonMemberWorkspaceAndMalformedHeader() {
        UUID inaccessible = workspaces.save(WorkspaceEntity.shared("private team")).getWorkspaceId();
        selectWorkspace(inaccessible.toString());
        assertNotFound(() -> conversationService.list(PageRequest.of(0, 20)));
        assertNotFound(() -> conversationService.create(new AIConversationCreateRequest("forbidden")));
        selectWorkspace("invalid-uuid");
        assertThatThrownBy(() -> conversationService.list(PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(Code.INVALID_REQUEST));
    }

    private void selectWorkspace(String id) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(WorkspaceContext.HEADER_NAME, id);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(Code.NOT_FOUND));
    }
}
