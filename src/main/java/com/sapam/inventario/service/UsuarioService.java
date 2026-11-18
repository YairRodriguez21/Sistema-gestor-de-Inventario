package com.sapam.inventario.service;

import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cuentas del sistema.
 *
 * Las altas y las ediciones se validan aquí y no en el controlador, para que
 * den el mismo resultado se pidan por HTTP o desde la ventana de escritorio.
 * La baja es lógica y no física: una cuenta que ya registró entradas, salidas
 * o eventos de bitácora no se puede borrar sin romper su trazabilidad, y la
 * fila se conserva para auditoría. Una cuenta inactiva además no puede entrar
 * (lo comprueba el {@code UserDetailsService}).
 *
 * Las contraseñas que llegan aquí llegan en claro y así salen hacia la
 * columna: no se cifran, que es la decisión del proyecto (ver
 * {@code SecurityConfig.passwordEncoder()}).
 */
@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private BitacoraService bitacoraService;

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Optional<Usuario> buscarPorId(Integer id) {
        return usuarioRepository.findById(id);
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    /**
     * Alta o edición (según venga con id).
     *
     * Si el correo ya está en uso por otra cuenta se rechaza aquí con un
     * mensaje claro: la restricción única de la base se vería como un error de
     * SQL crudo.
     *
     * @param usuarioSesion quien hace el cambio, para que quede en la bitácora
     */
    @Transactional
    public Usuario guardar(Usuario usuario, Usuario usuarioSesion) {
        if (usuario.getNombre() == null || usuario.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del usuario es obligatorio");
        }
        if (usuario.getEmail() == null || usuario.getEmail().isBlank()) {
            throw new IllegalArgumentException("El correo es obligatorio");
        }
        if (usuario.getRol() == null) {
            throw new IllegalArgumentException("Hay que indicar el rol del usuario");
        }

        usuario.setNombre(usuario.getNombre().trim());
        usuario.setEmail(usuario.getEmail().trim());

        boolean esAlta = usuario.getId() == null;
        Usuario previo = esAlta ? null
                : usuarioRepository.findById(usuario.getId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Usuario no encontrado: " + usuario.getId()));

        if (esAlta) {
            if (usuario.getPassword() == null || usuario.getPassword().isBlank()) {
                throw new IllegalArgumentException("La contraseña es obligatoria");
            }
        } else if (usuario.getPassword() == null || usuario.getPassword().isBlank()) {
            // Edición sin escribir contraseña: se conserva la que ya tenía.
            // (Pasarla a null borraría la columna y la cuenta quedaría sin login.)
            usuario.setPassword(previo.getPassword());
        }

        if (!esAlta) {
            // El alta no se rehace, y un campo que no viene no se apaga.
            usuario.setFechaRegistro(previo.getFechaRegistro());
            if (usuario.getActivo() == null) {
                usuario.setActivo(previo.getActivo());
            }
        }

        usuarioRepository.findByEmail(usuario.getEmail()).ifPresent(existente -> {
            if (!Objects.equals(existente.getId(), usuario.getId())) {
                throw new IllegalArgumentException(
                        "Ya existe una cuenta con el correo " + usuario.getEmail());
            }
        });

        if (esAlta) {
            usuario.setFechaRegistro(LocalDateTime.now());
            if (usuario.getActivo() == null) {
                usuario.setActivo(true);
            }
        }

        Usuario guardado = usuarioRepository.save(usuario);

        bitacoraService.registrar(
                esAlta ? BitacoraService.CREAR_USUARIO : BitacoraService.MODIFICAR_USUARIO,
                "USUARIOS",
                "Cuenta: " + guardado.getEmail()
                        + " | Nombre: " + guardado.getNombre()
                        + " | Rol: " + guardado.getRol()
                        + (Boolean.TRUE.equals(guardado.getActivo()) ? "" : " | INACTIVA"),
                esAlta ? "Alta de usuario" : "Edición de usuario",
                usuarioSesion);

        return guardado;
    }

    /**
     * Baja lógica: la cuenta deja de poder entrar pero su fila y todo lo que
     * ya registró quedan en pie.
     *
     * @param usuarioSesion quien ordena la baja, para que quede en la bitácora
     */
    @Transactional
    public void baja(Integer id, Usuario usuarioSesion) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));

        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new IllegalArgumentException("La cuenta ya está inactiva: " + usuario.getEmail());
        }
        if (usuarioSesion != null && Objects.equals(usuarioSesion.getId(), usuario.getId())) {
            throw new IllegalArgumentException(
                    "No puedes desactivar la cuenta con la que estás trabajando");
        }

        usuario.setActivo(false);
        usuarioRepository.save(usuario);

        bitacoraService.registrar(
                BitacoraService.BAJA_USUARIO,
                "USUARIOS",
                "Cuenta: " + usuario.getEmail() + " | Rol: " + usuario.getRol(),
                "Baja lógica (los eventos que ya registró se conservan)",
                usuarioSesion);
    }

    /**
     * Vuelve a dejar entrar a una cuenta dada de baja.
     *
     * Es la operación inversa de {@link #baja(Integer, Usuario)} y también
     * queda anotada: reactivar sin dejar rastro permitiría que una cuenta
     * volviera sin que la bitácora dijera quién lo autorizó.
     *
     * @param usuarioSesion quién lo ordena, para que quede en la bitácora
     */
    @Transactional
    public void reactivar(Integer id, Usuario usuarioSesion) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));

        if (Boolean.TRUE.equals(usuario.getActivo())) {
            throw new IllegalArgumentException("La cuenta ya está activa: " + usuario.getEmail());
        }

        usuario.setActivo(true);
        usuarioRepository.save(usuario);

        bitacoraService.registrar(
                BitacoraService.REACTIVAR_USUARIO,
                "USUARIOS",
                "Cuenta: " + usuario.getEmail() + " | Rol: " + usuario.getRol(),
                "La cuenta vuelve a poder entrar",
                usuarioSesion);
    }
}
