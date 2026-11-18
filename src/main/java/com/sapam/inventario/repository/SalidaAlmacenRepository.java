package com.sapam.inventario.repository;

import com.sapam.inventario.entity.SalidaAlmacen;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalidaAlmacenRepository extends RepositorioSGIA<SalidaAlmacen, Long> {

    Optional<SalidaAlmacen> findByFolio(String folio);

    List<SalidaAlmacen> findByFechaBetweenOrderByFechaDescHoraDesc(LocalDate inicio, LocalDate fin);

    // ============================================================
    // Reportes 6 y 7: se alimentan de lo que cada salida ya registró,
    // no de un directorio de trabajadores que habría que mantener a mano.
    // ============================================================

    List<SalidaAlmacen> findByPersonaSolicitaIgnoreCaseOrderByFechaDescHoraDesc(String personaSolicita);

    List<SalidaAlmacen> findByDepartamentoSolicitaIgnoreCaseOrderByFechaDescHoraDesc(String departamento);

    /** Nombres distintos que han solicitado alguna salida: alimenta el selector. */
    @Query("SELECT DISTINCT s.personaSolicita FROM SalidaAlmacen s "
            + "WHERE s.personaSolicita IS NOT NULL ORDER BY s.personaSolicita")
    List<String> personasQueSolicitaron();

    /** Departamentos distintos registrados en alguna salida: alimenta el selector. */
    @Query("SELECT DISTINCT s.departamentoSolicita FROM SalidaAlmacen s "
            + "WHERE s.departamentoSolicita IS NOT NULL ORDER BY s.departamentoSolicita")
    List<String> departamentosQueSolicitaron();

    /** Nombres distintos que ya han retirado material: alimenta el selector. */
    @Query("SELECT DISTINCT s.personaRecibe FROM SalidaAlmacen s "
            + "WHERE s.personaRecibe IS NOT NULL ORDER BY s.personaRecibe")
    List<String> personasQueRecibieron();

    // === Lo que usa la aplicación ===

    List<SalidaAlmacen> findAll();

    <S extends SalidaAlmacen> S save(S salida);

    long count();
}
