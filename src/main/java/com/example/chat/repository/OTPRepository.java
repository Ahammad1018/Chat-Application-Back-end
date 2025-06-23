package com.example.chat.repository;

import com.example.chat.entity.UserOTP;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;

public interface OTPRepository  extends MongoRepository<UserOTP, String> {

    UserOTP findByEmail(String email);

    void deleteByEmail(String email);

    @Query("{ 'createdAt': { $lt: ?0 } }")
    void deleteByCreatedAtBefore(LocalDateTime expiryTime);

}
