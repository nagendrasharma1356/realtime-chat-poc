package com.example.chat.listener;

import com.example.chat.dto.Dtos.PresenceEvent;
import com.example.chat.repo.UserRepository;
import com.example.chat.service.RedisStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;

/** Maintains online/offline status in Redis and broadcasts changes on /topic/presence. */
@Component
public class PresenceEventListener {

    private static final Logger log = LoggerFactory.getLogger(PresenceEventListener.class);

    private final RedisStateService redisState;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messaging;

    public PresenceEventListener(RedisStateService redisState,
                                 UserRepository userRepository,
                                 SimpMessagingTemplate messaging) {
        this.redisState = redisState;
        this.userRepository = userRepository;
        this.messaging = messaging;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
        if (user == null || sessionId == null) {
            return;
        }
        if (redisState.sessionOpened(user.getName(), sessionId)) {
            log.info("{} is ONLINE", user.getName());
            messaging.convertAndSend("/topic/presence", new PresenceEvent(user.getName(), true));
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user == null) {
            return;
        }
        if (redisState.sessionClosed(user.getName(), event.getSessionId())) {
            log.info("{} is OFFLINE", user.getName());
            userRepository.findByUsername(user.getName()).ifPresent(u -> {
                u.setLastSeen(Instant.now());
                userRepository.save(u);
            });
            messaging.convertAndSend("/topic/presence", new PresenceEvent(user.getName(), false));
        }
    }
}
