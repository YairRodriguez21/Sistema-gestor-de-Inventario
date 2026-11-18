package com.sapam.inventario.reportes;

import java.time.LocalDate;
import java.util.List;

public class ReporteHerramientasPrestadasDTO {

    private List<ReportePrestamoHerramientaDTO> prestamosActivos; // préstamos que aún no han sido devueltos

    // Getters, setters, constructors

    public List<ReportePrestamoHerramientaDTO> getPrestamosActivos() { return prestamosActivos; }
    public void setPrestamosActivos(List<ReportePrestamoHerramientaDTO> prestamosActivos) { this.prestamosActivos = prestamosActivos; }
}

class ReportePrestamoHerramientaDTO {

    private String folio; // folio del préstamo, lo pone el sistema (PRE-...)
    private String nombreHerramienta;
    private String numeroSerie;
    private String personaRecibe; // quién se la lleva, texto libre
    private LocalDate fechaEntrega;
    private LocalDate fechaEstimadaDevolucion;
    private LocalDate fechaDevolucion; // null si no ha sido devuelta
    private String estado; // PENDIENTE, DEVUELTO
    private String horaEntrega;
    private String codigoBarras; // código de la etiqueta de la herramienta

    // Getters, setters, constructors

    public String getFolio() { return folio; }
    public void setFolio(String folio) { this.folio = folio; }

    public String getNombreHerramienta() { return nombreHerramienta; }
    public void setNombreHerramienta(String nombreHerramienta) { this.nombreHerramienta = nombreHerramienta; }

    public String getNumeroSerie() { return numeroSerie; }
    public void setNumeroSerie(String numeroSerie) { this.numeroSerie = numeroSerie; }

    public String getPersonaRecibe() { return personaRecibe; }
    public void setPersonaRecibe(String personaRecibe) { this.personaRecibe = personaRecibe; }

    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDate fechaEntrega) { this.fechaEntrega = fechaEntrega; }

    public LocalDate getFechaEstimadaDevolucion() { return fechaEstimadaDevolucion; }
    public void setFechaEstimadaDevolucion(LocalDate fechaEstimadaDevolucion) { this.fechaEstimadaDevolucion = fechaEstimadaDevolucion; }

    public LocalDate getFechaDevolucion() { return fechaDevolucion; }
    public void setFechaDevolucion(LocalDate fechaDevolucion) { this.fechaDevolucion = fechaDevolucion; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getHoraEntrega() { return horaEntrega; }
    public void setHoraEntrega(String horaEntrega) { this.horaEntrega = horaEntrega; }

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }
}