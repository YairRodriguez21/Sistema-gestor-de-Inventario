package com.sapam.inventario.reportes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ReporteHistorialMovimientosDTO {

    private LocalDate fechaMovimiento;
    private String tipoMovimiento; // ENTRADA, SALIDA, AJUSTE, PRESTAMO, DEVOLUCION
    private String folio;
    private String nombreProducto;
    private String codigoBarras;
    private Integer cantidadMovida;
    private Integer existenciaAnterior;
    private Integer existenciaPosterior;
    private String nombreUsuario;
    private String observaciones;
    private String codigoInterno; // código interno del producto del movimiento

    // Getters, setters, constructors

    public LocalDate getFechaMovimiento() { return fechaMovimiento; }
    public void setFechaMovimiento(LocalDate fechaMovimiento) { this.fechaMovimiento = fechaMovimiento; }

    public String getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(String tipoMovimiento) { this.tipoMovimiento = tipoMovimiento; }

    public String getFolio() { return folio; }
    public void setFolio(String folio) { this.folio = folio; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }

    public Integer getCantidadMovida() { return cantidadMovida; }
    public void setCantidadMovida(Integer cantidadMovida) { this.cantidadMovida = cantidadMovida; }

    public Integer getExistenciaAnterior() { return existenciaAnterior; }
    public void setExistenciaAnterior(Integer existenciaAnterior) { this.existenciaAnterior = existenciaAnterior; }

    public Integer getExistenciaPosterior() { return existenciaPosterior; }
    public void setExistenciaPosterior(Integer existenciaPosterior) { this.existenciaPosterior = existenciaPosterior; }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public String getCodigoInterno() { return codigoInterno; }
    public void setCodigoInterno(String codigoInterno) { this.codigoInterno = codigoInterno; }
}