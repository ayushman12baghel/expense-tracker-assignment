package com.splitwise.repository;

import com.splitwise.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {

    /**
     * Find all groups where the given user is a member.
     */
    @Query("SELECT g FROM Group g JOIN g.memberships m WHERE m.user.id = :userId")
    List<Group> findAllByMemberId(@Param("userId") UUID userId);
}
