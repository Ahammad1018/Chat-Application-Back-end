package com.example.chat.entity;

import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.*;

@Document
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    private String id;

    @Nonnull
    @Indexed
    private String userName;

    @Nonnull
    @Indexed(unique = true)
    private String email;

    @Nonnull
    private String password;
    private String profilePicture;
    private String status = "offline";
    private boolean isActive = true;
    private List<String> roles = new ArrayList<>(List.of("User"));
    private LocalDateTime lastSeen;
    private LocalDateTime createdAt = LocalDateTime.now();

}
