package com.sapam.inventario.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "codigo_barras", nullable = false, unique = true, length = 50)
    private String codigoBarras;

    @Column(name = "codigo_interno", unique = true, nullable = false, length = 50)
    private String codigoInterno; // identificador interno único, reserva de etiqueta

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    /**
     * Sección del inventario: una de las cinco que ofrece la pantalla
     * (Papelería, Construcción, Vehículos, Uniformes y Ferretería). Es el
     * único criterio de orden grueso: la categoría que existía antes se borró
     * por duplicarla.
     *
     * Se permite vacío para que un producto llegado por la API —o creado
     * antes de que existieran las secciones— siga apareciendo en el inventario
     * completo; lo que no aparece es en la sección que no le corresponde.
     */
    @Column(length = 20)
    private String seccion;

    @Column(length = 100)
    private String marca;

    @Column(length = 100)
    private String modelo;

    @Column(nullable = false, length = 20)
    private String unidadMedida = "pieza";

    @Column(nullable = false)
    private Integer existenciaActual = 0;

    @Column(nullable = false)
    private Integer existenciaMinima = 5;

    @Column(nullable = false)
    private Integer existenciaMaxima = 1000;

    @Column(length = 50)
    private String ubicacionFisica; // Estante A-1, bahía 2, etc.

    @Column(length = 500)
    private String observaciones;

    @Column(name = "estado", columnDefinition = "varchar(20) default 'ACTIVO'")
    private String estado; // ACTIVO, INACTIVO, MANTENIMIENTO, BAJAS

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "ultima_actualizacion")
    private LocalDateTime ultimaActualizacion;

    // Sin esto, fecha_creacion/ultima_actualizacion/estado se guardaban en NULL
    @PrePersist
    private void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        if (fechaCreacion == null) {
            fechaCreacion = ahora;
        }
        ultimaActualizacion = ahora;
        if (estado == null || estado.isBlank()) {
            estado = "ACTIVO";
        }
        if (codigoInterno == null || codigoInterno.isBlank()) {
            codigoInterno = "INT-" + UUID.randomUUID();
        }
        if (unidadMedida == null || unidadMedida.isBlank()) {
            unidadMedida = "pieza";
        }
        if (existenciaActual == null) existenciaActual = 0;
        if (existenciaMinima == null) existenciaMinima = 5;
        if (existenciaMaxima == null) existenciaMaxima = 1000;
    }

    @PreUpdate
    private void alActualizar() {
        ultimaActualizacion = LocalDateTime.now();
        if (estado == null || estado.isBlank()) {
            estado = "ACTIVO";
        }
    }

    // Getters, setters, constructors

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCodigoBarras() {
        return codigoBarras;
    }

    public void setCodigoBarras(String codigoBarras) {
        this.codigoBarras = codigoBarras;
    }

    public String getCodigoInterno() {
        return codigoInterno;
    }

    public void setCodigoInterno(String codigoInterno) {
        this.codigoInterno = codigoInterno;
    }

    /**
     * El texto que lleva impresa la etiqueta y el que escribe el lector en la
     * caja de búsqueda: el código de barras del producto. El código interno
     * queda como reserva para el caso improbable de un producto sin código.
     */
    public String getCodigoEscaneable() {
        if (codigoBarras != null && !codigoBarras.isBlank()) {
            return codigoBarras;
        }
        return codigoInterno;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getSeccion() {
        return seccion;
    }

    public void setSeccion(String seccion) {
        this.seccion = seccion;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public void setUnidadMedida(String unidadMedida) {
        this.unidadMedida = unidadMedida;
    }

    public Integer getExistenciaActual() {
        return existenciaActual;
    }

    public void setExistenciaActual(Integer existenciaActual) {
        this.existenciaActual = existenciaActual;
    }

    public Integer getExistenciaMinima() {
        return existenciaMinima;
    }

    public void setExistenciaMinima(Integer existenciaMinima) {
        this.existenciaMinima = existenciaMinima;
    }

    public Integer getExistenciaMaxima() {
        return existenciaMaxima;
    }

    public void setExistenciaMaxima(Integer existenciaMaxima) {
        this.existenciaMaxima = existenciaMaxima;
    }

    public String getUbicacionFisica() {
        return ubicacionFisica;
    }

    public void setUbicacionFisica(String ubicacionFisica) {
        this.ubicacionFisica = ubicacionFisica;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getUltimaActualizacion() {
        return ultimaActualizacion;
    }

    public void setUltimaActualizacion(LocalDateTime ultimaActualizacion) {
        this.ultimaActualizacion = ultimaActualizacion;
    }
}