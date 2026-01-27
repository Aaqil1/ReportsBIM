package com.edjobim.auth.controller;

import com.edjobim.auth.dto.TokenRequest;
import com.edjobim.auth.dto.TokenResponse;
import com.edjobim.auth.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.PublicKey;
import java.util.Map;

@RestController
@RequestMapping("/oauth2")
public class AuthController {

    private final TokenService tokenService;

    public AuthController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> issueToken(@RequestBody TokenRequest request) {
        String token = tokenService.generateToken(request.getUsername(), request.getRoles());
        return ResponseEntity.ok(new TokenResponse(token, "Bearer", 3600));
    }

    @GetMapping("/jwks")
    public ResponseEntity<Map<String, Object>> getJwks() {
        return ResponseEntity.ok(tokenService.getJwks());
    }
}
