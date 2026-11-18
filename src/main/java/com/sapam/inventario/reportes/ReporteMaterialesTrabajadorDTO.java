package com.sapam.inventario.reportes;

import java.time.LocalDate;
import java.util.List;

public class ReporteMaterialesTrabajadorDTO {

    private String nombreTrabajador;
    private String cargoTrabajador;
    private String departamentoTrabajador;
    private Long totalMovimientos;
    private LocalDate fechaUltimoMovimiento;
    private List<ReporteMaterialEntregadoDTO> materialesEntregados; // lista de materiales con folio y fecha

    // Getters, setters, constructors

    public String getNombreTrabajador() { return nombreTrabajador; }
    public void setNombreTrabajador(String nombreTrabajador) { this.nombreTrabajador = nombreTrabajador; }

    public String getCargoTrabajador() { return cargoTrabajador; }
    public void setCargoTrabajador(String cargoTrabajador) { this.cargoTrabajador = cargoTrabajador; }

    public String getDepartamentoTrabajador() { return departamentoTrabajador; }
    public void setDepartamentoTrabajador(String departamentoTrabajador) { this.departamentoTrabajador = departamentoTrabajador; }

    public Long getTotalMovimientos() { return totalMovimientos; }
    public void setTotalMovimientos(Long totalMovimientos) { this.totalMovimientos = totalMovimientos; }

    public LocalDate getFechaUltimoMovimiento() { return fechaUltimoMovimiento; }
    public void setFechaUltimoMovimiento(LocalDate fechaUltimoMovimiento) { this.fechaUltimoMovimiento = fechaUltimoMovimiento; }

    public List<ReporteMaterialEntregadoDTO> getMaterialesEntregados() { return materialesEntregados; }
    public void setMaterialesEntregados(List<ReporteMaterialEntregadoDTO> materialesEntregados) { this.materialesEntregados = materialesEntregados; }
}

class ReporteMaterialEntregadoDTO {

    private String folioSalida;
    private LocalDate fechaSalida;
    private String motivoSalida;
    private String nombreProducto;
    private Integer cantidadEntregada;
    private String codigoBarras; // código de barras del producto entregado

    // Getters, setters, constructors

    public String getFolioSalida() { return folioSalida; }
    public void setFolioSalida(String folioSalida) { this.folioSalida = folioSalida; }

    public LocalDate getFechaSalida() { return fechaSalida; }
    public void setFechaSalida(LocalDate fechaSalida) { this.fechaSalida = fechaSalida; }

    public String getMotivoSalida() { return motivoSalida; }
    public void setMotivoSalida(String motivoSalida) { this.motivoSalida = motivoSalida; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public Integer getCantidadEntregada() { return cantidadEntregada; }
    public void setCantidadEntregada(Integer cantidadEntregada) { this.cantidadEntregada = cantidadEntregada; }

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }
}