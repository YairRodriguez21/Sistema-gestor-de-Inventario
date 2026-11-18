package com.sapam.inventario.repository;

import com.sapam.inventario.entity.Kardex;

import java.util.List;

public interface KardexRepository extends RepositorioSGIA<Kardex, Long> {

    /** Historial completo de un producto, más reciente primero. */
    List<Kardex> findByProductoIdOrderByFechaDescHoraDesc(Integer productoId);

    List<Kardex> findByFolio(String folio);

    /** Reporte 8: historial de movimientos por periodo. */
    List<Kardex> findByFechaBetweenOrderByFechaDescHoraDesc(java.time.LocalDate inicio, java.time.LocalDate fin);

    <S extends Kardex> S save(S kardex);

    long count();
}
