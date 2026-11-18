package com.sapam.inventario.reportes;

import java.time.LocalDate;
import java.util.List;

public class ReporteMaterialesDepartamentoDTO {

    private String nombreDepartamento;
    private String areaResponsable;
    private Long totalMovimientos;
    private LocalDate fechaUltimoMovimiento;
    private List<ReporteMaterialEntregadoDTO> materialesEntregados; // reutilizamos la misma estructura

    // Getters, setters, constructors

    public String getNombreDepartamento() { return nombreDepartamento; }
    public void setNombreDepartamento(String nombreDepartamento) { this.nombreDepartamento = nombreDepartamento; }

    public String getAreaResponsable() { return areaResponsable; }
    public void setAreaResponsable(String areaResponsable) { this.areaResponsable = areaResponsable; }

    public Long getTotalMovimientos() { return totalMovimientos; }
    public void setTotalMovimientos(Long totalMovimientos) { this.totalMovimientos = totalMovimientos; }

    public LocalDate getFechaUltimoMovimiento() { return fechaUltimoMovimiento; }
    public void setFechaUltimoMovimiento(LocalDate fechaUltimoMovimiento) { this.fechaUltimoMovimiento = fechaUltimoMovimiento; }

    public List<ReporteMaterialEntregadoDTO> getMaterialesEntregados() { return materialesEntregados; }
    public void setMaterialesEntregados(List<ReporteMaterialEntregadoDTO> materialesEntregados) { this.materialesEntregados = materialesEntregados; }
}