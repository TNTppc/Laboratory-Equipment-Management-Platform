package cn.labmanage.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final String secret;
    private final long ttlHours;
    public JwtService(@Value("${security.jwt-secret}") String secret, @Value("${security.jwt-ttl-hours}") long ttlHours) { this.secret = secret; this.ttlHours = ttlHours; }
    private SecretKey key() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    public String issue(Long userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder().subject(username).claim("uid", userId).claim("role", role).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttlHours * 3600))).signWith(key()).compact();
    }
    public io.jsonwebtoken.Claims parse(String token) { return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload(); }
}
