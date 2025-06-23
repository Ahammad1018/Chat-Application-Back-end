package com.example.chat.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.Base64;

@Component
public class JwtUtil {

    private final String secretKey = Base64.getEncoder().encodeToString("MySuperSecretKey123456!".getBytes());

    // Create the signing key from the secret key string
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String generateToken(String username) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        // 1 hour
        long expiration = Duration.ofDays(1).toMillis();
        Date exp = new Date(nowMillis + expiration);

        return Jwts.builder()
                .claim("sub", username)  // Setting the 'sub' claim
                .claim("iat", now.getTime())  // Setting the 'iat' claim for issued time
                .claim("exp", exp.getTime())  // Setting the 'exp' claim for expiration time
                .signWith(getSigningKey()) // use the `signWith()` method here, which is fine
                .compact();
    }

    public String validateToken(String token) {
        try {
            JwtParser parser = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build();

            Jws<Claims> jws = parser.parseClaimsJws(token); // Will throw if expired or invalid
            Claims claims = jws.getBody();

            return claims.getSubject(); // Extract username (subject)

        } catch (ExpiredJwtException e) {
            System.out.println("Token expired: " + e.getMessage());
        } catch (MalformedJwtException e) {
            System.out.println("Malformed token: " + e.getMessage());
        } catch (SignatureException e) {
            System.out.println("Invalid signature: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("Illegal Argument Token: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Token validation failed: " + e.getMessage());
        }

        return "Token is invalid!"; // Token is invalid
    }

}
