package com.sw1.umltool.features.ai.chat.service;

import com.sw1.umltool.features.ai.chat.dto.SendAiMessageRequest;
import com.sw1.umltool.features.ai.chat.model.AiConversationEntity;
import com.sw1.umltool.features.ai.chat.model.AiConversationType;
import com.sw1.umltool.features.ai.chat.repository.AiConversationRepository;
import com.sw1.umltool.features.ai.chat.repository.AiMessageRepository;
import com.sw1.umltool.features.ai.chat.exception.AiConversationNotFoundException;
import com.sw1.umltool.features.auth.model.UserEntity;
import com.sw1.umltool.features.auth.repository.UserRepository;
import com.sw1.umltool.features.project.model.ProjectEntity;
import com.sw1.umltool.features.project.model.ProjectMemberEntity;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class AiChatServiceTest {
    private AiConversationRepository conversations;
    private AiMessageRepository messages;
    private UserRepository users;
    private ProjectAccessService access;
    private AiOrchestrator orchestrator;
    private AiChatService service;

    @BeforeEach
    void setUp() {
        conversations = mock(AiConversationRepository.class);
        messages = mock(AiMessageRepository.class);
        users = mock(UserRepository.class);
        access = mock(ProjectAccessService.class);
        orchestrator = mock(AiOrchestrator.class);
        service = new AiChatService(conversations, messages, users, access, 4000, orchestrator);
        when(conversations.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsOneGroupConversationForAnAuthorizedMember() {
        when(conversations.findByScopeKey("project-1:GROUP")).thenReturn(Optional.empty());
        var response = service.getGroupConversation("project-1", "user-1");

        assertEquals("project-1", response.projectId());
        assertEquals(AiConversationType.GROUP, response.type());
    }

    @Test
    void privateConversationCannotBeReadByAnotherUser() {
        AiConversationEntity conversation = conversation("project-1", "user-1", AiConversationType.PRIVATE);
        when(conversations.findById("private-1")).thenReturn(Optional.of(conversation));

        assertThrows(AiConversationNotFoundException.class,
                () -> service.listMessages("project-1", "user-2", "private-1", null));
    }

    @Test
    void messagesAreStoredWithAuthenticatedAuthorAndTrimmedContent() {
        when(conversations.findByScopeKey("project-1:GROUP")).thenReturn(Optional.empty());
        when(users.findById("user-1")).thenReturn(Optional.of(UserEntity.builder().id("user-1").firstName("Ana").lastName("Pérez").build()));
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(conversations.findById(any())).thenReturn(Optional.of(conversation("project-1", null, AiConversationType.GROUP)));
        var response = service.sendGroupMessage("project-1", "user-1", "private-1", new SendAiMessageRequest("  Hola equipo  "));

        assertEquals("Hola equipo", response.content());
        assertEquals("user-1", response.authorUserId());
    }

    @Test
    void rejectsBlankMessages() {
        when(conversations.findById("private-1")).thenReturn(Optional.of(conversation("project-1", "user-1", AiConversationType.PRIVATE)));
        assertThrows(IllegalArgumentException.class,
                () -> service.sendPrivateMessage("project-1", "user-1", "private-1", new SendAiMessageRequest("   ")));
    }

    @Test
    void sameClientMessageIdReturnsThePersistedMessageWithoutCreatingAnotherRow() {
        AiConversationEntity conversation = conversation("project-1", null, AiConversationType.GROUP);
        when(conversations.findById("private-1")).thenReturn(Optional.of(conversation));
        when(users.findById("user-1")).thenReturn(Optional.of(UserEntity.builder().id("user-1").build()));
        when(messages.findByConversation_IdAndClientMessageId("private-1", "client-1"))
                .thenReturn(Optional.empty());
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var first = service.saveGroupMessage("project-1", "user-1", "private-1",
                new SendAiMessageRequest("Hola", "client-1"));
        when(messages.findByConversation_IdAndClientMessageId("private-1", "client-1"))
                .thenReturn(Optional.of(first.message()));
        var retry = service.saveGroupMessage("project-1", "user-1", "private-1",
                new SendAiMessageRequest("Hola", "client-1"));

        assertEquals("client-1", retry.message().getClientMessageId());
        assertEquals(first.message().getId(), retry.message().getId());
    }

    @Test
    void groupMessageWithoutMentionDoesNotInvokeAi() {
        when(conversations.findById("private-1")).thenReturn(Optional.of(conversation("project-1", null, AiConversationType.GROUP)));
        when(users.findById("user-1")).thenReturn(Optional.of(UserEntity.builder().id("user-1").build()));
        service.saveGroupMessage("project-1", "user-1", "private-1", new SendAiMessageRequest("Hola equipo"));

        verify(orchestrator, never()).request(any());
    }

    @Test
    void groupMentionInvokesAiAfterPersistingHumanMessage() {
        when(conversations.findById("private-1")).thenReturn(Optional.of(conversation("project-1", null, AiConversationType.GROUP)));
        when(users.findById("user-1")).thenReturn(Optional.of(UserEntity.builder().id("user-1").build()));
        service.saveGroupMessage("project-1", "user-1", "private-1", new SendAiMessageRequest("@IA explica la relación"));

        ArgumentCaptor<AiGenerationRequest> requestCaptor = ArgumentCaptor.forClass(AiGenerationRequest.class);
        verify(orchestrator).request(requestCaptor.capture());

        AiGenerationRequest request = requestCaptor.getValue();
        assertEquals("private-1", request.conversationId());
        assertEquals("project-1", request.projectId());
        assertEquals("user-1", request.userId());
        assertEquals("@IA explica la relación", request.content());
        assertEquals(AiConversationType.GROUP, request.conversationType());
    }

    private AiConversationEntity conversation(String projectId, String ownerId, AiConversationType type) {
        return AiConversationEntity.builder()
                .id("private-1")
                .project(ProjectEntity.builder().id(projectId).build())
                .ownerUser(ownerId == null ? null : UserEntity.builder().id(ownerId).build())
                .type(type)
                .scopeKey(projectId + ":PRIVATE:" + ownerId)
                .build();
    }
}
