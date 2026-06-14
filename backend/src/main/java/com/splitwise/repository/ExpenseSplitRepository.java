package com.splitwise.repository;

import com.splitwise.entity.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, UUID> {

    List<ExpenseSplit> findAllByExpenseId(UUID expenseId);

    /**
     * Find all splits for all expenses within a specific group.
     * Used by BalanceService to compute group-wide net balances.
     */
    List<ExpenseSplit> findAllByExpenseGroupId(UUID groupId);
}
