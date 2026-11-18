package com.sapam.inventario.reportes;

import java.time.LocalDate;
import java.util.List;

public class ReporteMovimientosUsuarioDTO {

    private String nombreUsuario;
    private String emailUsuario;
    private Long totalMovimientos;
    private LocalDate fechaPrimerMovimiento;
    private LocalDate fechaUltimoMovimiento;
    private List<ReporteMovimientoUsuarioResumenDTO> movimientos; // resumen por tipo de movimiento

    // Getters, setters, constructors

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }

    public String getEmailUsuario() { return emailUsuario; }
    public void setEmailUsuario(String emailUsuario) { this.emailUsuario = emailUsuario; }

    public Long getTotalMovimientos() { return totalMovimientos; }
    public void setTotalMovimientos(Long totalMovimientos) { this.totalMovimientos = totalMovimientos; }

    public LocalDate getFechaPrimerMovimiento() { return fechaPrimerMovimiento; }
    public void setFechaPrimerMovimiento(LocalDate fechaPrimerMovimiento) { this.fechaPrimerMovimiento = fechaPrimerMovimiento; }

    public LocalDate getFechaUltimoMovimiento() { return fechaUltimoMovimiento; }
    public void setFechaUltimoMovimiento(LocalDate fechaUltimoMovimiento) { this.fechaUltimoMovimiento = fechaUltimoMovimiento; }

    public List<ReporteMovimientoUsuarioResumenDTO> getMovimientos() { return movimientos; }
    public void setMovimientos(List<ReporteMovimientoUsuarioResumenDTO> movimientos) { this.movimientos = movimientos; }
}

class ReporteMovimientoUsuarioResumenDTO {

    private String tipoMovimiento; // ENTRADA, SALIDA, PRESTAMO, DEVOLUCION, etc.
    private Long cantidad;
    private LocalDate fecha;
    private String modulo; // INVENTARIO, ENTRADAS, SALIDAS, HERRAMIENTAS

    // Getters, setters, constructors

    public String getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(String tipoMovimiento) { this.tipoMovimiento = tipoMovimiento; }

    public Long getCantidad() { return cantidad; }
    public void setCantidad(Long cantidad) { this.cantidad = cantidad; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public String getModulo() { return modulo; }
    public void setModulo(String modulo) { this.modulo = modulo; }
}