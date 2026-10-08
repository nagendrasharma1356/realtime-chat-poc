package com.example.chat.repo;

import com.example.chat.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByRecipientOrderByCreatedAtDesc(String recipient);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.seen = true " +
            "where n.recipient = :recipient and n.refUser = :sender " +
            "and n.type = 'NEW_MESSAGE' and n.seen = false")
    int markMessageNotificationsSeen(@Param("recipient") String recipient, @Param("sender") String sender);
}
