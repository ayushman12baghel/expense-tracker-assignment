package com.splitwise.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupResponse {

    private UUID id;
    private String name;
    private LocalDateTime createdAt;
    private List<GroupMemberResponse> members;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GroupMemberResponse {
        private UUID id;
        private String name;
        private String email;
    }
}
