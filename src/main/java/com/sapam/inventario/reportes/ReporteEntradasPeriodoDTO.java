package com.sapam.inventario.reportes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class ReporteEntradasPeriodoDTO {

    private String folio;
    private LocalDate fecha;
    private LocalTime hora;
    private String usuarioRegistro;
    private String observaciones;
    private List<ReporteEntradaProductoDTO> productos; // productos con cantidades

    // Getters, setters, constructors

    public String getFolio() { return folio; }
    public void setFolio(String folio) { this.folio = folio; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }

    public String getUsuarioRegistro() { return usuarioRegistro; }
    public void setUsuarioRegistro(String usuarioRegistro) { this.usuarioRegistro = usuarioRegistro; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public List<ReporteEntradaProductoDTO> getProductos() { return productos; }
    public void setProductos(List<ReporteEntradaProductoDTO> productos) { this.productos = productos; }
}

class ReporteEntradaProductoDTO {

    private String codigoBarras;
    private String nombreProducto;
    private String descripcion;
    private Integer cantidadIngresada;
    private String unidadMedida;
    private String codigoInterno; // código interno del producto de la línea

    // Getters, setters, constructors

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Integer getCantidadIngresada() { return cantidadIngresada; }
    public void setCantidadIngresada(Integer cantidadIngresada) { this.cantidadIngresada = cantidadIngresada; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public String getCodigoInterno() { return codigoInterno; }
    public void setCodigoInterno(String codigoInterno) { this.codigoInterno = codigoInterno; }
}