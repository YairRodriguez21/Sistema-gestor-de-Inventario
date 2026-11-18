package com.sapam.inventario.service;

import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Optional;

/**
 * Acceso de la ventana de escritorio.
 *
 * Las contraseñas se guardan en texto plano (decisión del proyecto: ver
 * {@code SecurityConfig.passwordEncoder()}), así que aquí lo que se compara es
 * literalmente el valor de la columna. Es el único sitio por el que se entra
 * desde la ventana; la API pasa por el AuthenticationManager sobre la misma
 * columna, de modo que las dos rutas no pueden disentir sobre si una
 * contraseña vale.
 */
@Service
public class AuthService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    /**
     * @return la cuenta si la contraseña es correcta, vacío en otro caso
     */
    public Optional<Usuario> entrar(String email, String password) {
        return usuarioRepository.findByEmail(email)
                .filter(usuario -> password != null && password.equals(usuario.getPassword()));
    }

    public boolean autenticar(String email, String password) {
        return entrar(email, password).isPresent();
    }

    public Optional<Usuario> obtenerUsuarioPorEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }
}
