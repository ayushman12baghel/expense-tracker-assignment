package com.splitwise.repository;

import com.splitwise.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    List<Settlement> findAllByGroupId(UUID groupId);

    List<Settlement> findAllByGroupIdOrderByCreatedAtDesc(UUID groupId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT s FROM Settlement s " +
        "WHERE s.group.id = :groupId AND " +
        "((s.payer.id = :user1Id AND s.payee.id = :user2Id) OR " +
        "(s.payer.id = :user2Id AND s.payee.id = :user1Id)) " +
        "ORDER BY s.createdAt DESC"
    )
    List<Settlement> findBilateralSettlements(
        @org.springframework.data.repository.query.Param("groupId") UUID groupId,
        @org.springframework.data.repository.query.Param("user1Id") UUID user1Id,
        @org.springframework.data.repository.query.Param("user2Id") UUID user2Id
    );
}
