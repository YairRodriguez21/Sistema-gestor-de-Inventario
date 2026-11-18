package com.sapam.inventario.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    private Boolean activo = true;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;

    /**
     * Roles del sistema.
     *
     * El nombre en minúscula es el que usan la aplicación de escritorio y la
     * columna {@code usuarios.rol}; {@link #rolSpring()} da el nombre que pide
     * Spring Security (ROLE_ADMIN / ROLE_OPERATIVO / ROLE_CONSULTA).
     */
    public enum Rol {
        admin, usuario, consulta;

        public String rolSpring() {
            return switch (this) {
                case admin -> "ADMIN";
                case usuario -> "OPERATIVO";
                case consulta -> "CONSULTA";
            };
        }

        /**
         * Cómo se le llama en pantalla, en vez del valor de la columna.
         * Lo usan la cabecera de la ventana principal y la tabla de cuentas,
         * para que un rol no se muestre de dos maneras distintas.
         */
        public String descripcion() {
            return switch (this) {
                case admin -> "Administrador";
                case usuario -> "Usuario operativo";
                case consulta -> "Consulta";
            };
        }
    }

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    /**
     * Nombre para mostrar en reportes.
     * {@code Usuario} guarda un solo campo {@code nombre} (no hay apellidos),
     * así que es simplemente ese valor.
     */
    public String getNombreCompleto() {
        return nombre;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    /**
     * Nunca sale por HTTP. Los datos siguen en la base y sirven para el login,
     * pero si algún endpoint devuelve un {@code Usuario} (ahora mismo ninguno),
     * la contraseña de texto plano no iría en el JSON.
     */
    @JsonIgnore
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    /**
     * Nombre para los desplegables de la aplicación (quién recibe una
     * herramienta, a quién se le entrega material): sin esto el combo mostraría
     * la dirección de la memoria del objeto.
     */
    @Override
    public String toString() {
        return nombre + (email == null ? "" : "  (" + email + ")");
    }

    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
}
