package com.example.chat.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_msg_sender_recipient", columnList = "sender,recipient"),
        @Index(name = "idx_msg_recipient_status", columnList = "recipient,status")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String sender;

    @Column(nullable = false, length = 30)
    private String recipient;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private Instant sentAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MessageStatus status = MessageStatus.SENT;

    protected ChatMessage() {
    }

    public ChatMessage(String sender, String recipient, String content, MessageStatus status) {
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public String getContent() { return content; }
    public Instant getSentAt() { return sentAt; }
    public MessageStatus getStatus() { return status; }
    public void setStatus(MessageStatus status) { this.status = status; }
}
