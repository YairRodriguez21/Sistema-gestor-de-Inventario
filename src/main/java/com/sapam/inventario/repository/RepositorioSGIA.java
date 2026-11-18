package com.sapam.inventario.repository;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Base de todos los repositorios de SGIA.
 *
 * Extiende únicamente la raíz marcadora de Spring Data, que no trae nada
 * consigo: cada repositorio declara a mano los métodos que de verdad usa. Por
 * eso ninguno de ellos hereda los borrados de {@code CrudRepository} o
 * {@code JpaRepository} — ni {@code delete}, ni {@code deleteById}, ni
 * {@code deleteAll}, ni los borrados masivos por lote.
 *
 * No es una costumbre que haya que recordar: en este sistema todo se da de
 * baja con estado ({@code BAJAS} / {@code activo=false}) para conservar la
 * trazabilidad, así que el borrado físico sobra y no debe poder escribirse ni
 * por error. Si algún día hace falta borrar de verdad en alguna tabla, que sea
 * una decisión consciente y anotada, no algo que se cuela porque alguien
 * cambió la línea {@code extends}.
 *
 * Si se cambia esta interfaz para que extienda de {@code JpaRepository},
 * vuelven los dieciséis {@code delete} de golpe: la prueba de reflexión de
 * {@code RepositoriosTest} salta sola.
 */
@NoRepositoryBean
public interface RepositorioSGIA<T, ID> extends Repository<T, ID> {
}
