package com.example.chat.controller;

import com.example.chat.dto.Dtos.MessageDto;
import com.example.chat.service.ChatService;
import com.example.chat.service.RedisStateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MessageController {

    private final ChatService chatService;
    private final RedisStateService redisState;

    public MessageController(ChatService chatService, RedisStateService redisState) {
        this.chatService = chatService;
        this.redisState = redisState;
    }

    /** Full persisted history between two users (oldest first). */
    @GetMapping("/conversations/{user}/{other}")
    public List<MessageDto> conversation(@PathVariable String user, @PathVariable String other) {
        return chatService.conversation(user, other);
    }

    /** Unread counters per sender, served from Redis. */
    @GetMapping("/unread/{user}")
    public Map<String, Long> unread(@PathVariable String user) {
        return redisState.unreadFor(user);
    }
}
