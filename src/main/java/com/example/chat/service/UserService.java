package com.example.chat.service;
import com.example.chat.entity.User;
import com.example.chat.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class UserService {

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder encoder;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserDTO {
        private String id;
        private String userName;
        private String status;
        private String profilePicture;
        private LocalDateTime lastSeen;
    }
    
    public Optional<User> getUserById(String id){
        return userRepository.findById(id);
    }

    public User getUserByUserName(String userName){
        return userRepository.findByUserName(userName);
    }

    public String getUserNameByEmail(String email) {
        User user = userRepository.findUserByEmail(email);
        return user != null ? user.getUserName() : null;
    }

    public void saveUser(User user) {
        userRepository.save(user);
    }

    public void changePassword(String email, String newPassword) {
        User user = userRepository.findUserByEmail(email);
        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);
    }

    public boolean isUserOnline(String userName) {
        return userRepository.findByUserName(userName).getStatus().equals("online");
    }

    public List<UserDTO> getUsersBySearch(String search, String userName) {
        List<User> users = userRepository.findAll();
        return users.stream()
                .filter(user ->
                        !user.getUserName().equals(userName) &&
                        (user.getUserName().startsWith(search) || user.getEmail().equals(search)))
                .map(user -> new UserDTO(
                        user.getId(),
                        user.getUserName(),
                        user.getStatus(),
                        user.getProfilePicture(),
                        user.getLastSeen()
                ))
                .toList();
    }
}
