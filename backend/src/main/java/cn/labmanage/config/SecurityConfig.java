package cn.labmanage.config;

import cn.labmanage.security.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwt) throws Exception {
        return http.csrf(c -> c.disable()).cors(Customizer.withDefaults()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/api/auth/login", "/api/auth/register", "/uploads/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll().anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> jsonError(res,401,"请先登录")).accessDeniedHandler((req,res,ex) -> jsonError(res,403,"无权执行该操作")))
            .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class).build();
    }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    private static void jsonError(HttpServletResponse response,int status,String message) throws java.io.IOException { response.setStatus(status);response.setCharacterEncoding("UTF-8");response.setContentType("application/json");response.getWriter().write("{\"success\":false,\"message\":\""+message+"\",\"data\":null}"); }
}
