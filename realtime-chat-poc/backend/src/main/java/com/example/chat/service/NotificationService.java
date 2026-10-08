package com.example.chat.service;

import com.example.chat.dto.Dtos.NotificationDto;
import com.example.chat.model.Notification;
import com.example.chat.repo.NotificationRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final SimpMessagingTemplate messaging;

    public NotificationService(NotificationRepository repository, SimpMessagingTemplate messaging) {
        this.repository = repository;
        this.messaging = messaging;
    }

    /** Persist (so offline users see it on next login) and push live to /user/{recipient}/queue/notifications. */
    @Transactional
    public NotificationDto create(String recipient, String type, String title, String body, String refUser) {
        Notification saved = repository.save(new Notification(recipient, type, title, body, refUser));
        NotificationDto dto = NotificationDto.from(saved);
        messaging.convertAndSendToUser(recipient, "/queue/notifications", dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> latestFor(String recipient) {
        return repository.findTop50ByRecipientOrderByCreatedAtDesc(recipient)
                .stream().map(NotificationDto::from).toList();
    }

    @Transactional
    public void markSeen(Long id) {
        repository.findById(id).ifPresent(n -> n.setSeen(true));
    }
}
