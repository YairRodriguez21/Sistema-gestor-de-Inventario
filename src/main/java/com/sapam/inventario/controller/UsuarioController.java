package com.sapam.inventario.controller;

import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.service.SesionService;
import com.sapam.inventario.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/**
 * Cuentas de usuario por HTTP.
 *
 * Todas las respuestas pasan por {@link UsuarioDTO}: la entidad entera
 * arrastraría la contraseña, que la base guarda en texto plano. Y el borrado
 * es una baja lógica —la cuenta queda inactiva—, porque borrarla destruiría la
 * trazabilidad de todo lo que ya registró.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SesionService sesionService;

    @GetMapping
    public List<UsuarioDTO> listar() {
        return usuarioService.listarTodos().stream().map(UsuarioDTO::new).toList();
    }

    @GetMapping("/{id}")
    public UsuarioDTO porId(@PathVariable Integer id) {
        return usuarioService.buscarPorId(id)
                .map(UsuarioDTO::new)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Usuario no encontrado: " + id));
    }

    @GetMapping("/email/{email}")
    public UsuarioDTO porEmail(@PathVariable String email) {
        return usuarioService.buscarPorEmail(email)
                .map(UsuarioDTO::new)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Usuario no encontrado: " + email));
    }

    /** Alta (sin id) o edición (con id). */
    @PostMapping
    public UsuarioDTO guardar(@RequestBody SolicitudUsuarioDTO solicitud) {
        Usuario usuario = new Usuario();
        usuario.setId(solicitud.getId());
        usuario.setNombre(solicitud.getNombre());
        usuario.setEmail(solicitud.getEmail());
        usuario.setPassword(solicitud.getPassword());
        usuario.setActivo(solicitud.getActivo());
        usuario.setRol(aRol(solicitud.getRol()));

        try {
            return new UsuarioDTO(usuarioService.guardar(usuario, sesionService.actual()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** Baja lógica: la cuenta deja de entrar pero sus registros siguen en pie. */
    @DeleteMapping("/{id}")
    public void baja(@PathVariable Integer id) {
        try {
            usuarioService.baja(id, sesionService.actual());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Acepta el nombre de la columna ({@code admin}) o el que da Spring
     * Security ({@code ADMIN} / {@code OPERATIVO} / {@code CONSULTA}), porque
     * los dos nombres andan por ahí según de dónde venga la petición.
     */
    private static Usuario.Rol aRol(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return switch (valor.trim().toUpperCase(Locale.ROOT)) {
            case "ADMIN", "ADMINISTRADOR" -> Usuario.Rol.admin;
            case "OPERATIVO", "USUARIO" -> Usuario.Rol.usuario;
            case "CONSULTA" -> Usuario.Rol.consulta;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rol no válido: " + valor + ". Use uno de admin, operativo o consulta");
        };
    }

    /**
     * Cuerpo de alta o edición. El rol llega como texto para poder escribir
     * tanto {@code admin} como {@code ADMIN}; la contraseña no se devuelve
     * nunca, porque los DTO la dejan fuera.
     */
    public static class SolicitudUsuarioDTO {
        private Integer id;
        private String nombre;
        private String email;
        private String password;
        private String rol;
        private Boolean activo;

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getRol() { return rol; }
        public void setRol(String rol) { this.rol = rol; }
        public Boolean getActivo() { return activo; }
        public void setActivo(Boolean activo) { this.activo = activo; }
    }
}
