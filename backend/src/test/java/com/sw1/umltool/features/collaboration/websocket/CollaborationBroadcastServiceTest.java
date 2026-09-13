package com.sw1.umltool.features.collaboration.websocket;

import com.sw1.umltool.features.auth.repository.UserRepository;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CollaborationBroadcastServiceTest {
    @Test
    void concurrentSendsToOneSessionAreSerializedByOneDecorator() throws Exception {
        CollaborationBroadcastService service = service();
        WebSocketSession session = session("s1");
        AtomicInteger writes = new AtomicInteger();
        CountDownLatch firstWrite = new CountDownLatch(1);
        doAnswer(invocation -> {
            writes.incrementAndGet();
            firstWrite.countDown();
            Thread.sleep(2);
            return null;
        }).when(session).sendMessage(any(TextMessage.class));
        service.add("diagram-1", session);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 24; i++) executor.submit(() -> service.send(session, Map.of("type", "PRESENCE")));
        assertTrue(firstWrite.await(2, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (writes.get() < 24 && System.nanoTime() < deadline) Thread.sleep(5);
        assertEquals(24, writes.get());
        assertEquals(1, service.sessions("diagram-1").size());
    }

    @Test
    void registeringTheSameSessionTwiceKeepsOneWrapper() {
        CollaborationBroadcastService service = service();
        WebSocketSession session = session("s1");
        service.add("diagram-1", session);
        WebSocketSession first = service.sessions("diagram-1").iterator().next();
        service.add("diagram-1", session);
        WebSocketSession second = service.sessions("diagram-1").iterator().next();

        assertNotSame(session, first);
        assertSame(first, second);
        assertEquals(1, service.sessions("diagram-1").size());
    }

    @Test
    void failedSessionIsRemovedAndDoesNotPreventOtherSession() throws Exception {
        CollaborationBroadcastService service = service();
        WebSocketSession failed = session("failed");
        WebSocketSession healthy = session("healthy");
        doThrow(new IOException("closed")).when(failed).sendMessage(any(TextMessage.class));
        service.add("diagram-1", failed);
        service.add("diagram-1", healthy);

        assertFalse(service.send(failed, Map.of("type", "OPERATION_ACK")));
        assertTrue(service.send(healthy, Map.of("type", "OPERATION_APPLIED")));
        verify(healthy, times(1)).sendMessage(any(TextMessage.class));
        assertEquals(1, service.sessions("diagram-1").size());
        assertEquals("healthy", service.sessions("diagram-1").iterator().next().getId());
    }

    @Test
    void presenceBroadcastContinuesWhenOneDestinationFails() throws Exception {
        CollaborationBroadcastService service = service();
        WebSocketSession failed = session("failed");
        WebSocketSession healthy = session("healthy");
        doThrow(new IOException("closed")).when(failed).sendMessage(any(TextMessage.class));
        service.add("diagram-1", failed);
        service.add("diagram-1", healthy);

        service.broadcastPresence("diagram-1");

        verify(healthy, times(1)).sendMessage(any(TextMessage.class));
        assertEquals(1, service.sessions("diagram-1").size());
    }

    @Test
    void closedSessionIsIgnoredAndRemoved() {
        CollaborationBroadcastService service = service();
        WebSocketSession session = session("s1");
        when(session.isOpen()).thenReturn(false);
        service.add("diagram-1", session);

        assertFalse(service.send(session, Map.of("type", "RESYNC")));
        assertTrue(service.sessions("diagram-1").isEmpty());
    }

    @Test
    void disconnectCleansSessionRegistry() {
        CollaborationBroadcastService service = service();
        WebSocketSession session = session("s1");
        service.add("diagram-1", session);
        service.remove("diagram-1", session);

        assertTrue(service.sessions("diagram-1").isEmpty());
    }

    private CollaborationBroadcastService service() {
        ObjectMapper mapper = mock(ObjectMapper.class);
        try {
            when(mapper.writeValueAsString(any())).thenReturn("{}");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        return new CollaborationBroadcastService(mapper, mock(DiagramRepository.class),
                mock(ProjectAccessService.class), mock(UserRepository.class));
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        return session;
    }
}
