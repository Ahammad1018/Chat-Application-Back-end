package com.example.chat.controller;
import com.example.chat.entity.*;
import com.example.chat.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    UserService userService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    CloudinaryService cloudinaryService;

    @Autowired
    OTPService otpService;

    @Autowired
    LoginService loginService;

    @Autowired
    UserConnectionService userConnectionService;

    @Autowired
    SimpMessagingTemplate messagingTemplate;


    @GetMapping("/get-user")
    public ResponseEntity<?> getUser(@AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
        }

        String userName = userDetails.getUsername();
        return new ResponseEntity<>(userService.getUserByUserName(userName), HttpStatus.OK);
    }

    @PostMapping("/change/status/{status}")
    public ResponseEntity<?> changeStatus(Authentication authentication, @PathVariable String status) {

        if (authentication.isAuthenticated()){
            UserDetails authUser = (UserDetails) authentication.getPrincipal();

            User user = userService.getUserByUserName(authUser.getUsername());

            user.setStatus(status);
            user.setLastSeen(LocalDateTime.now());

            userService.saveUser(user);

            loginService.notifyLoginStatus(user.getUserName(), status);

            return new ResponseEntity<>(HttpStatus.CREATED);
        }

        return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(Authentication authentication, @RequestBody Map<String, String> newCredentials) {
        try {

            if (authentication.isAuthenticated()) {
                UserDetails authUser = (UserDetails) authentication.getPrincipal();

                User user = userService.getUserByUserName(authUser.getUsername());

                if (passwordEncoder.matches(newCredentials.get("oldPassword"), user.getPassword())) {

                    user.setPassword(passwordEncoder.encode(newCredentials.get("newPassword")));

                    userService.saveUser(user);

                    return new ResponseEntity<>("Changed successfully!", HttpStatus.CREATED);
                }

                return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
            }

            return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);

        } catch (Exception e) {
            return new ResponseEntity<>("Something went wrong!", HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/update-profile-picture/cloudinary")
    public ResponseEntity<?> updateFileToCloudinary(Authentication authentication, @RequestParam("profilePicture") MultipartFile newImage, @RequestParam("oldUrl") String oldImageUrl, @RequestParam("fileType") String fileType ) throws Exception {
        try {
            if (authentication.isAuthenticated()) {
                UserDetails authUser = (UserDetails) authentication.getPrincipal();

                User user = userService.getUserByUserName(authUser.getUsername());

                boolean isDeleted = oldImageUrl.equals("default") || cloudinaryService.deleteFile(oldImageUrl, fileType);
                if (isDeleted) {
                    String fileURL = cloudinaryService.uploadFile(newImage);

                    user.setProfilePicture(fileURL);
                    userService.saveUser(user);

                    return new ResponseEntity<>(fileURL, HttpStatus.CREATED);
                }

                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        } catch (IOException e) {
            return new ResponseEntity<>(e, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/invite/new-user")
    public ResponseEntity<?> inviteNewUser(Authentication authentication, @RequestBody Map<String, String> inviteRequest) {
        try {
            if (authentication.isAuthenticated()) {
                UserDetails authUser = (UserDetails) authentication.getPrincipal();

                User user = userService.getUserByUserName(authUser.getUsername());

                String email = inviteRequest.get("email");
                String inviteLink = inviteRequest.get("inviteLink");

                otpService.sendOtpEmail(user.getUserName(), email, "Invite", inviteLink);

                return new ResponseEntity<>(HttpStatus.OK);
            }
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        } catch (Exception e) {
            return new ResponseEntity<>(e, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/block-user/{state}/{userName}")
    public ResponseEntity<?> manageBlockState(Authentication authentication, @PathVariable String userName, @PathVariable boolean state) {
        try {
            UserDetails authUser = (UserDetails) authentication.getPrincipal();
            User user = userService.getUserByUserName(authUser.getUsername());

            String participants = UserConnectionService.getSortedUserKey(user.getUserName(), userName);
            UserConnection userConnection = userConnectionService.getConnection(participants);

            if (userConnection == null){
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }

            if (userConnection.getUserName1().equals(user.getUserName())){
                userConnection.setBlockedByUser1(state);
                if(userConnection.isConnectionDeletedByUser1()) {
                    userConnection.setConnectionDeletedByUser1(false);
                }
            } else if (userConnection.getUserName2().equals(user.getUserName())) {
                userConnection.setBlockedByUser2(state);
                if(userConnection.isConnectionDeletedByUser2()) {
                    userConnection.setConnectionDeletedByUser2(false);
                }
            }

            userConnectionService.saveConnection(userConnection);

            User receiver = userService.getUserByUserName(userName);

            userConnection.setEmail(receiver.getEmail())
                    .setLastSeen(!state ? receiver.getLastSeen() : null)
                    .setLoginStatus(!state ? receiver.getStatus() : null)
                    .setProfilePicture(!state ? user.getProfilePicture() : null);

            boolean isChatOpened = userConnection.getUserName1().equals(user.getUserName()) ? userConnection.isUser2ChatOpened() : userConnection.isUser1ChatOpened();

            messagingTemplate.convertAndSendToUser(
                    userName,
                    "/queue/messages",
                    List.of(new WebSocketsController.StatusResponse(user.getUserName(), isChatOpened ? null : new UserConversation(), userConnection, 200, "", "Receive"))
            );

            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(e, HttpStatus.BAD_REQUEST);
        }
    }

}
