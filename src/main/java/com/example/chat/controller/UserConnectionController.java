package com.example.chat.controller;

import com.example.chat.entity.*;
import com.example.chat.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/connection")
public class UserConnectionController {

    @Autowired
    UserConnectionService userConnectionService;

    @Autowired
    UserService userService;

    @Autowired
    UserConversationService userConversationService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @GetMapping("/get-connections")
    public ResponseEntity<?> getConnections(Authentication authentication) {

        try {
            UserDetails authUser = (UserDetails) authentication.getPrincipal();

            User user = userService.getUserByUserName(authUser.getUsername());

            List<UserConnection> userConnections = userConnectionService.getAllConnectionsById(user.getId());

            if (userConnections.isEmpty()) {
                return new ResponseEntity<>("No Connections Exists!", HttpStatus.NO_CONTENT);
            }

            for (UserConnection userConnection : userConnections) {
                String userName = user.getUserName()
                        .equals(userConnection.getUserName1()) ?
                            userConnection.getUserName2()
                            :
                            userConnection.getUserName1();

                User userDetails = userService.getUserByUserName(userName);

                Set<UserConversation> conversationsList = userConversationService.getConversations(userConnection.getConversationsId());

                long[] counts = conversationsList.stream()
                        .filter(data ->
                                    !data.getStatus().equals("Read") &&
                                    !(
                                        (data.getSender().equals(user.getUserName()) && data.isMessageDeletedByUser1())
                                        ||
                                        (data.getReceiver().equals(user.getUserName()) && data.isMessageDeletedByUser2())
                                    )
                        )
                        .collect(() -> new long[2], (arr, data) -> {
                            if (data.getSender().equals(userName)) arr[0]++;
                            if (data.getReceiver().equals(userName)) arr[1]++;
                        }, (arr1, arr2) -> {
                            arr1[0] += arr2[0];
                            arr1[1] += arr2[1];
                        });

                long user1Count = counts[0];
                long user2Count = counts[1];

                boolean isBlocked = userConnection.getUserName1().equals(authUser.getUsername()) ? userConnection.isBlockedByUser2() : userConnection.isBlockedByUser1();

                userConnection.setUserName(userName)
                        .setProfilePicture(isBlocked ? null : userDetails.getProfilePicture())
                        .setLastSeen(isBlocked ? null : userDetails.getLastSeen())
                        .setUnReadMsgsOfUser1(isBlocked ? "0" : user1Count + "")
                        .setUnReadMsgsOfUser2(isBlocked ? "0" : user2Count + "")
                        .setLoginStatus(isBlocked ? null : userDetails.getStatus())
                        .setEmail(userDetails.getEmail());
            }

            userConnections.sort((user1, user2) -> {
                LocalDateTime latest1 = user1.getUser1LastConversationAt().isAfter(user1.getUser2LastConversationAt())
                        ? user1.getUser1LastConversationAt()
                        : user1.getUser2LastConversationAt();

                LocalDateTime latest2 = user2.getUser1LastConversationAt().isAfter(user2.getUser2LastConversationAt())
                        ? user2.getUser1LastConversationAt()
                        : user2.getUser2LastConversationAt();

                return latest2.compareTo(latest1);
            });

            return new ResponseEntity<>(userConnections, HttpStatus.OK);

        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/get-searched-users/{searched}")
    public ResponseEntity<?> getSearchedUsers(Authentication authentication, @PathVariable String searched) {

        if (!authentication.isAuthenticated()){
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        List<UserService.UserDTO> users = userService.getUsersBySearch(searched, userDetails.getUsername());

        if (users.isEmpty()){
            return new ResponseEntity<>("No users found!", HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    @PostMapping("/change-user/block-state/{userName}={status}")
    public ResponseEntity<?> changeUserBlockState(Authentication authentication, @PathVariable String userName, boolean status) {
        try {
            UserDetails authUser = (UserDetails) authentication.getPrincipal();
            String participants = UserConnectionService.getSortedUserKey(authUser.getUsername(), userName);

            UserConnection userConnection = userConnectionService.getConnection(participants);

            if (userConnection.getUserName1().equals(authUser.getUsername())){
                userConnection.setBlockedByUser1(status);
            } else {
                userConnection.setBlockedByUser2(status);
            }

            userConnectionService.saveConnection(userConnection);

            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/delete-user-connection/{userName}/block={status}")
    public ResponseEntity<?> deleteUserConnection(Authentication authentication, @PathVariable String userName, @PathVariable boolean status) {
        try {
            UserDetails authUser = (UserDetails) authentication.getPrincipal();
            String participants = UserConnectionService.getSortedUserKey(authUser.getUsername(), userName);

            UserConnection userConnection = userConnectionService.getConnection(participants);

            if (userConnection.getUserName1().equals(authUser.getUsername())){
                userConnection.setConnectionDeletedByUser1(true)
                        .setBlockedByUser1(status)
                        .setRecentChatClearedByUser1(LocalDateTime.now())
                        .setUser2LastConversationId(null)
                        .setUser2LastConversation(null)
                        .setUser2LastConversationType(null)
                        .setUser2LastConversationAt(null);
            } else {
                userConnection.setConnectionDeletedByUser2(true)
                        .setBlockedByUser2(status)
                        .setRecentChatClearedByUser2(LocalDateTime.now())
                        .setUser1LastConversationId(null)
                        .setUser1LastConversation(null)
                        .setUser1LastConversationType(null)
                        .setUser1LastConversationAt(null);
            }

            userConnectionService.saveConnection(userConnection);

            if (status) {
                User receiver = userService.getUserByUserName(userName);
                userConnection.setEmail(receiver.getEmail());

                boolean isChatOpened = userConnection.getUserName1().equals(authUser.getUsername()) ? userConnection.isUser2ChatOpened() : userConnection.isUser1ChatOpened();

                messagingTemplate.convertAndSendToUser(
                        userName,
                        "/queue/messages",
                        List.of(new WebSocketsController.StatusResponse(authUser.getUsername(), isChatOpened ? null : new UserConversation(), userConnection, 200, "", "Receive"))
                );
            }

            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/change/chat-opened-status/{userName}")
    public ResponseEntity<?> changeChatOpenedStatus(Authentication authentication, @PathVariable String userName, @RequestBody Map<String, String> object) {
        try {
            UserDetails authUser = (UserDetails) authentication.getPrincipal();
            String participants = UserConnectionService.getSortedUserKey(authUser.getUsername(), userName);

            String prevParticipants = object.get("prevUserName").equals("None") ? null : UserConnectionService.getSortedUserKey(authUser.getUsername(), object.get("prevUserName"));

            UserConnection userConnection = userConnectionService.getConnection(participants);

            if (userConnection == null) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }

            if (prevParticipants != null){
                UserConnection prevUserConnection = userConnectionService.getConnection(prevParticipants);
                if (prevUserConnection.getUserName1().equals(authUser.getUsername()) && prevUserConnection.isUser1ChatOpened()) {
                    prevUserConnection.setUser1ChatOpened(false);
                } else if (prevUserConnection.getUserName2().equals(authUser.getUsername()) && prevUserConnection.isUser2ChatOpened()) {
                    prevUserConnection.setUser2ChatOpened(false);
                }
                userConnectionService.saveConnection(prevUserConnection);
            }

            if (userConnection.getUserName1().equals(authUser.getUsername())){
                userConnection.setUser1ChatOpened(true);
                userConnection.setUnReadMsgsOfUser2("0");
                Set<String> userConversationsId = userConnection.getConversationsId();
                Set<UserConversation> userConversations = userConversationService.getConversations(userConversationsId);
                for (UserConversation userConversation : userConversations) {
                    if (userConversation.getSender().equals(userConnection.getUserName2()) && !userConversation.isMessageDeletedByUser2()) {
                        if (userConversation.getStatus().equals("Read")){
                            break;
                        }
                        userConversation.setStatus("Read");
                    }
                }
                userConversationService.saveAllConversations(userConversations);
            } else if (userConnection.getUserName2().equals(authUser.getUsername())) {
                userConnection.setUser2ChatOpened(true);
                userConnection.setUnReadMsgsOfUser1("0");
                Set<String> userConversationsId = userConnection.getConversationsId();
                Set<UserConversation> userConversations = userConversationService.getConversations(userConversationsId);
                for (UserConversation userConversation : userConversations) {
                    if (userConversation.getSender().equals(userConnection.getUserName1()) && !userConversation.isMessageDeletedByUser1()) {
                        if (userConversation.getStatus().equals("Read")){
                            break;
                        }
                        userConversation.setStatus("Read");
                    }
                }
                userConversationService.saveAllConversations(userConversations);

            }

            userConnectionService.saveConnection(userConnection);

            if (!object.get("unReadMsgCount").equals("0") && !object.get("unReadMsgCount").equals("None")) {
                User user = userService.getUserByUserName(authUser.getUsername());
                userConnection.setEmail(user.getEmail())
                        .setLastSeen(user.getLastSeen())
                        .setLoginStatus(user.getStatus())
                        .setProfilePicture(user.getProfilePicture());

                messagingTemplate.convertAndSendToUser(
                        userName,
                        "/queue/messages",
                        List.of(new WebSocketsController.StatusResponse(authUser.getUsername(), null, userConnection, 200, "", "Receive"))
                );
            }

            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e){
            return new ResponseEntity<>("An error occurred: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/close-chat/{userName}")
    public ResponseEntity<?> closeUserChat(Authentication authentication, @PathVariable String userName) {
        try {
            UserDetails authUser = (UserDetails) authentication.getPrincipal();
            String participants = UserConnectionService.getSortedUserKey(authUser.getUsername(), userName);

            UserConnection userConnection = userConnectionService.getConnection(participants);

            if (userConnection.getUserName1().equals(authUser.getUsername())) {
                userConnection.setUser1ChatOpened(false);
            } else if (userConnection.getUserName2().equals(authUser.getUsername())) {
                userConnection.setUser2ChatOpened(false);
            }

            userConnectionService.saveConnection(userConnection);

            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e, HttpStatus.BAD_REQUEST);
        }
    }
}
