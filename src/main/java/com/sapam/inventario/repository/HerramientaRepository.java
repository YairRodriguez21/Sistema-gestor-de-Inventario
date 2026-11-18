package com.sapam.inventario.repository;

import com.sapam.inventario.entity.Herramienta;

import java.util.List;
import java.util.Optional;

public interface HerramientaRepository extends RepositorioSGIA<Herramienta, Long> {

    /** Búsqueda por el código de la etiqueta: lo que escribe el lector USB. */
    Optional<Herramienta> findByCodigoBarras(String codigoBarras);

    /**
     * Búsqueda por número de serie. Devuelve lista porque el campo es opcional
     * y se puede repetir: en los datos reales hay dos herramientas con serie
     * "SN", y con una sola ficha el lector no sabría cuál enseñar.
     */
    List<Herramienta> findByNumeroSerieIgnoreCase(String numeroSerie);

    List<Herramienta> findByNombreContainingIgnoreCase(String nombre);

    /**
     * Todo lo que sigue de alta. Las dadas de baja quedan en la tabla (para
     * conservar la trazabilidad) pero desaparecen de la pantalla.
     */
    List<Herramienta> findByEstadoNot(String estado);

    Optional<Herramienta> findById(Long id);

    /** Todas, dadas de alta o de baja: lo usa el relleno de códigos antiguos. */
    List<Herramienta> findAll();

    <S extends Herramienta> S save(S herramienta);
}
