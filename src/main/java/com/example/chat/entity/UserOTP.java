package com.example.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserOTP {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;
    private String authCode;

    @CreatedDate
    @Indexed
    private LocalDateTime createdAt = LocalDateTime.now();
}
