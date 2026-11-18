package com.sapam.inventario.repository;

import com.sapam.inventario.entity.Producto;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends RepositorioSGIA<Producto, Integer> {
    Optional<Producto> findByCodigoBarras(String codigoBarras);

    /** Búsqueda por identificador interno: un lector USB puede escribirlo. */
    Optional<Producto> findByCodigoInterno(String codigoInterno);
    List<Producto> findByExistenciaActualLessThanEqual(Integer stockMinimo);

    // === Consultas de reportes ===

    /** Reporte 1: productos activos (se excluyen los dados de baja). */
    @Query("SELECT p FROM Producto p WHERE p.estado IS NULL OR p.estado <> 'BAJAS' ORDER BY p.nombre")
    List<Producto> findActivos();

    /** Reporte 2: productos agotados (existencia en 0). */
    @Query("SELECT p FROM Producto p WHERE p.existenciaActual = 0 AND (p.estado IS NULL OR p.estado <> 'BAJAS') ORDER BY p.nombre")
    List<Producto> findAgotados();

    /**
     * Reporte 3: por debajo del stock mínimo, sin incluir los agotados
     * (esos ya aparecen en el reporte de agotados).
     */
    @Query("SELECT p FROM Producto p WHERE p.existenciaActual > 0 AND p.existenciaActual <= p.existenciaMinima AND (p.estado IS NULL OR p.estado <> 'BAJAS') ORDER BY p.existenciaActual")
    List<Producto> findBajoStockMinimo();

    // === Lo que usa la aplicación ===

    List<Producto> findAll();

    Optional<Producto> findById(Integer id);

    <S extends Producto> S save(S producto);
}
