package com.example.chat.dto;

import com.example.chat.model.ChatMessage;
import com.example.chat.model.MessageStatus;
import com.example.chat.model.Notification;

import java.time.Instant;
import java.util.List;

/** All request/response/event payloads in one place. */
public final class Dtos {

    private Dtos() {
    }

    // ---- inbound (client -> server) ----
    public record LoginRequest(String username) {}
    public record SendMessageRequest(String to, String content) {}
    public record TypingRequest(String to, boolean typing) {}
    public record ReadRequest(String with) {}
    public record SendNotificationRequest(String to, String title, String body) {}

    // ---- outbound (server -> client) ----
    public record UserDto(String username, boolean online, Instant lastSeen) {}

    public record MessageDto(Long id, String sender, String recipient, String content,
                             Instant sentAt, MessageStatus status) {
        public static MessageDto from(ChatMessage m) {
            return new MessageDto(m.getId(), m.getSender(), m.getRecipient(),
                    m.getContent(), m.getSentAt(), m.getStatus());
        }
    }

    public record NotificationDto(Long id, String type, String title, String body,
                                  String refUser, Instant createdAt, boolean seen) {
        public static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getBody(),
                    n.getRefUser(), n.getCreatedAt(), n.isSeen());
        }
    }

    public record TypingEvent(String from, boolean typing) {}
    public record ReadReceipt(String reader, List<Long> messageIds) {}
    public record PresenceEvent(String username, boolean online) {}
}
