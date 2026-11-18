package com.sapam.inventario.controller;

import com.sapam.inventario.entity.AuditoriaSistema;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Fila de bitácora tal y como se sirve por HTTP.
 *
 * Se devuelve un DTO y no la entidad entera para no exponer por la API el resto
 * de campos del usuario (rol, fecha de alta) que a la bitácora no le importan.
 * El nombre del usuario sí: el reporte de "quién lo hizo" es justo lo que
 * interesa.
 */
public class BitacoraEventoDTO {

    private final LocalDate fecha;
    private final LocalTime hora;
    private final String usuario;
    private final String accion;
    private final String modulo;
    private final String detalle;
    private final String observaciones;
    private final String codigoVerificacion;

    public BitacoraEventoDTO(AuditoriaSistema evento) {
        this.fecha = evento.getFecha();
        this.hora = evento.getHora();
        this.usuario = evento.getUsuario() == null ? "Sistema" : evento.getUsuario().getNombre();
        this.accion = evento.getAccion();
        this.modulo = evento.getModuloAfectado();
        this.detalle = evento.getInformacionModificada();
        this.observaciones = evento.getObservaciones();
        this.codigoVerificacion = evento.getCodigoVerificacion();
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getAccion() {
        return accion;
    }

    public String getModulo() {
        return modulo;
    }

    public String getDetalle() {
        return detalle;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public String getCodigoVerificacion() {
        return codigoVerificacion;
    }
}
