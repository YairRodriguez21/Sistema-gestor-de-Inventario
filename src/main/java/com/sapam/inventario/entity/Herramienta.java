package com.sapam.inventario.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "herramientas")
public class Herramienta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_barras", unique = true, nullable = false, length = 50)
    private String codigoBarras; // código único de la etiqueta

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 100)
    private String marca;

    @Column(length = 100)
    private String modelo;

    @Column(length = 50)
    private String numeroSerie; // Si aplica

    @Column(columnDefinition = "varchar(20) default 'NUEVO'")
    private String estadoFisico; // NUEVO, BUENO, REGULAR, MALO, REPARADO

    /**
     * Baja lógica, igual que en productos: no se borra la fila para no perder
     * la trazabilidad de sus préstamos y de la bitácora.
     */
    @Column(nullable = false, length = 20)
    private String estado = "ACTIVO"; // ACTIVO, BAJAS

    @Column(length = 50)
    private String areaResponsable; // Área que cuida la herramienta

    @Column(name = "fecha_estimada_devolucion")
    private LocalDate fechaEstimadaDevolucion;

    @Column(columnDefinition = "text")
    private String observaciones;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;

    /**
     * Quién dio de alta la herramienta.
     *
     * El porqué de cada alta ya está en la bitácora, que no se puede borrar;
     * esto deja el dato también en la propia ficha, que es donde se mira al
     * revisar quién metió una herramienta al almacén. Las altas anteriores a
     * este campo quedan vacías (no se puede saber a posteriori) y así se
     * muestran.
     */
    @ManyToOne
    @JoinColumn(name = "usuario_registro")
    private Usuario usuarioRegistro;

    // Getters, setters, constructors

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigoBarras() {
        return codigoBarras;
    }

    public void setCodigoBarras(String codigoBarras) {
        this.codigoBarras = codigoBarras;
    }

    /**
     * El texto que lleva impresa la etiqueta y el que escribe el lector en la
     * caja de búsqueda: el código HER corto que asigna el sistema.
     *
     * No se usa el número de serie aunque esté en la etiqueta a la vista: es
     * opcional y se repite (hay dos herramientas con serie "SN"), con lo que el
     * lector no sabría cuál de las dos es.
     */
    public String getCodigoEscaneable() {
        return codigoBarras;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
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

    public String getNumeroSerie() {
        return numeroSerie;
    }

    public void setNumeroSerie(String numeroSerie) {
        this.numeroSerie = numeroSerie;
    }

    public String getEstadoFisico() {
        return estadoFisico;
    }

    public void setEstadoFisico(String estadoFisico) {
        this.estadoFisico = estadoFisico;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getAreaResponsable() {
        return areaResponsable;
    }

    public void setAreaResponsable(String areaResponsable) {
        this.areaResponsable = areaResponsable;
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

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public Usuario getUsuarioRegistro() {
        return usuarioRegistro;
    }

    public void setUsuarioRegistro(Usuario usuarioRegistro) {
        this.usuarioRegistro = usuarioRegistro;
    }
}