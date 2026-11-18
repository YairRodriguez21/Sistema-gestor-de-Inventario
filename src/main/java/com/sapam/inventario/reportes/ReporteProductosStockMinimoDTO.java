package com.sapam.inventario.reportes;

import java.time.LocalDate;
import java.util.List;

public class ReporteProductosStockMinimoDTO {

    private Long productoId;
    private String codigoBarras;
    private String nombre;
    private String seccion;
    private Integer existenciaActual;
    private Integer existenciaMinima;
    private Double porcentajeStock; // (existenciaActual / existenciaMinima) * 100
    private String codigoInterno; // código interno, para pedir el reabastecimiento

    // Getters, setters, constructors

    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getSeccion() { return seccion; }
    public void setSeccion(String seccion) { this.seccion = seccion; }

    public Integer getExistenciaActual() { return existenciaActual; }
    public void setExistenciaActual(Integer existenciaActual) { this.existenciaActual = existenciaActual; }

    public Integer getExistenciaMinima() { return existenciaMinima; }
    public void setExistenciaMinima(Integer existenciaMinima) { this.existenciaMinima = existenciaMinima; }

    public Double getPorcentajeStock() { return porcentajeStock; }
    public void setPorcentajeStock(Double porcentajeStock) { this.porcentajeStock = porcentajeStock; }

    public String getCodigoInterno() { return codigoInterno; }
    public void setCodigoInterno(String codigoInterno) { this.codigoInterno = codigoInterno; }
}