package com.example.urlshortener.auth;

import com.example.urlshortener.auth.dto.LoginRequest;
import com.example.urlshortener.auth.dto.RegisterRequest;
import com.example.urlshortener.auth.dto.TokenResponse;
import com.example.urlshortener.auth.dto.UserResponse;
import com.example.urlshortener.user.User;
import com.example.urlshortener.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserResponse(user.getId(), user.getEmail()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userService.authenticate(request.email(), request.password());
        return new TokenResponse(jwtService.generate(user), "Bearer", jwtService.expiresInSeconds());
    }


}
