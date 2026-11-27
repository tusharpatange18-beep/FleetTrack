package com.fleettrack.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Creates and checks JWT login tokens.
 * A JWT has three parts, header.payload.signature. The payload holds the username ("subject")
 * and the expiry time; the signature (HMAC-SHA256 with our secret key) proves we created it.
 */
@Service
public class JwtService {

    // Read from application.yml (jwt.secret). Base64 text of at least 32 random bytes.
    @Value("${jwt.secret}")
    private String secretKey;

    // How long a token stays valid, in milliseconds (from application.yml)
    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    /** Builds a signed token for this user, valid for jwtExpirationMs. */
    public String generateToken(UserDetails userDetails) {
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expiresAt = new Date(now + jwtExpirationMs);

        return Jwts.builder()
                .subject(userDetails.getUsername())       // who the token belongs to
                .issuedAt(issuedAt)                       // when it was created
                .expiration(expiresAt)                    // when it stops working
                .signWith(getSignInKey(), Jwts.SIG.HS256) // sign it with our secret key
                .compact();                               // turn it into the "xxx.yyy.zzz" text
    }

    /** Reads the username from the token. Throws an exception if the token is fake or expired. */
    public String extractUsername(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getSubject();
    }

    /** True if the token belongs to this user and has not expired yet. */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        boolean sameUser = username.equals(userDetails.getUsername());
        return sameUser && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        Claims claims = extractAllClaims(token);
        Date expiresAt = claims.getExpiration();
        return expiresAt.before(new Date());
    }

    /**
     * Checks the signature and expiry, then returns the token's payload ("claims").
     * If someone changed the token, the signature no longer matches and this throws an exception.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Turns the Base64 secret from the config into a key object the JWT library can use. */
    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
