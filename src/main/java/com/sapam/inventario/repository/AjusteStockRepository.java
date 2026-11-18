package com.sapam.inventario.repository;

import com.sapam.inventario.entity.AjusteStock;

import java.util.List;

public interface AjusteStockRepository extends RepositorioSGIA<AjusteStock, Integer> {

    List<AjusteStock> findByProductoId(Integer productoId);

    <S extends AjusteStock> S save(S ajuste);
}
