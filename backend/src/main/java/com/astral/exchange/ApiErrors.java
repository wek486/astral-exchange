package com.astral.exchange;

import java.io.IOException;
import java.time.DateTimeException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    // An SSE client can disappear between the scheduler's send and Tomcat's async dispatch.
    // The response is already unusable; there is no error body to send to that client.
    @ExceptionHandler(IOException.class)
    void disconnectedClient() {}
    @ExceptionHandler({IllegalArgumentException.class,DateTimeException.class,HttpMessageNotReadableException.class})
    ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(Map.of("message",e instanceof HttpMessageNotReadableException ? "请求格式不正确" : e.getMessage())); }
    @ExceptionHandler(ApiController.AuthException.class)
    ResponseEntity<?> auth() { return ResponseEntity.status(401).body(Map.of("message","用户名或密码不正确")); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("message","数据冲突，请刷新后重试")); }
}
