package com.splitwise.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "group_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GroupMembership {

    @EmbeddedId
    @EqualsAndHashCode.Include
    private GroupMembershipId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("groupId")
    @JoinColumn(name = "group_id")
    @ToString.Exclude
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private User user;

    @Column(name = "joined_date")
    private LocalDate joinedDate;

    @Column(name = "left_date")
    private LocalDate leftDate;

    public GroupMembership(Group group, User user, LocalDate joinedDate) {
        this.id = new GroupMembershipId(group.getId(), user.getId());
        this.group = group;
        this.user = user;
        this.joinedDate = joinedDate;
    }
}
