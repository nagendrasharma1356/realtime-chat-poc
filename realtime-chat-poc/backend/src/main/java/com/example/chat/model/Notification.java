package com.example.chat.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_recipient", columnList = "recipient")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String recipient;

    /** NEW_MESSAGE, SYSTEM, ... */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 500)
    private String body;

    /** For NEW_MESSAGE: the user who sent the message. */
    @Column(length = 30)
    private String refUser;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private boolean seen = false;

    protected Notification() {
    }

    public Notification(String recipient, String type, String title, String body, String refUser) {
        this.recipient = recipient;
        this.type = type;
        this.title = title;
        this.body = body;
        this.refUser = refUser;
    }

    public Long getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getRefUser() { return refUser; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isSeen() { return seen; }
    public void setSeen(boolean seen) { this.seen = seen; }
}
