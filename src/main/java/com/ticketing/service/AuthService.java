package com.ticketing.service;

import com.ticketing.domain.entity.User;
import com.ticketing.domain.enums.UserRole;
import com.ticketing.domain.repository.UserRepository;
import com.ticketing.dto.request.LoginRequest;
import com.ticketing.dto.request.RegisterRequest;
import com.ticketing.dto.response.AuthResponse;
import com.ticketing.exception.ResourceNotFoundException;
import com.ticketing.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository     userRepository;
    private final PasswordEncoder    passwordEncoder;
    private final JwtTokenProvider   jwtTokenProvider;
    private final AuthenticationManager authManager;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + req.getEmail());
        }
        User user = User.builder()
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .role(UserRole.USER)
                .build();
        userRepository.save(user);
        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getRole().name(), user.getId().toString());
        return new AuthResponse(token, user.getEmail(), user.getRole().name(), user.getId());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        authManager.authenticate(new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getRole().name(), user.getId().toString());
        return new AuthResponse(token, user.getEmail(), user.getRole().name(), user.getId());
    }
}
