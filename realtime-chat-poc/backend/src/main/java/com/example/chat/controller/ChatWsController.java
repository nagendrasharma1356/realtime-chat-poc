package com.example.chat.controller;

import com.example.chat.dto.Dtos.ReadRequest;
import com.example.chat.dto.Dtos.SendMessageRequest;
import com.example.chat.dto.Dtos.TypingEvent;
import com.example.chat.dto.Dtos.TypingRequest;
import com.example.chat.service.ChatService;
import com.example.chat.service.RedisStateService;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket (STOMP) endpoints. Client publishes to /app/chat.send, /app/chat.typing, /app/chat.read.
 */
@Controller
public class ChatWsController {

    private final ChatService chatService;
    private final RedisStateService redisState;
    private final SimpMessagingTemplate messaging;

    public ChatWsController(ChatService chatService, RedisStateService redisState, SimpMessagingTemplate messaging) {
        this.chatService = chatService;
        this.redisState = redisState;
        this.messaging = messaging;
    }

    @MessageMapping("/chat.send")
    public void send(@Payload SendMessageRequest request, Principal principal) {
        chatService.send(principal.getName(), request.to(), request.content());
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingRequest request, Principal principal) {
        String from = principal.getName();
        if (request.to() == null || request.to().isBlank()) {
            return;
        }
        if (redisState.shouldForwardTyping(from, request.to(), request.typing())) {
            messaging.convertAndSendToUser(request.to(), "/queue/typing", new TypingEvent(from, request.typing()));
        }
    }

    @MessageMapping("/chat.read")
    public void read(@Payload ReadRequest request, Principal principal) {
        if (request.with() == null || request.with().isBlank()) {
            return;
        }
        chatService.markRead(principal.getName(), request.with());
    }

    /** Errors are delivered privately to the user who caused them: /user/queue/errors */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Exception ex) {
        return ex.getMessage();
    }
}
