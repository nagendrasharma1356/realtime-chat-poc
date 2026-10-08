package com.example.chat.service;

import com.example.chat.dto.Dtos.MessageDto;
import com.example.chat.dto.Dtos.ReadReceipt;
import com.example.chat.model.ChatMessage;
import com.example.chat.model.MessageStatus;
import com.example.chat.repo.MessageRepository;
import com.example.chat.repo.NotificationRepository;
import com.example.chat.repo.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChatService {

    private final MessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RedisStateService redisState;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messaging;

    public ChatService(MessageRepository messageRepository,
                       NotificationRepository notificationRepository,
                       UserRepository userRepository,
                       RedisStateService redisState,
                       NotificationService notificationService,
                       SimpMessagingTemplate messaging) {
        this.messageRepository = messageRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.redisState = redisState;
        this.notificationService = notificationService;
        this.messaging = messaging;
    }

    @Transactional
    public MessageDto send(String from, String to, String content) {
        if (to == null || to.isBlank() || content == null || content.isBlank()) {
            throw new IllegalArgumentException("'to' and 'content' are required");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("You cannot message yourself");
        }
        if (!userRepository.existsByUsername(to)) {
            throw new IllegalArgumentException("User '" + to + "' does not exist");
        }
        String text = content.trim();
        if (text.length() > 2000) {
            throw new IllegalArgumentException("Message too long (max 2000 chars)");
        }

        MessageStatus status = redisState.isOnline(to) ? MessageStatus.DELIVERED : MessageStatus.SENT;
        ChatMessage saved = messageRepository.save(new ChatMessage(from, to, text, status));
        MessageDto dto = MessageDto.from(saved);

        redisState.incrementUnread(to, from);

        // push to both participants (sender gets an echo with the server-side id/status)
        messaging.convertAndSendToUser(to, "/queue/messages", dto);
        messaging.convertAndSendToUser(from, "/queue/messages", dto);

        String preview = text.length() > 80 ? text.substring(0, 80) + "..." : text;
        notificationService.create(to, "NEW_MESSAGE", "New message from " + from, preview, from);
        return dto;
    }

    @Transactional
    public List<Long> markRead(String reader, String sender) {
        List<ChatMessage> unread = messageRepository
                .findBySenderAndRecipientAndStatusNot(sender, reader, MessageStatus.READ);
        unread.forEach(m -> m.setStatus(MessageStatus.READ));
        messageRepository.saveAll(unread);

        notificationRepository.markMessageNotificationsSeen(reader, sender);
        redisState.clearUnread(reader, sender);

        List<Long> ids = unread.stream().map(ChatMessage::getId).toList();
        if (!ids.isEmpty()) {
            messaging.convertAndSendToUser(sender, "/queue/read-receipts", new ReadReceipt(reader, ids));
        }
        return ids;
    }

    @Transactional(readOnly = true)
    public List<MessageDto> conversation(String a, String b) {
        return messageRepository.findConversation(a, b).stream().map(MessageDto::from).toList();
    }
}
