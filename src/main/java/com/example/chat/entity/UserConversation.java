package com.example.chat.entity;

import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Document
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class UserConversation {

    @Id
    private String id;

    @Nonnull
    private String sender;

    @Nonnull
    private String senderId;

    @Nonnull
    private String receiver;

    @Nonnull
    private String receiverId;

    @Nonnull
    private String message;

    @Nonnull
    private String messageType;

    private String status;

    private LocalDateTime createdAt = LocalDateTime.now();

    private String fileName;
    private String fileSize;
    private boolean isReplied = false;
    private String repliedBy;
    private String repliedMessageId;

    private boolean messageDeletedByUser1 = false;
    private boolean messageDeletedByUser2 = false;
}
