package com.splitwise.repository;

import com.splitwise.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findAllByGroupId(UUID groupId);

    List<Expense> findAllByGroupIdOrderByCreatedAtDesc(UUID groupId);

    List<Expense> findAllByGroupIdOrderByDateDescCreatedAtDesc(UUID groupId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT e FROM Expense e JOIN e.splits s " +
        "WHERE e.group.id = :groupId AND " +
        "((e.payer.id = :user1Id AND s.user.id = :user2Id) OR " +
        "(e.payer.id = :user2Id AND s.user.id = :user1Id)) " +
        "ORDER BY e.date DESC, e.createdAt DESC"
    )
    List<Expense> findBilateralExpenses(
        @org.springframework.data.repository.query.Param("groupId") UUID groupId,
        @org.springframework.data.repository.query.Param("user1Id") UUID user1Id,
        @org.springframework.data.repository.query.Param("user2Id") UUID user2Id
    );
}
