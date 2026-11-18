package com.sapam.inventario.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Un evento de la bitácora.
 *
 * El índice sobre {@code (fecha, hora)} es el que sostiene la lectura de los
 * últimos eventos: sin él, cada vez que se abre la pantalla de bitácora MySQL
 * tiene que leer la tabla entera y ordenarla en memoria para quedarse con los
 * 200 más recientes —con 122 filas no se nota, con las que acumulan diez años
 * sí—. Con el índice la tabla ya viene ordenada por esos dos campos, así que
 * se salta directo a los más nuevos, que es justo lo que pide la consulta.
 */
@Entity
@Table(name = "auditoria_sistema", indexes = {
        @Index(name = "idx_auditoria_fecha_hora", columnList = "fecha, hora")
})
public class AuditoriaSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario; // Usuario que realizó la acción (null para system)

    @Column(nullable = false, length = 50)
    private String accion; // LOGIN, LOGOUT, CREAR_PRODUCTO, MODIFICAR_PRODUCTO, ELIMINAR_PRODUCTO, ENTRADA_ALMACEN, SALIDA_ALMACEN, PRESTAMO_HERRAMIENTA, DEVOLUCION_HERRAMIENTA, GENERAR_REPORTE, etc.

    @Column(nullable = false, length = 50)
    private String moduloAfectado; // INVENTARIO, ENTRADAS, SALIDAS, HERRAMIENTAS, REPORTES, SEGURIDAD

    @Column(columnDefinition = "text")
    private String informacionModificada; // Qué cambió (descripción o JSON)

    @Column(columnDefinition = "text")
    private String observaciones;

    @Column(unique = true, nullable = false, length = 50)
    private String codigoVerificacion; // código de auditoría para verificar autenticidad en la bitácora

    // Getters, setters, constructors

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public void setHora(LocalTime hora) {
        this.hora = hora;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public String getAccion() {
        return accion;
    }

    public void setAccion(String accion) {
        this.accion = accion;
    }

    public String getModuloAfectado() {
        return moduloAfectado;
    }

    public void setModuloAfectado(String moduloAfectado) {
        this.moduloAfectado = moduloAfectado;
    }

    public String getInformacionModificada() {
        return informacionModificada;
    }

    public void setInformacionModificada(String informacionModificada) {
        this.informacionModificada = informacionModificada;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getCodigoVerificacion() {
        return codigoVerificacion;
    }

    public void setCodigoVerificacion(String codigoVerificacion) {
        this.codigoVerificacion = codigoVerificacion;
    }
}