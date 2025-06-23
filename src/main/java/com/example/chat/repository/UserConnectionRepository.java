package com.example.chat.repository;

import com.example.chat.entity.User;
import com.example.chat.entity.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.*;


public interface UserConnectionRepository extends MongoRepository<UserConnection, String> {

    @Query("{ '$or': [ { 'userId1': ?0 }, { 'userId2': ?0 } ] }")
    List<UserConnection> findByUserId1OrUserId2(String userId);

    @Query("{ '$or': [ { 'userName1': ?0 }, { 'userName2': ?0 } ] }")
    List<UserConnection> findByUserName1OrUserName2(String userName);

    UserConnection findByParticipants(String participants);

}