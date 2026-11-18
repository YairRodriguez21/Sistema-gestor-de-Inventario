package com.sapam.inventario.controller;

import com.sapam.inventario.entity.Kardex;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Línea de kardex tal y como se sirve por HTTP.
 *
 * Se expone como DTO para no arrastrar por la API los datos del producto y del
 * usuario que la entidad anida; la línea es la trazabilidad en sí.
 */
public class KardexLineaDTO {

    private final LocalDate fecha;
    private final LocalTime hora;
    private final Integer productoId;
    private final String producto;
    private final String tipoMovimiento;
    private final String folio;
    private final Integer cantidadMovida;
    private final Integer existenciaAnterior;
    private final Integer existenciaPosterior;
    private final String responsable;
    private final String observaciones;

    public KardexLineaDTO(Kardex linea) {
        this.fecha = linea.getFecha();
        this.hora = linea.getHora();
        this.productoId = linea.getProducto() == null ? null : linea.getProducto().getId();
        this.producto = linea.getProducto() == null ? "" : linea.getProducto().getNombre();
        this.tipoMovimiento = linea.getTipoMovimiento();
        this.folio = linea.getFolio();
        this.cantidadMovida = linea.getCantidadMovida();
        this.existenciaAnterior = linea.getExistenciaAnterior();
        this.existenciaPosterior = linea.getExistenciaPosterior();
        this.responsable = linea.getUsuarioResponsable() == null ? ""
                : linea.getUsuarioResponsable().getNombre();
        this.observaciones = linea.getObservaciones();
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public Integer getProductoId() {
        return productoId;
    }

    public String getProducto() {
        return producto;
    }

    public String getTipoMovimiento() {
        return tipoMovimiento;
    }

    public String getFolio() {
        return folio;
    }

    public Integer getCantidadMovida() {
        return cantidadMovida;
    }

    public Integer getExistenciaAnterior() {
        return existenciaAnterior;
    }

    public Integer getExistenciaPosterior() {
        return existenciaPosterior;
    }

    public String getResponsable() {
        return responsable;
    }

    public String getObservaciones() {
        return observaciones;
    }
}
