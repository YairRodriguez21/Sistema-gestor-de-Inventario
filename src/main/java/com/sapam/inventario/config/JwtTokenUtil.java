package com.sapam.inventario.config;

import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtTokenUtil {

    private static final String SECRET_KEY = "sgia-secret-key-2026-very-secure-change-in-production";

    private final UsuarioRepository usuarioRepository;

    public JwtTokenUtil(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    // Clave simétrica derivada de SECRET_KEY (HS256 requiere al menos 256 bits)
    private static SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    // Obtener el username del token
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    // Obtener la fecha de expiración del token
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    // Verificar si el token ha expirado
    public boolean isTokenExpired(String token) {
        return getExpirationDateFromToken(token).before(new Date());
    }

    // Extraerclaim del token
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Extraer todas las claims del token
    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .setSigningKey(getSigningKey())
                .parseClaimsJws(token)
                .getBody();
    }

    // Generar token JWT
    public String generateToken(String username) {
        return Jwts
                .builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hora
                .claim("roles", obtenerRoles(username))
                .signWith(SignatureAlgorithm.HS256, getSigningKey())
                .compact();
    }

    /**
     * Roles del usuario, leídos de la tabla {@code usuarios}.
     *
     * Antes se deducían del texto del correo ({@code contains("admin")}), lo que
     * hacía que el rol no guardara relación con el que había en la base de datos.
     */
    private String[] obtenerRoles(String username) {
        return rolesDesdeBase(username);
    }

    /** Roles del correo indicado, tal y como están en {@code usuarios}. */
    public String[] rolesDesdeBase(String email) {
        if (email == null) {
            return new String[]{Usuario.Rol.consulta.rolSpring()};
        }
        return usuarioRepository.findByEmail(email)
                .map(u -> u.getRol() == null
                        ? new String[]{Usuario.Rol.consulta.rolSpring()}
                        : new String[]{u.getRol().rolSpring()})
                .orElse(new String[]{Usuario.Rol.consulta.rolSpring()});
    }

    // Validar si el token es válido
    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}