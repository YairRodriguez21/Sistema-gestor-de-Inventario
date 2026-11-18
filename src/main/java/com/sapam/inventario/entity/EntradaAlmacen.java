package com.sapam.inventario.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

@Entity
@Table(name = "entradas_almacen")
public class EntradaAlmacen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String folio; // Folio único por entrada

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    @ManyToOne
    @JoinColumn(name = "usuario_registro_id", nullable = false)
    private Usuario usuarioRegistro; // Usuario que registra la entrada

    @Column(columnDefinition = "text")
    private String observaciones;

    @OneToMany(mappedBy = "entradaAlmacen", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductoEntrada> productosIngresados; // Productos con cantidades

    // Getters, setters, constructors

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFolio() {
        return folio;
    }

    public void setFolio(String folio) {
        this.folio = folio;
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

    public Usuario getUsuarioRegistro() {
        return usuarioRegistro;
    }

    public void setUsuarioRegistro(Usuario usuarioRegistro) {
        this.usuarioRegistro = usuarioRegistro;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public Set<ProductoEntrada> getProductosIngresados() {
        return productosIngresados;
    }

    public void setProductosIngresados(Set<ProductoEntrada> productosIngresados) {
        this.productosIngresados = productosIngresados;
    }
}