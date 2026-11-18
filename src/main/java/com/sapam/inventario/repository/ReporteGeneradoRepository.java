package com.sapam.inventario.repository;

import com.sapam.inventario.entity.ReporteGenerado;

import java.util.List;

public interface ReporteGeneradoRepository extends RepositorioSGIA<ReporteGenerado, Integer> {

    List<ReporteGenerado> findByUsuarioId(Integer usuarioId);

    List<ReporteGenerado> findAll();

    <S extends ReporteGenerado> S save(S reporte);
}
