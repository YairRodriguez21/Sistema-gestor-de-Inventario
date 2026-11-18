package com.sapam.inventario.repository;

import com.sapam.inventario.entity.ProductoSalida;

import java.util.List;

public interface ProductoSalidaRepository extends RepositorioSGIA<ProductoSalida, Long> {

    List<ProductoSalida> findBySalidaAlmacenId(Long salidaAlmacenId);
}
