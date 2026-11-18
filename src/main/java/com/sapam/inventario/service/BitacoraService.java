package com.sapam.inventario.service;

import com.sapam.inventario.entity.AuditoriaSistema;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.AuditoriaSistemaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bitácora del sistema: registra toda acción importante de forma automática.
 *
 * Los métodos usan REQUIRES_NEW para que un error de guardado en el módulo de
 * negocio no revierta el registro de auditoría, y viceversa: la bitácora no
 * debe romper la operación principal.
 */
@Service
public class BitacoraService {

    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";

    public static final String CREAR_USUARIO = "CREAR_USUARIO";

    public static final String MODIFICAR_USUARIO = "MODIFICAR_USUARIO";

    public static final String BAJA_USUARIO = "BAJA_USUARIO";

    public static final String REACTIVAR_USUARIO = "REACTIVAR_USUARIO";

    public static final String CREAR_PRODUCTO = "CREAR_PRODUCTO";
    public static final String MODIFICAR_PRODUCTO = "MODIFICAR_PRODUCTO";
    public static final String ELIMINAR_PRODUCTO = "ELIMINAR_PRODUCTO";
    public static final String SALIDA_ALMACEN = "SALIDA_ALMACEN";

    public static final String ENTRADA_ALMACEN = "ENTRADA_ALMACEN";
    public static final String AJUSTE_STOCK = "AJUSTE_STOCK";
    public static final String GENERAR_REPORTE = "GENERAR_REPORTE";

    public static final String CREAR_HERRAMIENTA = "CREAR_HERRAMIENTA";
    public static final String MODIFICAR_HERRAMIENTA = "MODIFICAR_HERRAMIENTA";
    public static final String BAJA_HERRAMIENTA = "BAJA_HERRAMIENTA";
    public static final String PRESTAMO_HERRAMIENTA = "PRESTAMO_HERRAMIENTA";
    public static final String DEVOLUCION_HERRAMIENTA = "DEVOLUCION_HERRAMIENTA";

    /**
     * Nombre que cuelga de los eventos escritos sin cuenta atrás: los del
     * propio arranque del sistema.
     *
     * Lo comparten la ventana y este servicio para que el selector de filtro y
     * la tabla digan lo mismo; si cada uno se inventara el suyo, filtrar por
     * "Sistema" no traería nada.
     */
    public static final String USUARIO_SISTEMA = "Sistema";

    /** Eventos por página: los que caben en la ventana de bitácora. */
    public static final int TAMANO_PAGINA = 200;

    /**
     * Una ventana de la bitácora.
     *
     * {@code paginas} nunca baja de 1, aunque no haya nada, para que la
     * pantalla pueda decir "Página 1 de 1" en vez de un cero que no significa
     * nada; y {@code pagina} es la que de verdad se pintó, que puede ser otra
     * si la que se pidió se salía del rango.
     */
    public record Pagina(List<AuditoriaSistema> eventos, int pagina, int paginas, long total) {}

    @Autowired
    private AuditoriaSistemaRepository auditoriaRepository;

    /**
     * Registra un evento en la bitácora.
     * Nunca lanza excepción hacia arriba: no debe romper la operación que lo originó.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String accion, String moduloAfectado,
                          String informacionModificada, String observaciones,
                          Usuario usuario) {
        try {
            AuditoriaSistema evento = new AuditoriaSistema();
            evento.setFecha(LocalDate.now());
            evento.setHora(LocalTime.now());
            evento.setUsuario(usuario);
            evento.setAccion(accion);
            evento.setModuloAfectado(moduloAfectado);
            evento.setInformacionModificada(informacionModificada);
            evento.setObservaciones(observaciones);
            // Código único: permite verificar la autenticidad del evento escaneándolo
            evento.setCodigoVerificacion("AUD-" + UUID.randomUUID());
            auditoriaRepository.save(evento);
        } catch (Exception e) {
            // Se traga el error para no afectar la operación principal,
            // pero queda visible en el log del servidor.
            System.err.println("No se pudo registrar en la bitácora: " + e.getMessage());
        }
    }

    /**
     * Los 200 eventos más recientes, del más nuevo al más viejo.
     *
     * Siempre es el mismo tope, también en los filtros de abajo: la pantalla
     * nunca pide "toda la bitácora", pide lo último, y acotar en la consulta
     * hace que la ventana abra igual de rápido con 122 filas que con las que
     * acumule dentro de un par de años.
     */
    @Transactional(readOnly = true)
    public List<AuditoriaSistema> ultimosEventos() {
        return auditoriaRepository.findTop200ByOrderByFechaDescHoraDesc();
    }

