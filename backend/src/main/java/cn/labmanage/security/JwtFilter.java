package cn.labmanage.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final JdbcTemplate db;
    public JwtFilter(JwtService jwt, JdbcTemplate db) { this.jwt = jwt; this.db = db; }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) try {
            var claims = jwt.parse(header.substring(7));
            Integer enabled = db.queryForObject("SELECT COUNT(*) FROM lab_user WHERE username=? AND enabled=1", Integer.class, claims.getSubject());
            if (enabled == null || enabled == 0) throw new IllegalArgumentException("Account is disabled");
            var auth = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.get("role", String.class))));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception ignored) { SecurityContextHolder.clearContext(); }
        chain.doFilter(req, res);
    }
}
