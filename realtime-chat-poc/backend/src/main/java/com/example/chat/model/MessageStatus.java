package com.example.chat.model;

public enum MessageStatus {
    SENT,       // saved on server, recipient offline
    DELIVERED,  // recipient was online when sent
    READ        // recipient opened the conversation
}
