package com.sapam.inventario.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida el token JWT enviado en el header "Authorization: Bearer ..."
 * y lo convierte en un SecurityContext con los roles del usuario.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;

    public JwtAuthenticationFilter(JwtTokenUtil jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = header.substring(7).trim();

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String username = jwtTokenUtil.getUsernameFromToken(token);

                if (!jwtTokenUtil.isTokenExpired(token) && username != null) {
                    Claims claims = jwtTokenUtil.getClaimFromToken(token, c -> c);

                    List<SimpleGrantedAuthority> authorities = extraerRoles(claims);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            // Token inválido o expirado: se continúa sin autenticación
            SecurityContextHolder.clearContext();
            logger.warn("Token JWT inválido: " + e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extrae los roles del claim "roles". Según cómo serialice Jackson el claim,
     * puede llegar como String[], List u otro objeto, por eso se trata de forma flexible.
     */
    private List<SimpleGrantedAuthority> extraerRoles(Claims claims) {
        Object rolesClaim = claims.get("roles");
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        if (rolesClaim == null) {
            return authorities;
        }

        if (rolesClaim instanceof Object[] arr) {
            for (Object rol : arr) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + rol));
            }
        } else if (rolesClaim instanceof Iterable<?> iterable) {
            for (Object rol : iterable) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + rol));
            }
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + rolesClaim));
        }
        return authorities;
    }
}
