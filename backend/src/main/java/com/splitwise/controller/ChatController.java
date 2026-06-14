package com.splitwise.controller;

import com.splitwise.dto.ChatMessageResponse;
import com.splitwise.entity.ChatMessage;
import com.splitwise.entity.Expense;
import com.splitwise.entity.User;
import com.splitwise.exception.ResourceNotFoundException;
import com.splitwise.repository.ChatMessageRepository;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    public ChatController(ChatMessageRepository chatMessageRepository,
                          ExpenseRepository expenseRepository,
                          UserRepository userRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
    }

    /**
     * WebSocket endpoint for posting messages.
     * Expects destination: /app/expenses/{expenseId}/chat
     * Broadcasts to: /topic/expenses/{expenseId}
     */
    @MessageMapping("/expenses/{expenseId}/chat")
    @SendTo("/topic/expenses/{expenseId}")
    @Transactional
    public ChatMessageResponse sendMessage(
            @DestinationVariable UUID expenseId,
            String text,
            Principal principal) {

        if (principal == null) {
            throw new RuntimeException("Unauthenticated websocket connection");
        }

        User sender = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));

        ChatMessage message = ChatMessage.builder()
                .expense(expense)
                .sender(sender)
                .text(text.trim())
                .build();

        message = chatMessageRepository.save(message);

        return mapToResponse(message);
    }

    /**
     * REST endpoint to fetch chat history when a user opens the expense.
     */
    @GetMapping("/api/expenses/{expenseId}/messages")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ChatMessageResponse>> getChatHistory(
            @PathVariable UUID expenseId,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        // Ensure user is valid (further authorization could be added to ensure user is in the group)
        userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        List<ChatMessage> messages = chatMessageRepository.findAllByExpenseIdOrderByCreatedAtAsc(expenseId);
        
        List<ChatMessageResponse> response = messages.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    private ChatMessageResponse mapToResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .expenseId(message.getExpense().getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getName())
                .text(message.getText())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
