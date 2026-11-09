package com.astral.exchange;

import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final MarketService market;
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final SecurityContextRepository contexts;
    private final SseHub hub;
    public ApiController(MarketService market,JdbcTemplate db,PasswordEncoder passwords,SecurityContextRepository contexts,SseHub hub) {
        this.market=market;this.db=db;this.passwords=passwords;this.contexts=contexts;this.hub=hub;
    }
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken()); }
    public record Credentials(String username,String password) {}
    @PostMapping("/auth/register")
    public Map<String,Object> register(@RequestBody Credentials c,HttpServletRequest request,HttpServletResponse response) {
        validate(c);
        String hash=passwords.encode(c.password());
        market.register(() -> {
            if (!db.queryForList("SELECT id FROM app_user WHERE username=?",c.username()).isEmpty()) throw new IllegalArgumentException("用户名已被使用");
            db.update("INSERT INTO app_user(username,password_hash,created_at) VALUES(?,?,?)",c.username(),hash,System.currentTimeMillis());
            Long id=db.queryForObject("SELECT id FROM app_user WHERE username=?",Long.class,c.username());
            db.update("INSERT INTO account VALUES(?,?)",id,MarketMath.CAPITAL);
            return id;
        });
        signIn(c.username(),request,response);
        return market.account(userId(c.username()));
    }
    @PostMapping("/auth/login")
    public Map<String,Object> login(@RequestBody Credentials c,HttpServletRequest request,HttpServletResponse response) {
        validate(c);
        var users=db.queryForList("SELECT username,password_hash FROM app_user WHERE username=?",c.username());
        if(users.isEmpty() || !passwords.matches(c.password(),users.getFirst().get("password_hash").toString())) throw new AuthException();
        String username=users.getFirst().get("username").toString();
        signIn(username,request,response);
        return market.account(userId(username));
    }
    private void validate(Credentials c) {
        if(c.username()==null || !c.username().matches("[\\p{L}\\p{N}_]{2,24}") || c.password()==null || c.password().length()<8 || c.password().length()>64)
            throw new IllegalArgumentException("用户名需 2–24 位文字、数字或下划线，密码需 8–64 位");
    }
    private void signIn(String name,HttpServletRequest request,HttpServletResponse response) {
        request.getSession(); request.changeSessionId();
        var principal=User.withUsername(name).password("").roles("PLAYER").build();
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
        SecurityContextHolder.setContext(context); contexts.saveContext(context,request,response);
    }
    private long userId(String name) { return db.queryForObject("SELECT id FROM app_user WHERE username=?",Long.class,name); }
    @GetMapping("/market") public Object market() { return market.market(); }
    @GetMapping("/candles/{symbol}") public Object candles(@PathVariable String symbol) { return market.candles(symbol); }
    @GetMapping("/account") public Object account(Principal p) { return market.account(userId(p.getName())); }
    @GetMapping("/ranking") public Object ranking(@RequestParam(required=false) String day) { return market.ranking(day); }
    @PostMapping("/orders/preview") public Object preview(@RequestBody MarketService.OpenRequest request) { return market.preview(request); }
    @PostMapping("/orders") public Object open(Principal p,@RequestBody MarketService.OpenRequest request) { return market.open(userId(p.getName()),request); }
    public record CloseRequest(String requestId) {}
    @PostMapping("/positions/{id}/close") public Object close(Principal p,@PathVariable String id,@RequestBody CloseRequest request) { return market.close(userId(p.getName()),id,request.requestId()); }
    @GetMapping(value="/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() { return hub.connect(market.market()); }
    static class AuthException extends RuntimeException {}
}
