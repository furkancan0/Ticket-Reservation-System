package com.ticketing.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data public class RegisterRequest {
    @NotBlank @Email private String email;
    @NotBlank @Size(min=8) private String password;
    private String firstName;
    private String lastName;
}
