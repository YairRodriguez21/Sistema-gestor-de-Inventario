package com.sapam.inventario.reportes;

import java.time.LocalDate;
import java.util.List;

public class ReporteProductosAgotadosDTO {

    private Long productoId;
    private String codigoBarras;
    private String nombre;
    private String seccion;
    private String marca;
 private Integer existenciaActual;
    private String motivo; // "AGOTADO", "STOCK_MINIMO", etc.
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

    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }

    public Integer getExistenciaActual() { return existenciaActual; }
    public void setExistenciaActual(Integer existenciaActual) { this.existenciaActual = existenciaActual; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getCodigoInterno() { return codigoInterno; }
    public void setCodigoInterno(String codigoInterno) { this.codigoInterno = codigoInterno; }
}