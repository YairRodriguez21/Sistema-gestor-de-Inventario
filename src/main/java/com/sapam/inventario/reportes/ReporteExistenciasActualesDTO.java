package com.sapam.inventario.reportes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ReporteExistenciasActualesDTO {

    private Long productoId;
    private String codigoBarras;
    private String nombre;
    private String seccion;
    private String marca;
    private String modelo;
    private String unidadMedida;
    private Integer existenciaActual;
    private Integer existenciaMinima;
    private Integer existenciaMaxima;
    private String ubicacionFisica;
    private String estado;
    private String codigoInterno; // código interno, para abrir la ficha del producto

    // Getters, setters, constructors

    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getSeccion() { return seccion; }
    public void setSeccion(String seccion) { this.seccion = seccion; }

    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }

    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public Integer getExistenciaActual() { return existenciaActual; }
    public void setExistenciaActual(Integer existenciaActual) { this.existenciaActual = existenciaActual; }

    public Integer getExistenciaMinima() { return existenciaMinima; }
    public void setExistenciaMinima(Integer existenciaMinima) { this.existenciaMinima = existenciaMinima; }

    public Integer getExistenciaMaxima() { return existenciaMaxima; }
    public void setExistenciaMaxima(Integer existenciaMaxima) { this.existenciaMaxima = existenciaMaxima; }

    public String getUbicacionFisica() { return ubicacionFisica; }
    public void setUbicacionFisica(String ubicacionFisica) { this.ubicacionFisica = ubicacionFisica; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getCodigoInterno() { return codigoInterno; }
    public void setCodigoInterno(String codigoInterno) { this.codigoInterno = codigoInterno; }
}