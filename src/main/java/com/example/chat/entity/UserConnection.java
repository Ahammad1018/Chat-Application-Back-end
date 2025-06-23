package com.example.chat.entity;

import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.*;

@Document
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class UserConnection {

    @Id
    private String id;

    @Indexed(unique = true)
    private String participants;

    @Nonnull
    @Indexed
    private String userId1;

    @Nonnull
    @Indexed
    private String userName1;

    @Nonnull
    @Indexed
    private String userId2;

    @Nonnull
    @Indexed
    private String userName2;

    @Indexed
    private Set<String> conversationsId;

    private String user1LastConversation;
    private String user1LastConversationId;
    private LocalDateTime user1LastConversationAt;
    private String user1LastConversationType;

    private String user2LastConversation;
    private String user2LastConversationId;
    private LocalDateTime user2LastConversationAt;
    private String user2LastConversationType;

    private String userName;
    private String email;
    private String profilePicture;
    private LocalDateTime lastSeen;
    private String unReadMsgsOfUser1;
    private String unReadMsgsOfUser2;
    private String loginStatus;

    private boolean isBlockedByUser1 = false;
    private boolean isBlockedByUser2 = false;
    private boolean isConnectionDeletedByUser1 = false;
    private boolean isConnectionDeletedByUser2 = false;
    private boolean user1ChatOpened;
    private boolean user2ChatOpened;

    private LocalDateTime recentChatClearedByUser1;
    private LocalDateTime recentChatClearedByUser2;
    private LocalDateTime connectedAt = LocalDateTime.now();

}

