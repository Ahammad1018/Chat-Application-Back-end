package com.example.chat.repository;

import com.example.chat.entity.*;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;


public interface UserConversationRepository extends MongoRepository<UserConversation, String> {

}