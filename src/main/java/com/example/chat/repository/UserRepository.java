package com.example.chat.repository;

import com.example.chat.entity.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;


public interface UserRepository extends MongoRepository<User, String> {

    User findByUserName(String userName);

    User findUserByEmail(String email);

}
