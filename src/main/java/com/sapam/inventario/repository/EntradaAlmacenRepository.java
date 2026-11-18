package com.sapam.inventario.repository;

import com.sapam.inventario.entity.EntradaAlmacen;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EntradaAlmacenRepository extends RepositorioSGIA<EntradaAlmacen, Long> {

    Optional<EntradaAlmacen> findByFolio(String folio);

    List<EntradaAlmacen> findByFechaBetweenOrderByFechaDescHoraDesc(LocalDate inicio, LocalDate fin);

    List<EntradaAlmacen> findAll();

    <S extends EntradaAlmacen> S save(S entrada);

    long count();
}
