package com.sapam.inventario.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "kardex_productos")
public class Kardex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    @Column(nullable = false, length = 20)
    private String tipoMovimiento; // ENTRADA, SALIDA, AJUSTE, PRESTAMO, DEVOLUCION

    @Column(nullable = false, length = 20)
    private String folio; // Folio asociado (entrada/salida/préstamo)

    @Column(nullable = false)
    private Integer cantidadMovida; // Cantidad movida (positiva para entrada, negativa para salida)

    @Column(nullable = false)
    private Integer existenciaAnterior; // Existencias antes del movimiento

    @Column(nullable = false)
    private Integer existenciaPosterior; // Existencias después del movimiento

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuarioResponsable; // Usuario que realizó el movimiento

    @Column(columnDefinition = "text")
    private String observaciones;

    // Getters, setters, constructors

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Producto getProducto() {
        return producto;
    }

    public void setProducto(Producto producto) {
        this.producto = producto;
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

    public String getTipoMovimiento() {
        return tipoMovimiento;
    }

    public void setTipoMovimiento(String tipoMovimiento) {
        this.tipoMovimiento = tipoMovimiento;
    }

    public String getFolio() {
        return folio;
    }

    public void setFolio(String folio) {
        this.folio = folio;
    }

    public Integer getCantidadMovida() {
        return cantidadMovida;
    }

    public void setCantidadMovida(Integer cantidadMovida) {
        this.cantidadMovida = cantidadMovida;
    }

    public Integer getExistenciaAnterior() {
        return existenciaAnterior;
    }

    public void setExistenciaAnterior(Integer existenciaAnterior) {
        this.existenciaAnterior = existenciaAnterior;
    }

    public Integer getExistenciaPosterior() {
        return existenciaPosterior;
    }

    public void setExistenciaPosterior(Integer existenciaPosterior) {
        this.existenciaPosterior = existenciaPosterior;
    }

    public Usuario getUsuarioResponsable() {
        return usuarioResponsable;
    }

    public void setUsuarioResponsable(Usuario usuarioResponsable) {
        this.usuarioResponsable = usuarioResponsable;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}