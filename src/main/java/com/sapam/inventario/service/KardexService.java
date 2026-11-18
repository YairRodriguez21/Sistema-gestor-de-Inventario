package com.sapam.inventario.service;

import com.sapam.inventario.entity.Kardex;
import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.KardexRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Kardex automático: por cada movimiento de existencias queda registrada
 * fecha, hora, tipo, folio, cantidad, existencia anterior, existencia posterior,
 * usuario responsable y observaciones.
 */
@Service
public class KardexService {

    @Autowired
    private KardexRepository kardexRepository;

    /**
     * Registra una línea de kardex.
     * REQUIRES_NEW para que no se pierda si luego falla la operación que lo llamó
     * por un error no relacionado (la trazabilidad debe ser durable).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Producto producto, String tipoMovimiento, String folio,
                          int cantidadMovida, int existenciaAnterior, int existenciaPosterior,
                          Usuario usuario, String observaciones) {
        Kardex kardex = new Kardex();
        kardex.setProducto(producto);
        kardex.setFecha(LocalDate.now());
        kardex.setHora(LocalTime.now());
        kardex.setTipoMovimiento(tipoMovimiento);
        kardex.setFolio(folio);
        kardex.setCantidadMovida(cantidadMovida);
        kardex.setExistenciaAnterior(existenciaAnterior);
        kardex.setExistenciaPosterior(existenciaPosterior);
        kardex.setUsuarioResponsable(usuario);
        kardex.setObservaciones(observaciones);
        kardexRepository.save(kardex);
    }

    /** Historial de kardex de un producto (el que se muestra al escanear su código). */
    @Transactional(readOnly = true)
    public List<Kardex> historialPorProducto(Integer productoId) {
        return kardexRepository.findByProductoIdOrderByFechaDescHoraDesc(productoId);
    }

    @Transactional(readOnly = true)
    public List<Kardex> porFolio(String folio) {
        return kardexRepository.findByFolio(folio);
    }
}
