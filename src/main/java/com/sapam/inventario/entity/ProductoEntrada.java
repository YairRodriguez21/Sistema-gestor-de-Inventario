package com.sapam.inventario.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "productos_entrada")
public class ProductoEntrada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entrada_almacen_id", nullable = false)
    private EntradaAlmacen entradaAlmacen; // Dueño de la relación (mappedBy)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(length = 500)
    private String observaciones;

    // Getters, setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EntradaAlmacen getEntradaAlmacen() {
        return entradaAlmacen;
    }

    public void setEntradaAlmacen(EntradaAlmacen entradaAlmacen) {
        this.entradaAlmacen = entradaAlmacen;
    }

    public Producto getProducto() {
        return producto;
    }

    public void setProducto(Producto producto) {
        this.producto = producto;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
