package com.sapam.inventario.repository;

import com.sapam.inventario.entity.ProductoEntrada;

import java.util.List;

public interface ProductoEntradaRepository extends RepositorioSGIA<ProductoEntrada, Long> {

    List<ProductoEntrada> findByEntradaAlmacenId(Long entradaAlmacenId);
}
