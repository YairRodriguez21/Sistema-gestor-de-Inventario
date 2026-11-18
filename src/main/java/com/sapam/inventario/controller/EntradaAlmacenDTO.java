package com.sapam.inventario.controller;

import com.sapam.inventario.entity.EntradaAlmacen;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * Entrada de almacén tal y como se sirve por HTTP.
 *
 * Se expone como DTO por lo que no arrastra: la entidad anida al usuario que
 * la registró, y ese objeto lleva la contraseña. Aquí queda solo el nombre,
 * que es lo que hace falta para la trazabilidad.
 *
 * Las líneas se devuelven siempre ordenadas por producto: la entidad las guarda
 * en un {@code Set}, que no tiene orden propio.
 */
public class EntradaAlmacenDTO {

    /** Una línea de la entrada: qué producto entró y en qué cantidad. */
    public record Linea(Integer productoId, String producto, Integer cantidad,
                        String observaciones) {}

    private static final Comparator<Linea> ORDEN =
            Comparator.comparing(Linea::productoId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final Long id;
    private final String folio;
    private final LocalDate fecha;
    private final LocalTime hora;
    private final String responsable;
    private final List<Linea> productos;
    private final String observaciones;

    public EntradaAlmacenDTO(EntradaAlmacen entrada) {
        this.id = entrada.getId();
        this.folio = entrada.getFolio();
        this.fecha = entrada.getFecha();
        this.hora = entrada.getHora();
        this.responsable = entrada.getUsuarioRegistro() == null
                ? "" : entrada.getUsuarioRegistro().getNombreCompleto();
        this.observaciones = entrada.getObservaciones();

        this.productos = entrada.getProductosIngresados() == null
                ? List.of()
                : entrada.getProductosIngresados().stream()
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
    public String getResponsable() { return responsable; }
    public List<Linea> getProductos() { return productos; }
    public String getObservaciones() { return observaciones; }
}
