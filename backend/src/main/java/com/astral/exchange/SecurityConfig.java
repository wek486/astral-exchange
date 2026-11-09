package com.astral.exchange;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityContextRepository securityContexts() { return new HttpSessionSecurityContextRepository(); }
    @Bean SecurityFilterChain security(HttpSecurity http, SecurityContextRepository repository) throws Exception {
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        return http.authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**","/api/csrf","/api/market","/api/candles/**","/api/events","/api/ranking","/error").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/","/index.html","/favicon.svg","/assets","/assets/**","/ranking","/challenge").permitAll()
                .anyRequest().authenticated())
            .csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .securityContext(s -> s.securityContextRepository(repository))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex) -> json(res,401,"请先登录"))
                .accessDeniedHandler((req,res,ex) -> json(res,403,"请求已失效，请刷新后重试")))
            .logout(l -> l.logoutUrl("/api/logout").logoutSuccessHandler((req,res,auth) -> json(res,200,"已退出")))
            .addFilterBefore(new AuthRateLimit(),CsrfFilter.class)
            .build();
    }
    private static void json(HttpServletResponse res,int status,String message) throws IOException {
        res.setStatus(status); res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\""+message+"\"}");
    }
    static class AuthRateLimit extends OncePerRequestFilter {
        private record Window(long start,int attempts) {}
        private final ConcurrentHashMap<String,Window> attempts = new ConcurrentHashMap<>();
        @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
            if (req.getMethod().equals("POST") && req.getRequestURI().startsWith("/api/auth/")) {
                long now=System.currentTimeMillis();
                attempts.entrySet().removeIf(e -> now-e.getValue().start>60000);
                Window w=attempts.compute(req.getRemoteAddr(),(k,v) -> v==null ? new Window(now,1): new Window(v.start,v.attempts+1));
                if (w.attempts>20) { json(res,429,"尝试过于频繁，请一分钟后再试"); return; }
            }
            chain.doFilter(req,res);
        }
    }
}
