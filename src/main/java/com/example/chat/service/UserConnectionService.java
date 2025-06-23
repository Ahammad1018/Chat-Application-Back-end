package com.example.chat.service;
import com.example.chat.entity.*;
import com.example.chat.repository.UserConnectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class UserConnectionService {

    @Autowired
    UserConnectionRepository userConnectionRepository;

    public List<UserConnection> getAllConnectionsById(String id) {
        return userConnectionRepository.findByUserId1OrUserId2(id);
    }

    public Optional<UserConnection> getConnectionById(String id) {
        return userConnectionRepository.findById(id);
    }

    public List<UserConnection> getAllConnectionsByUsername(String userName) {
        return userConnectionRepository.findByUserName1OrUserName2(userName);
    }

    public UserConnection getConnection(String participants) {
        return userConnectionRepository.findByParticipants(participants);
    }

    public UserConnection saveConnection(UserConnection userConnection){
        return userConnectionRepository.save(userConnection);
    }

    public void saveManyConnections(List<UserConnection> userConnectionsList) {
        userConnectionRepository.saveAll(userConnectionsList);
    }

    public void deleteConnection(String id) {
        userConnectionRepository.deleteById(id);
    }

    public static String getSortedUserKey(String user1, String user2) {
        if (user1.compareTo(user2) <= 0) {
            return user1 + "~" + user2;
        } else {
            return user2 + "~" + user1;
        }
    }

}
