package com.sapam.inventario.reportes;

import java.time.LocalDate;
import java.util.List;

public class ReporteHerramientasPendientesDevolucionDTO {

    private List<ReportePrestamoPendienteDTO> prestamosPendientes; // préstamos con fecha de devolución pasada o próxima

    // Getters, setters, constructors

    public List<ReportePrestamoPendienteDTO> getPrestamosPendientes() { return prestamosPendientes; }
    public void setPrestamosPendientes(List<ReportePrestamoPendienteDTO> prestamosPendientes) { this.prestamosPendientes = prestamosPendientes; }
}

class ReportePrestamoPendienteDTO {

    private String folio; // folio del préstamo, lo pone el sistema (PRE-...)
    private String nombreHerramienta;
    private String numeroSerie;
    private String personaRecibe; // quién se la lleva, texto libre
    private LocalDate fechaEntrega;
    private LocalDate fechaEstimadaDevolucion;
    private LocalDate fechaVencimiento; // fecha en que debería haberse devuelto
    private long diasVencido; // días que lleva vencido el préstamo
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

    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }

    public long getDiasVencido() { return diasVencido; }
    public void setDiasVencido(long diasVencido) { this.diasVencido = diasVencido; }

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }
}