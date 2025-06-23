package com.example.chat.service;

import com.example.chat.entity.UserOTP;
import com.example.chat.repository.OTPRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.File;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;


@Component
public class OTPService {

    private final OTPRepository otpRepository;

    @Autowired
    UserService userService;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    PasswordEncoder encoder;

    public OTPService(OTPRepository otpRepository) {
        this.otpRepository = otpRepository;
    }

    // Every 5 minutes
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void cleanExpiredOTPs() {
        LocalDateTime expiryTime = LocalDateTime.now().minusMinutes(30);
        otpRepository.deleteByCreatedAtBefore(expiryTime);
    }

    public String generateOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000); // 6-digit
        return String.valueOf(otp);
    }

    public void sendOtpEmail(String userName, String toEmail, String type, String link) throws MessagingException {
        String otp = generateOtp();

        otpRepository.deleteByEmail(toEmail);

        UserOTP user = new UserOTP();
        user.setEmail(toEmail);
        user.setAuthCode(encoder.encode(otp));

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(toEmail);

        Map<String, String> templates = type.equals("Invite") ? emailTemplates(userName, type, "", link) : emailTemplates(userName, type, otp,"");

        helper.setSubject(templates.get("subject"));
        helper.setText(templates.get("template"), true);
        helper.setFrom("chatapp.team2025@gmail.com");

        mailSender.send(message);
        otpRepository.save(user);
    }

    public String validateOtp(String email, String enteredOtp) {
        UserOTP user = otpRepository.findByEmail(email);
        if (user == null) {
            return "Invalid";
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.getCreatedAt().plusMinutes(5).isBefore(now)) {
            otpRepository.deleteByEmail(email);
            return "Expired";
        }

        if (encoder.matches(enteredOtp, user.getAuthCode())) {
            String generatedToken = generateOneTimeToken();
            user.setAuthCode(encoder.encode(generatedToken));
            user.setCreatedAt(LocalDateTime.now());
            otpRepository.save(user);
            return generatedToken;
        }

        return "Invalid";
    }

    public String validateNewUserOtp(String email, String enteredOtp) {
        UserOTP user = otpRepository.findByEmail(email);
        if (user == null) {
            return "Invalid";
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.getCreatedAt().plusMinutes(5).isBefore(now)) {
            otpRepository.deleteByEmail(email);
            return "Expired";
        }

        if (encoder.matches(enteredOtp, user.getAuthCode())) {
            return "Validated";
        }

        return "Invalid";
    }

    public String resetPasswordByValidatingToken(String email, String authCode, String token) {
        UserOTP userOTP = otpRepository.findByEmail(email);
        if (userOTP == null) {
            return "Invalid user!";
        }

        if (userOTP.getCreatedAt().plusMinutes(5).isBefore(LocalDateTime.now())){
            otpRepository.deleteByEmail(email);
            return "Token expired!";
        }

        if (encoder.matches(token, userOTP.getAuthCode())){
            System.out.println("UserDetails Before Saving" + email + " " + authCode);
            userService.changePassword(email, authCode);
            otpRepository.deleteByEmail(email);
            return "Validated";
        }

        return "Invalid";
    }

    private String generateOneTimeToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Map<String, String> emailTemplates(String userName, String type, String otp, String inviteLink) {
        String signUpSubject = "Verify Your Email to Join ChatApp";
        String resetPasswordSubject = "Password Reset OTP Code";
        String inviteSubject = "You're Invited to Join ChatApp!";

        String signUpTemplate = "<html>"
                + "<body style='font-family: Arial, sans-serif; color: #333; background-color: #ffffff; padding: 20px;'>"
                + "<div style='text-align: center;'>"
                + "<img src='https://res.cloudinary.com/dnabniyug/image/upload/v1748327110/Chat-Application-Icon_yfcdgr.png' alt='ChatApp Logo' style='width: 120px; height: auto; margin-bottom: 20px; border-radius: 50%;'/>"
                + "</div>"
                + "<hr style='border: none; border-top: 1px solid #ccc; margin: 20px 0;'>"
                + "<h2 style='color: #2c3e50;'>Welcome to ChatApp!</h2>"
                + "<p>Hi "
                + userName
                + ",</p>"
                + "<p>Thank you for signing up for <strong>ChatApp</strong>. To verify your email address, please enter the following One-Time Password (OTP) in the app:</p>"
                + "<div style='font-size: 24px; font-weight: bold; background-color: #f4f6f8; padding: 12px 20px; border-radius: 6px; display: inline-block; letter-spacing: 3px; margin: 10px 0;'>"
                + otp
                + "</div>"
                + "<p>This OTP is valid for the next <strong>5 minutes</strong>. Please keep it confidential and do not share it with anyone.</p>"
                + "<p>If you did not request this verification, you can safely ignore this email.</p>"
                + "<p>We're excited to have you on board!<br><br>Best regards,<br><strong>The ChatApp Team</strong></p>"
                + "</body>"
                + "</html>";

        String resetPasswordTemplate = "<html>"
                + "<body style='font-family: Arial, sans-serif; color: #333; background-color: #ffffff; padding: 20px;'>"
                + "<div style='text-align: center;'>"
                + "<img src='https://res.cloudinary.com/dnabniyug/image/upload/v1748327110/Chat-Application-Icon_yfcdgr.png' alt='ChatApp Logo' style='width: 120px; height: auto; margin-bottom: 20px; border-radius: 50%;'/>"
                + "</div>"
                + "<hr style='border: none; border-top: 1px solid #ccc; margin: 20px 0;'>"
                + "<h2 style='color: #2c3e50;'>Reset Your Password</h2>"
                + "<p>Hi "
                + userName
                + ",</p>"
                + "<p>We received a request to reset your ChatApp password. To proceed, please enter the following One-Time Password (OTP) in the app:</p>"

                + "<div style='font-size: 24px; font-weight: bold; background-color: #f4f6f8; padding: 12px 20px; border-radius: 6px; display: inline-block; letter-spacing: 3px; margin: 10px 0;'>"
                + otp
                + "</div>"

                + "<p>This OTP is valid for the next <strong>5 minutes</strong>. Please do not share it with anyone.</p>"
                + "<p>If you did not request a password reset, you can safely ignore this email — your password will remain unchanged.</p>"
                + "<p>Stay secure,<br><br>Best regards,<br><strong>The ChatApp Team</strong></p>"
                + "</body>"
                + "</html>";

        String inviteTemplate = "<html>"
                + "<body style='font-family: Arial, sans-serif; color: #333; background-color: #ffffff; padding: 20px;'>"
                + "<div style='text-align: center;'>"
                + "<img src='https://res.cloudinary.com/dnabniyug/image/upload/v1748327110/Chat-Application-Icon_yfcdgr.png' alt='ChatApp Logo' style='width: 120px; height: auto; margin-bottom: 20px; border-radius: 50%;'/>"
                + "</div>"
                + "<hr style='border: none; border-top: 1px solid #ccc; margin: 20px 0;'>"
                + "<h2 style='color: #2c3e50;'>You're Invited to Join ChatApp!</h2>"
                + "<p>Hi there,</p>"
                + "<p><strong>" + userName + "</strong> has invited you to join them on <strong>ChatApp</strong> — a simple, secure, and fast way to stay connected with friends and discover new people to chat with!</p>"
                + "<p>Click the button below to create your free account and start chatting:</p>"

                + "<div style='margin: 20px 0;'>"
                + "<a href='" + inviteLink + "' style='background-color: #007bff; color: #ffffff; text-decoration: none; padding: 12px 20px; border-radius: 5px; font-weight: bold;'>"
                + "Join ChatApp</a>"
                + "</div>"

                + "<p>By joining, you'll instantly be able to connect with <strong>" + userName + "</strong> and others in the ChatApp community. It's easy to get started and find new friends to chat with!</p>"
                + "<p>If the button above doesn't work, copy and paste this link into your browser:</p>"
                + "<p><a href='" + inviteLink + "' style='color: #007bff;'>" + inviteLink + "</a></p>"

                + "<p>We can't wait to welcome you!<br><br>Cheers,<br><strong>The ChatApp Team</strong></p>"
                + "</body>"
                + "</html>";


        return switch (type) {
            case "SignUp" -> Map.of(
                    "subject", signUpSubject,
                    "template", signUpTemplate
            );
            case "ResetPassword" -> Map.of(
                    "subject", resetPasswordSubject,
                    "template", resetPasswordTemplate
            );
            case "Invite" -> Map.of(
                    "subject", inviteSubject,
                    "template", inviteTemplate
            );
            default -> throw new IllegalArgumentException("Unknown email template type: " + type);
        };
    }

}
