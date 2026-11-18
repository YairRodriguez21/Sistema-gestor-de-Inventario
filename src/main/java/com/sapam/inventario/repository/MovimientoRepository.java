package com.sapam.inventario.repository;

import com.sapam.inventario.entity.Movimiento;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoRepository extends RepositorioSGIA<Movimiento, Integer> {
    List<Movimiento> findByProductoId(Integer productoId);
    List<Movimiento> findByFechaBetween(LocalDateTime inicio, LocalDateTime fin);

    @Query("SELECT m FROM Movimiento m WHERE m.tipo = :tipo AND m.fecha BETWEEN :inicio AND :fin")
    List<Movimiento> findByTipoAndFechaBetween(
        @Param("tipo") Movimiento.TipoMovimiento tipo,
        @Param("inicio") LocalDateTime inicio,
        @Param("fin") LocalDateTime fin
    );

    // === Consultas de reportes ===

    /** Reporte 13: movimientos realizados por un usuario. */
    List<Movimiento> findByUsuarioIdOrderByFechaDesc(Integer usuarioId);

    <S extends Movimiento> S save(S movimiento);

    long count();
}
