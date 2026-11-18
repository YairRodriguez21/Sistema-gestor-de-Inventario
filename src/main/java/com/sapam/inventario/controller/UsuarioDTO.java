package com.sapam.inventario.controller;

import com.sapam.inventario.entity.Usuario;

import java.time.LocalDateTime;

/**
 * Cuenta de usuario tal y como se sirve por HTTP.
 *
 * Existe sobre todo por lo que <em>no</em> lleva: la contraseña. Devolver la
 * entidad entera filtraría el hash de todos los usuarios en cada petición, y
 * hoy la base guarda ese hash en texto plano.
 */
public class UsuarioDTO {

    private final Integer id;
    private final String nombre;
    private final String email;
    private final String rol;
    private final Boolean activo;
    private final LocalDateTime fechaRegistro;

    public UsuarioDTO(Usuario usuario) {
        this.id = usuario.getId();
        this.nombre = usuario.getNombre();
        this.email = usuario.getEmail();
        this.rol = usuario.getRol() == null ? null : usuario.getRol().name();
        this.activo = usuario.getActivo();
        this.fechaRegistro = usuario.getFechaRegistro();
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getEmail() { return email; }
    public String getRol() { return rol; }
    public Boolean getActivo() { return activo; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
