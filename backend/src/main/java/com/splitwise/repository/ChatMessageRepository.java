package com.splitwise.repository;

import com.splitwise.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * Retrieve chat history for an expense, ordered chronologically.
     */
    List<ChatMessage> findAllByExpenseIdOrderByCreatedAtAsc(UUID expenseId);
}
