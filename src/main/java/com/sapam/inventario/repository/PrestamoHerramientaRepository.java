package com.sapam.inventario.repository;

import com.sapam.inventario.entity.PrestamoHerramienta;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PrestamoHerramientaRepository extends RepositorioSGIA<PrestamoHerramienta, Long> {

    /** Préstamos que aún no han sido devueltos (sin fecha de devolución). */
    List<PrestamoHerramienta> findByFechaDevolucionIsNullOrderByFechaEstimadaDevolucionAsc();

    /** Préstamos vencidos: sin devolver y con la fecha estimada ya pasada. */
    List<PrestamoHerramienta> findByFechaDevolucionIsNullAndFechaEstimadaDevolucionBeforeOrderByFechaEstimadaDevolucionAsc(LocalDate fecha);

    /** Préstamo abierto de una herramienta, si alguien la tiene. */
    List<PrestamoHerramienta> findByHerramientaIdAndFechaDevolucionIsNullOrderByIdDesc(Long herramientaId);

    /** Búsqueda por folio, como en entradas y salidas. */
    Optional<PrestamoHerramienta> findByFolio(String folio);

    List<PrestamoHerramienta> findAll();

    Optional<PrestamoHerramienta> findById(Long id);

    <S extends PrestamoHerramienta> S save(S prestamo);
}
