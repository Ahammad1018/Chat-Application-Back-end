package com.example.chat.service;
import com.example.chat.entity.*;
import com.example.chat.repository.*;
import com.example.chat.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class UserConversationService {

    @Autowired
    UserConversationRepository userConversationRepository;

    public Set<UserConversation> getConversations(Set<String> ids){
        List<UserConversation> iterable = userConversationRepository.findAllById(ids);
        Set<UserConversation> result = new TreeSet<>(
                Comparator.comparing(UserConversation::getCreatedAt)
                        .thenComparing(UserConversation::getId)
                        .reversed()
        );
        result.addAll(iterable);
        return result;
    }

    public Optional<UserConversation> getConversation(String id){
        return userConversationRepository.findById(id);
    }

    public UserConversation saveConversation(UserConversation userConversation) {
        return userConversationRepository.save(userConversation);
    }

    public void saveAllConversations(Set<UserConversation> userConversations){
        userConversationRepository.saveAll(userConversations);
    }

    public void deleteConversation(String id) {
        userConversationRepository.deleteById(id);
    }

    public void deleteManyConversation(List<String> ids) {
        userConversationRepository.deleteAllById(ids);
    }

    public UserConversation getLastConversationExcludeId(Set<String> ids, Set<String> excludeIds, String userName) {
        Set<UserConversation> userConversations = getConversations(ids);

        return userConversations.stream()
                .filter(conversation ->
                        !excludeIds.contains(conversation.getId())
                        && (
                            (conversation.getSender().equals(userName) && !conversation.isMessageDeletedByUser1())
                                    ||
                            (conversation.getReceiver().equals(userName) && !conversation.isMessageDeletedByUser2())
                        )
                ).max(Comparator.comparing(UserConversation::getCreatedAt))
                .orElse(null); // or throw an exception if that's preferable
    }

    public UserConversation getLastConversation(Set<String> ids, String userName) {
        Set<UserConversation> userConversations = getConversations(ids);

        return userConversations.stream()
                .filter(conversation ->
                                ((conversation.getSender().equals(userName) && !conversation.isMessageDeletedByUser1())
                                || (conversation.getReceiver().equals(userName) && !conversation.isMessageDeletedByUser2()))
                )
                .findFirst()
                .orElse(null); // or throw an exception if that's preferable
    }

}
