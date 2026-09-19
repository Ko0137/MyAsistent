package com.example.myjarvis;

public class ChatMessage {
    private String message;
    private boolean isUser;
    private String time;

    public ChatMessage(String message, boolean isUser, String time) {
        this.message = message;
        this.isUser = isUser;
        this.time = time;
    }

    public String getMessage() { return message; }
    public boolean isUser() { return isUser; }
    public String getTime() { return time; }
}
