package com.sapam.inventario.reportes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class ReporteSalidasPeriodoDTO {

    private String folio;
    private LocalDate fecha;
    private LocalTime hora;
    private String personaSolicita;
    private String puestoSolicita;
    private String departamentoSolicita;
    private String motivoSalida;
    private String usuarioResponsable;
    private List<ReporteSalidaProductoDTO> productos; // productos con cantidades y quien recibió

    // Getters, setters, constructors

    public String getFolio() { return folio; }
    public void setFolio(String folio) { this.folio = folio; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }

    public String getPersonaSolicita() { return personaSolicita; }
    public void setPersonaSolicita(String personaSolicita) { this.personaSolicita = personaSolicita; }

    public String getPuestoSolicita() { return puestoSolicita; }
    public void setPuestoSolicita(String puestoSolicita) { this.puestoSolicita = puestoSolicita; }

    public String getDepartamentoSolicita() { return departamentoSolicita; }
    public void setDepartamentoSolicita(String departamentoSolicita) { this.departamentoSolicita = departamentoSolicita; }

    public String getMotivoSalida() { return motivoSalida; }
    public void setMotivoSalida(String motivoSalida) { this.motivoSalida = motivoSalida; }

    public String getUsuarioResponsable() { return usuarioResponsable; }
    public void setUsuarioResponsable(String usuarioResponsable) { this.usuarioResponsable = usuarioResponsable; }

    public List<ReporteSalidaProductoDTO> getProductos() { return productos; }
    public void setProductos(List<ReporteSalidaProductoDTO> productos) { this.productos = productos; }
}

class ReporteSalidaProductoDTO {

    private String codigoBarras;
    private String nombreProducto;
    private Integer cantidadEntregada;
    private String nombreReceptor;
    private String cargoReceptor;
    private String areaReceptor;
    private String codigoInterno; // código interno del producto de la línea

    // Getters, setters, constructors

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public Integer getCantidadEntregada() { return cantidadEntregada; }
    public void setCantidadEntregada(Integer cantidadEntregada) { this.cantidadEntregada = cantidadEntregada; }

    public String getNombreReceptor() { return nombreReceptor; }
    public void setNombreReceptor(String nombreReceptor) { this.nombreReceptor = nombreReceptor; }

    public String getCargoReceptor() { return cargoReceptor; }
    public void setCargoReceptor(String cargoReceptor) { this.cargoReceptor = cargoReceptor; }

    public String getAreaReceptor() { return areaReceptor; }
    public void setAreaReceptor(String areaReceptor) { this.areaReceptor = areaReceptor; }

    public String getCodigoInterno() { return codigoInterno; }
    public void setCodigoInterno(String codigoInterno) { this.codigoInterno = codigoInterno; }
}