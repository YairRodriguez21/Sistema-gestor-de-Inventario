package com.sapam.inventario.config;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;

    public AuthenticationController(AuthenticationManager authenticationManager,
                                    JwtTokenUtil jwtTokenUtil) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
    }

    @PostMapping("/login")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest loginRequest) {
        // 1. Autenticar al usuario (usuario y contraseña)
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(),
                loginRequest.getPassword()
        );
        try {
            authenticationManager.authenticate(authToken);
        } catch (AuthenticationException e) {
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales incorrectas"));
        }

        // 2. Si la autenticación es exitosa, obtener el nombre de usuario
        String email = loginRequest.getEmail();

        // 3. Generar token JWT con los roles del usuario
        String token = jwtTokenUtil.generateToken(email);

        // 4. Retornar el token y información del usuario
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("email", email);
        response.put("role", "ROLE_" + obtenerRolPorEmail(email)); // Agregamos el role en la respuesta

        return ResponseEntity.ok(response);
    }

    @GetMapping("/usuario-perfil")
    @ResponseBody
    public Map<String, Object> usuarioPerfil() {
        // Este endpoint requiere estar autenticado y devuelve el perfil del usuario
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String email = auth == null ? null : auth.getName();
        String rol = auth == null || auth.getAuthorities().isEmpty()
                ? "CONSULTA"
                : auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");

        Map<String, Object> perfil = new HashMap<>();
        perfil.put("email", email);
        perfil.put("role", "ROLE_" + rol);
        perfil.put("acceso", "concedido");
        return perfil;
    }

    // Método auxiliar para obtener el rol por email (lo lee de la tabla usuarios)
    private String obtenerRolPorEmail(String email) {
        String[] roles = jwtTokenUtil.rolesDesdeBase(email);
        return roles.length > 0 ? roles[0] : "CONSULTA";
    }

    // Clase interna para la request de login
    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}