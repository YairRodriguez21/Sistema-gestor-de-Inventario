package com.sapam.inventario.repository;

import com.sapam.inventario.entity.AuditoriaSistema;
import com.sapam.inventario.entity.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Bitácora del sistema: aquí solo se escribe desde {@code BitacoraService} y
 * solo se lee.
 *
 * La interfaz extiende {@link RepositorioSGIA}, que no trae nada consigo, y
 * declara a mano lo único que hace falta: guardar, contar y los tres filtros
 * de consulta. Por eso mismo esta interfaz <b>no tiene método de borrado
 * ninguno</b> — ni {@code delete}, ni {@code deleteById}, ni {@code deleteAll}:
 * no los declara ella y no los hereda de nadie, así que quien escriba código
 * contra la bitácora no puede borrarla ni por error, porque el compilador no
 * se lo permite.
 *
 * Ese es el requisito de bitácora "sin posibilidad de eliminación": no basta
 * con que nadie llame a borrar hoy, hay que dejar la puerta cerrada.
 *
 * Si algún día se toca esta clase, hay que mantener el mismo criterio: no se
 * hereda de {@code CrudRepository}/{@code JpaRepository} y no se añade ningún
 * método que borre. {@code BitacoraTest} lo comprueba con un test de reflexión.
 */
public interface AuditoriaSistemaRepository extends RepositorioSGIA<AuditoriaSistema, Long> {

    /** Única escritura permitida: registrar un evento nuevo. */
    <S extends AuditoriaSistema> S save(S evento);

    /** Para las pruebas, que comprueban que la bitácora crece al operar. */
    long count();

    /**
     * Los 200 eventos más recientes, del más nuevo al más viejo.
     *
     * Lo mismo vale para los dos filtros de abajo, y la razón es la misma en
     * los tres: son las tres lecturas que puede hacer quien pida la bitácora,
     * y ninguna pide "todo". El límite está en la consulta y no en el
     * controlador porque así no depende de que alguien se acuerde de ponerlo
     * en cada endpoint; un filtrado sin tope crece con la tabla y acabaría
     * devolviendo cientos de miles de filas en una sola respuesta.
     */
    List<AuditoriaSistema> findTop200ByOrderByFechaDescHoraDesc();

    List<AuditoriaSistema> findTop200ByUsuarioIdOrderByFechaDescHoraDesc(Integer usuarioId);

    List<AuditoriaSistema> findTop200ByAccionOrderByFechaDescHoraDesc(String accion);

    // --------------------------------------------------------------- páginas

    /**
     * Una ventana de la bitácora: los eventos de esa página y cuántas hay en
     * total, siempre del más reciente al más viejo.
     *
     * Los dos filtros son opcionales: los parámetros llegan {@code null} cuando
     * no se está filtrando por esa columna, y la consulta los deja pasar. El
     * filtro de usuario se pide por nombre porque así se apoya en lo que se ve
     * en el selector de la ventana; "Sistema" es el nombre que cuelga de los
     * eventos escritos sin cuenta atrás, los del propio arranque.
     *
     * El conteo se declara aparte y no se deja que Spring Data lo deduzca: la
     * consulta tiene un {@code OR} con un literal y una autodeducción no daría
     * con lo mismo.
     */
    @Query(value = """
            SELECT a FROM AuditoriaSistema a
             WHERE (:accion IS NULL OR a.accion = :accion)
               AND (:usuario IS NULL
                    OR (:usuario = 'Sistema' AND a.usuario IS NULL)
                    OR a.usuario.nombre = :usuario)
             ORDER BY a.fecha DESC, a.hora DESC
            """, countQuery = """
            SELECT COUNT(a) FROM AuditoriaSistema a
             WHERE (:accion IS NULL OR a.accion = :accion)
               AND (:usuario IS NULL
                    OR (:usuario = 'Sistema' AND a.usuario IS NULL)
                    OR a.usuario.nombre = :usuario)
            """)
    Page<AuditoriaSistema> buscar(@Param("accion") String accion,
                                  @Param("usuario") String usuario,
                                  Pageable pageable);

    /**
     * Para llenar los selectores de filtro con lo que de verdad existe, y no
     * con lo que tocó en la página de turno: si las opciones salieran de la
     * página visible, cambiar de página cambiaría lo que se puede filtrar.
     */
    @Query("SELECT DISTINCT a.accion FROM AuditoriaSistema a WHERE a.accion IS NOT NULL")
    List<String> accionesDistintas();

    @Query("SELECT DISTINCT a.usuario FROM AuditoriaSistema a WHERE a.usuario IS NOT NULL")
    List<Usuario> usuariosConEventos();

    /** Para saber si conviene ofrecer el filtro "Sistema". */
    long countByUsuarioIsNull();
}
