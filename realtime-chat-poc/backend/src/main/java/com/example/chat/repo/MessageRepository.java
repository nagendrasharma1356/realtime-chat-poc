package com.example.chat.repo;

import com.example.chat.model.ChatMessage;
import com.example.chat.model.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("select m from ChatMessage m " +
            "where (m.sender = :a and m.recipient = :b) or (m.sender = :b and m.recipient = :a) " +
            "order by m.sentAt asc, m.id asc")
    List<ChatMessage> findConversation(@Param("a") String a, @Param("b") String b);

    List<ChatMessage> findBySenderAndRecipientAndStatusNot(String sender, String recipient, MessageStatus status);

    /** rows: [recipient, sender, count] - used to rebuild Redis unread counters on startup */
    @Query("select m.recipient, m.sender, count(m) from ChatMessage m " +
            "where m.status <> :status group by m.recipient, m.sender")
    List<Object[]> unreadCounts(@Param("status") MessageStatus status);
}
