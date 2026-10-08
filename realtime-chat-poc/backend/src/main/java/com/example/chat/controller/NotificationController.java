package com.example.chat.controller;

import com.example.chat.dto.Dtos.NotificationDto;
import com.example.chat.dto.Dtos.SendNotificationRequest;
import com.example.chat.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/{user}")
    public List<NotificationDto> list(@PathVariable String user) {
        return notificationService.latestFor(user);
    }

    @PostMapping("/{id}/seen")
    public void markSeen(@PathVariable Long id) {
        notificationService.markSeen(id);
    }

    /** Trigger a SYSTEM notification from Postman/curl - it is pushed live over WebSocket. */
    @PostMapping("/send")
    public NotificationDto send(@RequestBody SendNotificationRequest request) {
        return notificationService.create(request.to(), "SYSTEM", request.title(), request.body(), null);
    }
}
