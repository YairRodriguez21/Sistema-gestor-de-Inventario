package com.sapam.inventario.reportes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class ReporteKardexProductoDTO {

    private Long productoId;
    private String codigoBarras;
    private String nombreProducto;
    private String marca;
    private String modelo;
    private String seccion;
    private List<ReporteMovimientoKardexDTO> movimientos; // historial completo de kardex

    // Getters, setters, constructors

    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }

    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }

    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }

    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }

    public String getSeccion() { return seccion; }
    public void setSeccion(String seccion) { this.seccion = seccion; }

    public List<ReporteMovimientoKardexDTO> getMovimientos() { return movimientos; }
    public void setMovimientos(List<ReporteMovimientoKardexDTO> movimientos) { this.movimientos = movimientos; }
}

class ReporteMovimientoKardexDTO {

    private LocalDate fecha;
    private LocalTime hora;
    private String tipoMovimiento;
    private String folio;
    private Integer cantidadMovida;
    private Integer existenciaAnterior;
    private Integer existenciaPosterior;
    private String nombreUsuario;
    private String observaciones;
    private String codigoInterno; // código interno del producto del kardex

    // Getters, setters, constructors

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }

    public String getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(String tipoMovimiento) { this.tipoMovimiento = tipoMovimiento; }

    public String getFolio() { return folio; }
    public void setFolio(String folio) { this.folio = folio; }

    public Integer getCantidadMovida() { return cantidadMovida; }
    public void setCantidadMovida(Integer cantidadMovida) { this.cantidadMovida = cantidadMovida; }

    public Integer getExistenciaAnterior() { return existenciaAnterior; }
    public void setExistenciaAnterior(Integer existenciaAnterior) { this.existenciaAnterior = existenciaAnterior; }

    public Integer getExistenciaPosterior() { return existenciaPosterior; }
    public void setExistenciaPosterior(Integer existenciaPosterior) { this.existenciaPosterior = existenciaPosterior; }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public String getCodigoInterno() { return codigoInterno; }
    public void setCodigoInterno(String codigoInterno) { this.codigoInterno = codigoInterno; }
}