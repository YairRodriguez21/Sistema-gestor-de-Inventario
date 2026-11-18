package com.sapam.inventario.controller;

import com.sapam.inventario.entity.SalidaAlmacen;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * Salida de almacén tal y como se sirve por HTTP.
 *
 * Como en la entrada, se expone como DTO para no arrastrar por la API al
 * usuario que la registró (lleva la contraseña). El campo {@code responsable}
 * es la cuenta con la que se abrió la sesión: no se puede elegir ni negociar.
 */
public class SalidaAlmacenDTO {

    /** Una línea de la salida: qué producto salió y en qué cantidad. */
    public record Linea(Integer productoId, String producto, Integer cantidad,
                        String observaciones) {}

    private static final Comparator<Linea> ORDEN =
            Comparator.comparing(Linea::productoId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final Long id;
    private final String folio;
    private final LocalDate fecha;
    private final LocalTime hora;
    private final String motivoSalida;
    private final String personaSolicita;
    private final String puestoSolicita;
    private final String departamentoSolicita;
    private final String personaRecibe;
    private final String puestoRecibe;
    private final String departamentoRecibe;
    private final String responsable;
    private final List<Linea> productos;
    private final String observaciones;

    public SalidaAlmacenDTO(SalidaAlmacen salida) {
        this.id = salida.getId();
        this.folio = salida.getFolio();
        this.fecha = salida.getFecha();
        this.hora = salida.getHora();
        this.motivoSalida = salida.getMotivoSalida();
        this.personaSolicita = salida.getPersonaSolicita();
        this.puestoSolicita = salida.getPuestoSolicita();
        this.departamentoSolicita = salida.getDepartamentoSolicita();
        this.personaRecibe = salida.getPersonaRecibe();
        this.puestoRecibe = salida.getPuestoRecibe();
        this.departamentoRecibe = salida.getDepartamentoRecibe();
        this.responsable = salida.getUsuarioRegistro() == null
                ? "" : salida.getUsuarioRegistro().getNombreCompleto();
        this.observaciones = salida.getObservaciones();

        this.productos = salida.getProductosEntregados() == null
                ? List.of()
                : salida.getProductosEntregados().stream()
                        .map(p -> new Linea(
                                p.getProducto() == null ? null : p.getProducto().getId(),
                                p.getProducto() == null ? "" : p.getProducto().getNombre(),
                                p.getCantidad(),
                                p.getObservaciones()))
                        .sorted(ORDEN)
                        .toList();
    }

    public Long getId() { return id; }
    public String getFolio() { return folio; }
    public LocalDate getFecha() { return fecha; }
    public LocalTime getHora() { return hora; }
    public String getMotivoSalida() { return motivoSalida; }
    public String getPersonaSolicita() { return personaSolicita; }
    public String getPuestoSolicita() { return puestoSolicita; }
    public String getDepartamentoSolicita() { return departamentoSolicita; }
    public String getPersonaRecibe() { return personaRecibe; }
    public String getPuestoRecibe() { return puestoRecibe; }
    public String getDepartamentoRecibe() { return departamentoRecibe; }
    public String getResponsable() { return responsable; }
    public List<Linea> getProductos() { return productos; }
    public String getObservaciones() { return observaciones; }
}
