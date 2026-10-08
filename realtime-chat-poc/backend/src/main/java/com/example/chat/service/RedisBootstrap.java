package com.example.chat.service;

import com.example.chat.model.MessageStatus;
import com.example.chat.repo.MessageRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On startup: wipe stale presence (old sockets are gone) and rebuild Redis unread
 * counters from the H2 source of truth, so counters survive a Redis flush or restart.
 */
@Component
public class RedisBootstrap implements ApplicationRunner {

    private final RedisStateService redisState;
    private final MessageRepository messageRepository;

    public RedisBootstrap(RedisStateService redisState, MessageRepository messageRepository) {
        this.redisState = redisState;
        this.messageRepository = messageRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        redisState.resetPresence();
        redisState.resetUnread();
        for (Object[] row : messageRepository.unreadCounts(MessageStatus.READ)) {
            redisState.setUnread((String) row[0], (String) row[1], ((Number) row[2]).longValue());
        }
    }
}
