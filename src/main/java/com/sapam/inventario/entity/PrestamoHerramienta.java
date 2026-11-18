package com.sapam.inventario.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "prestamos_herramientas")
public class PrestamoHerramienta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "herramienta_id", nullable = false)
    private Herramienta herramienta;

    /**
     * Quién se la lleva: texto libre, igual que quien recibe una salida
     * ({@code salidas_almacen.persona_recibe}). No apunta a la tabla de
     * usuarios porque el trabajador que recibe la herramienta casi nunca tiene
     * cuenta en el sistema.
     */
    @Column(name = "persona_recibe", nullable = false, length = 100)
    private String personaRecibe;

    @Column(nullable = false)
    private LocalDateTime fechaEntrega;

    @Column(length = 100)
    private String personaEntrega; // Quien entrega (almacenista responsable)

    @Column(length = 50)
    private String areaResponsable;

    /**
     * Para dónde sale la herramienta: obra, planta, laboratorio o el lugar
     * donde se va a usar. Es texto libre, igual que quien recibe y el área.
     * Va impreso en el ticket de préstamo.
     */
    @Column(length = 150)
    private String destino;

    @Column(name = "fecha_estimada_devolucion", nullable = false)
    private LocalDate fechaEstimadaDevolucion;

    @Column(columnDefinition = "text")
    private String observaciones;

    @Column(name = "fecha_devolucion")
    private LocalDate fechaDevolucion;

    @Column(columnDefinition = "varchar(20) default 'PENDIENTE'")
    private String estado; // PENDIENTE, DEVUELTO

    @Column(columnDefinition = "varchar(20) default 'ACTIVO'")
    private String herramientaEstado; // Estado al momento de la devolución

    /**
     * Folio del préstamo, como el de una entrada ({@code ENT-}) o de una salida
     * ({@code SAL-}): es el código que se anota en el reporte de "herramientas
     * prestadas", el que se busca para saber con quién está la herramienta y
     * el que va impreso como código de barras en el ticket de préstamo.
     * Lo pone el sistema, igual que los demás folios.
     *
     * Sin {@code nullable = false}: la columna se añade sobre una tabla que ya
     * tiene filas, y MySQL no admite añadirla NOT NULL sin valor por defecto.
     * Los préstamos hechos antes se rellenan al arrancar en
     * {@code MigracionPrestamos}.
     */
    @Column(name = "folio", unique = true, length = 50)
    private String folio;

    /**
     * Estado físico al momento de la entrega, congelado aquí.
     *
     * El de la devolución vive en {@code herramientaEstado}. Si los dos
     * fueran el mismo campo, al cerrar el préstamo se sobrescribiría el de la
     * entrega y quedaría sin constancia de cómo salió la herramienta, que es
     * justo lo que hace falta para poder comparar cómo volvió.
     */
    @Column(name = "estado_entrega", length = 30)
    private String estadoEntrega;

    /** La cuenta con la que se cerró la devolución. */
    @ManyToOne
    @JoinColumn(name = "usuario_devolucion")
    private Usuario usuarioDevolucion;

    // Getters, setters, constructors

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Herramienta getHerramienta() {
        return herramienta;
    }

    public void setHerramienta(Herramienta herramienta) {
        this.herramienta = herramienta;
    }

    public String getPersonaRecibe() {
        return personaRecibe;
    }

    public void setPersonaRecibe(String personaRecibe) {
        this.personaRecibe = personaRecibe;
    }

    public LocalDateTime getFechaEntrega() {
        return fechaEntrega;
    }

    public void setFechaEntrega(LocalDateTime fechaEntrega) {
        this.fechaEntrega = fechaEntrega;
    }

    public String getPersonaEntrega() {
        return personaEntrega;
    }

    public void setPersonaEntrega(String personaEntrega) {
        this.personaEntrega = personaEntrega;
    }

    public String getAreaResponsable() {
        return areaResponsable;
    }

    public void setAreaResponsable(String areaResponsable) {
        this.areaResponsable = areaResponsable;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public LocalDate getFechaEstimadaDevolucion() {
        return fechaEstimadaDevolucion;
    }

    public void setFechaEstimadaDevolucion(LocalDate fechaEstimadaDevolucion) {
        this.fechaEstimadaDevolucion = fechaEstimadaDevolucion;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public LocalDate getFechaDevolucion() {
        return fechaDevolucion;
    }

    public void setFechaDevolucion(LocalDate fechaDevolucion) {
        this.fechaDevolucion = fechaDevolucion;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getHerramientaEstado() {
        return herramientaEstado;
    }

    public void setHerramientaEstado(String herramientaEstado) {
        this.herramientaEstado = herramientaEstado;
    }

    public String getFolio() {
        return folio;
    }

    public void setFolio(String folio) {
        this.folio = folio;
    }

    public String getEstadoEntrega() {
        return estadoEntrega;
    }

    public void setEstadoEntrega(String estadoEntrega) {
        this.estadoEntrega = estadoEntrega;
    }

    public Usuario getUsuarioDevolucion() {
        return usuarioDevolucion;
    }

    public void setUsuarioDevolucion(Usuario usuarioDevolucion) {
        this.usuarioDevolucion = usuarioDevolucion;
    }
}