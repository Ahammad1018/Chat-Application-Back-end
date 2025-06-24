package com.example.chat.controller;

import com.example.chat.entity.*;
import com.example.chat.security.JwtUtil;
import com.example.chat.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/login")
public class LoginController {

    @Data
    public static class AuthRequest {
        private String email;
        private String password;
    }

    @Data
    public static class UserOTPRequest {
        private String email;
        private String authCode;
    }

    @Data
    public static class UserRegistrationRequest {
        private User userData;
        private String otp;
    }

    @Autowired
    LoginService loginService;

    @Autowired
    UserService userService;

    @Autowired
    OTPService otpService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    UserConnectionService userConnectionService;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    private static SimpMessagingTemplate messagingTemplate;

    @PostMapping("/create-new-user")
    public ResponseEntity<?> createUser(
            @RequestPart("userInfo") String userDataJson,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();
        UserRegistrationRequest userRegistrationRequest = objectMapper.readValue(userDataJson, UserRegistrationRequest.class);

        User userData = userRegistrationRequest.getUserData();
        String otp = userRegistrationRequest.getOtp();
        String validate = otpService.validateNewUserOtp(userData.getEmail(), otp);

        if (validate.equals("Invalid")) {
            return new ResponseEntity<>("Invalid Details", HttpStatus.NOT_ACCEPTABLE);
        }

        if (validate.equals("Expired")) {
            return new ResponseEntity<>("OTP expired!", HttpStatus.REQUEST_TIMEOUT);
        }

        if (file != null && !file.isEmpty()) {
            try {
                String profileUrl =  cloudinaryService.uploadFile(file);
                userData.setProfilePicture(profileUrl);
            } catch (IOException e) {
                return new ResponseEntity<>("Failed to upload file!", HttpStatus.EXPECTATION_FAILED);
            }
        }

        loginService.saveData(userData);
        return new ResponseEntity<>("User created successfully!", HttpStatus.CREATED);
    }

    @PostMapping("/user-login")
    public ResponseEntity<?> userLogin(@RequestBody AuthRequest authRequest,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        try {
            String userName = userService.getUserNameByEmail(authRequest.getEmail());

            if (userName == null) {
                return ResponseEntity.status(404).body("Username not found");
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(userName);

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(userDetails.getUsername(), authRequest.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String jwtToken = jwtUtil.generateToken(userDetails.getUsername());
            String key = userService.getUserByUserName(userDetails.getUsername()).getId();
            String encodedKey = encoder.encode(key);

            // Get CSRF token from the request (Spring Security adds it automatically)
            CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");

            Cookie cookie = new Cookie("XSRF-TOKEN", csrfToken.getToken());
            cookie.setPath("/");
            cookie.setHttpOnly(false); // Make it accessible to JavaScript
            response.addCookie(cookie);

            User user = userService.getUserByUserName(userName);
            user.setStatus("online");
            userService.saveUser(user);
            user.setPassword("");

            Map<String, Object> result = new HashMap<>();
            result.put("jwt", jwtToken);
//            result.put("csrfToken", csrfToken.getToken());
            result.put("key", encodedKey);
//            result.put("csrfHeaderName", csrfToken.getHeaderName());
            result.put("userData", user);

            return new ResponseEntity<>(result, HttpStatus.OK);

        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body("Invalid username or password");
        }
    }

    @GetMapping("/csrf-token")
    public ResponseEntity<?> csrfToken(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());

        if (token == null) {
            return ResponseEntity.noContent().build(); // no token available
        }

        // Return the token value and header name in JSON so client can use it
        return ResponseEntity.ok()
                .body(new CsrfTokenResponse(token.getHeaderName(), token.getToken()));
    }

    record CsrfTokenResponse(String headerName, String token) {
    }

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOTP(@RequestBody UserOTPRequest userOTPRequest) throws MessagingException {
        String userName = userService.getUserNameByEmail(userOTPRequest.getEmail());
        if (userName == null) {
            return new ResponseEntity<>("User not found!", HttpStatus.NOT_FOUND);
        }

        otpService.sendOtpEmail(userName, userOTPRequest.getEmail(), "ResetPassword", "");
        return new ResponseEntity<>("OTP send successfully", HttpStatus.CREATED);
    }

    @PostMapping("/send-otp/new-{username}")
    public ResponseEntity<?> newUser_SendOTP(@RequestBody UserOTPRequest userOTPRequest, @PathVariable String username) throws MessagingException {
        User userName = userService.getUserByUserName(username);
        if (userName != null) {
            return new ResponseEntity<>("User found!", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        otpService.sendOtpEmail(username, userOTPRequest.getEmail(), "SignUp", "");
        return new ResponseEntity<>("OTP send successfully", HttpStatus.CREATED);
    }

    @PostMapping("/validate-otp")
    public ResponseEntity<?> validateOTP(@RequestBody UserOTPRequest userOTPRequest) {
        String userName = userService.getUserNameByEmail(userOTPRequest.getEmail());
        if (userName == null) {
            return new ResponseEntity<>("User not found!", HttpStatus.NOT_FOUND);
        }
        String res = otpService.validateOtp(userOTPRequest.getEmail(), userOTPRequest.getAuthCode());
        if (res.equals("Invalid")) {
            return new ResponseEntity<>("Invalid Details", HttpStatus.NOT_ACCEPTABLE);
        }

        if (res.equals("Expired")) {
            return new ResponseEntity<>("OTP expired!", HttpStatus.REQUEST_TIMEOUT);
        }
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    @PutMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody UserOTPRequest userOTPRequest, @RequestHeader("Authorization") String authorizationHeader) {

        String token;
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            token = authorizationHeader.substring(7);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Authorization header");
        }

        if (userOTPRequest == null) {
            return new ResponseEntity<>("No user data found!", HttpStatus.NOT_FOUND);
        }

        String res = otpService.resetPasswordByValidatingToken(
                    userOTPRequest.getEmail(),
                    userOTPRequest.getAuthCode(),
                    token
                );

        if (res.contains("Invalid")) {
            return new ResponseEntity<>("Invalid Details", HttpStatus.NOT_ACCEPTABLE);
        }

        if (res.equals("Token expired!")) {
            return new ResponseEntity<>("Session expired!", HttpStatus.REQUEST_TIMEOUT);
        }

        return new ResponseEntity<>("Password reset successful!", HttpStatus.CREATED);
    }

}
