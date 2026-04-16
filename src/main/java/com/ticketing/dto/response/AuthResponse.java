package com.ticketing.dto.response;
import lombok.*;
import java.util.UUID;
@Data @AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private String role;
    private UUID   userId;
}