    /** Últimos 200 eventos de un usuario, sin filtrar todo lo que haya. */
    @Transactional(readOnly = true)
    public List<AuditoriaSistema> porUsuario(Integer usuarioId) {
        return auditoriaRepository.findTop200ByUsuarioIdOrderByFechaDescHoraDesc(usuarioId);
    }

    /** Últimos 200 eventos de una acción, sin filtrar todo lo que haya. */
    @Transactional(readOnly = true)
    public List<AuditoriaSistema> porAccion(String accion) {
        return auditoriaRepository.findTop200ByAccionOrderByFechaDescHoraDesc(accion);
    }

    // ------------------------------------------------------------ paginación

    /**
     * Una página de la bitácora, opcionalmente filtrada por acción y por
     * usuario.
     *
     * Es el que usa la ventana para poder avanzar hacia atrás en el tiempo. A
     * diferencia de {@link #ultimosEventos()}, que siempre trae lo último, aquí
     * se puede ir a cualquier punto de la historia sin cargarla entera: el
     * corte lo hace la base de datos, no la memoria de la aplicación.
     *
     * @param accion  acción a filtrar, o {@code null} para todas
     * @param usuario nombre del usuario a filtrar, {@code null} para todos
     * @param pagina  índice desde 0; 0 es lo más reciente
     */
    @Transactional(readOnly = true)
    public Pagina eventos(String accion, String usuario, int pagina) {
        int pedida = Math.max(0, pagina);
        Page<AuditoriaSistema> consultada =
                auditoriaRepository.buscar(accion, usuario, PageRequest.of(pedida, TAMANO_PAGINA));

        int paginas = Math.max(1, consultada.getTotalPages());
        int real = Math.min(pedida, paginas - 1);
        if (real != consultada.getNumber()) {
            // Se pidió una página que ya no existe —la bitácora se movió o se
            // aplicó un filtro— y se cae a la última que sí hay.
            consultada = auditoriaRepository.buscar(accion, usuario,
                    PageRequest.of(real, TAMANO_PAGINA));
        }

        return new Pagina(consultada.getContent(), real, paginas,
                consultada.getTotalElements());
    }

    /** Acciones que existen en la bitácora, para llenar su selector. */
    @Transactional(readOnly = true)
    public List<String> acciones() {
        return auditoriaRepository.accionesDistintas();
    }

    /**
     * Usuarios que existen en la bitácora, más {@link #USUARIO_SISTEMA} si hay
     * eventos escritos sin cuenta atrás.
     *
     * Salen de la historia entera y no de lo que se está mirando, porque si
     * salieran de la página de turno cambiar de página cambiaría lo que se
     * puede filtrar.
     */
    @Transactional(readOnly = true)
    public List<String> usuarios() {
        List<String> nombres = auditoriaRepository.usuariosConEventos().stream()
                .map(Usuario::getNombre)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .distinct()
                .sorted()
                .toList();

        if (auditoriaRepository.countByUsuarioIsNull() <= 0) {
            return nombres;
        }

        List<String> conSistema = new ArrayList<>(nombres.size() + 1);
        conSistema.add(USUARIO_SISTEMA);
        conSistema.addAll(nombres);
        return conSistema;
    }
}
