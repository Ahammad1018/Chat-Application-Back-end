package com.example.chat.repository;

import com.example.chat.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;


public interface LoginRepository extends MongoRepository<User, String> {

}