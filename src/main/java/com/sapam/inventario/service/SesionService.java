package com.sapam.inventario.service;

import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Quién está operando la API en este momento.
 *
 * El responsable de una entrada, una salida o un préstamo <strong>no es un
 * campo que se pueda elegir en el cuerpo de la petición</strong>: sale de la
 * cuenta que firmó el token. Si fuera elegible, quien registra podría firmar
 * por otra cuenta y después negarlo, que es exactamente lo que la trazabilidad
 * del sistema viene a impedir. Es el mismo criterio que ya usa la ventana de
 * escritorio, donde el responsable es el usuario con la sesión abierta.
 */
@Service
public class SesionService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    /**
     * @return la cuenta del token
     * @throws ResponseStatusException 401 si no hay sesión o la cuenta ya no
     *         está activa (un token sigue siendo válido hasta que expira, así
     *         que hay que comprobarlo aquí y no solo en el filtro)
     */
    public Usuario actual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        String email = autenticacion == null ? null : autenticacion.getName();

        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No hay sesión iniciada");
        }

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "La cuenta del token ya no existe: " + email));

        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "La cuenta está desactivada: " + email);
        }

        return usuario;
    }

    /** Identificador que se pasa a los servicios como responsable de la operación. */
    public Integer actualId() {
        return actual().getId();
    }
}
