package com.carechain.auth.model;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthResponse {
    private String token;
    private String email;
    private String role;
}
