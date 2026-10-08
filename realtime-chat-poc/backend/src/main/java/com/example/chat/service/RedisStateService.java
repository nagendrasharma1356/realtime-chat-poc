package com.example.chat.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * All "hot", short-lived chat state lives in Redis:
 *
 *   presence:online            SET   usernames that currently have >= 1 open WebSocket session
 *   presence:sessions:{user}   SET   open WebSocket session ids of that user (multi-tab support)
 *   unread:{recipient}         HASH  sender -> unread message count
 *   typing:{from}:{to}         STRING with 2s TTL, used to throttle typing events
 *
 * Durable data (messages, notifications, users) lives in H2.
 */
@Service
public class RedisStateService {

    private static final String ONLINE_KEY = "presence:online";
    private static final String SESSIONS_PREFIX = "presence:sessions:";
    private static final String UNREAD_PREFIX = "unread:";
    private static final String TYPING_PREFIX = "typing:";

    private final StringRedisTemplate redis;

    public RedisStateService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // ---------------- presence ----------------

    /** @return true if the user just transitioned offline -> online */
    public boolean sessionOpened(String user, String sessionId) {
        redis.opsForSet().add(SESSIONS_PREFIX + user, sessionId);
        Long added = redis.opsForSet().add(ONLINE_KEY, user);
        return added != null && added == 1L;
    }

    /** @return true if the user just transitioned online -> offline (last tab closed) */
    public boolean sessionClosed(String user, String sessionId) {
        redis.opsForSet().remove(SESSIONS_PREFIX + user, sessionId);
        Long remaining = redis.opsForSet().size(SESSIONS_PREFIX + user);
        if (remaining == null || remaining == 0L) {
            Long removed = redis.opsForSet().remove(ONLINE_KEY, user);
            return removed != null && removed == 1L;
        }
        return false;
    }

    public boolean isOnline(String user) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(ONLINE_KEY, user));
    }

    public Set<String> onlineUsers() {
        Set<String> members = redis.opsForSet().members(ONLINE_KEY);
        return members == null ? new HashSet<>() : members;
    }

    /** Called on startup: sockets from a previous run are gone, so clear stale presence. */
    public void resetPresence() {
        redis.delete(ONLINE_KEY);
        Set<String> keys = redis.keys(SESSIONS_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    // ---------------- unread counters ----------------

    public void incrementUnread(String recipient, String sender) {
        redis.opsForHash().increment(UNREAD_PREFIX + recipient, sender, 1);
    }

    public void setUnread(String recipient, String sender, long count) {
        redis.opsForHash().put(UNREAD_PREFIX + recipient, sender, String.valueOf(count));
    }

    public void clearUnread(String recipient, String sender) {
        redis.opsForHash().delete(UNREAD_PREFIX + recipient, sender);
    }

    public Map<String, Long> unreadFor(String recipient) {
        Map<Object, Object> raw = redis.opsForHash().entries(UNREAD_PREFIX + recipient);
        Map<String, Long> result = new HashMap<>();
        raw.forEach((k, v) -> result.put(String.valueOf(k), Long.parseLong(String.valueOf(v))));
        return result;
    }

    public void resetUnread() {
        Set<String> keys = redis.keys(UNREAD_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    // ---------------- typing indicator ----------------

    /**
     * "typing=true" is forwarded at most once per 2 seconds per pair (throttle),
     * "typing=false" is always forwarded and clears the throttle key.
     */
    public boolean shouldForwardTyping(String from, String to, boolean typing) {
        String key = TYPING_PREFIX + from + ":" + to;
        if (typing) {
            Boolean first = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(2));
            return Boolean.TRUE.equals(first);
        }
        redis.delete(key);
        return true;
    }
}
