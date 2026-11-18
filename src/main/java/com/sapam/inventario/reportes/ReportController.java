package com.sapam.inventario.reportes;

import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * API REST de los 13 reportes del SGIA.
 *
 * Esta clase solo resuelve el transporte HTTP (parámetros, código de estado y
 * usuario autenticado); la consulta a la base de datos vive en
 * {@link ReporteService}, que comparte con la aplicación de escritorio para
 * que ambos entreguen exactamente lo mismo.
 *
 * Ningún endpoint devuelve datos de ejemplo.
 */
@RestController
@RequestMapping("/api/reportes")
public class ReportController {

    private final ReporteService reporteService;
    private final UsuarioRepository usuarioRepository;

    public ReportController(ReporteService reporteService, UsuarioRepository usuarioRepository) {
        this.reporteService = reporteService;
        this.usuarioRepository = usuarioRepository;
    }

    // ============================================================
    // 1. REPORTE: Existencias Actuales
    // ============================================================
    @GetMapping("/existencias-actuales")
    public ResponseEntity<List<ReporteExistenciasActualesDTO>> getExistenciasActuales() {
        return ResponseEntity.ok(reporteService.existenciasActuales(usuarioEnSesion()));
    }

    // ============================================================
    // 2. REPORTE: Productos Agotados
    // ============================================================
    @GetMapping("/productos-agotados")
    public ResponseEntity<List<ReporteProductosAgotadosDTO>> getProductosAgotados() {
        return ResponseEntity.ok(reporteService.productosAgotados(usuarioEnSesion()));
    }

    // ============================================================
    // 3. REPORTE: Productos por debajo del stock mínimo
    // ============================================================
    @GetMapping("/productos-stock-minimo")
    public ResponseEntity<List<ReporteProductosStockMinimoDTO>> getProductosStockMinimo() {
        return ResponseEntity.ok(reporteService.productosStockMinimo(usuarioEnSesion()));
    }

    // ============================================================
    // 4. REPORTE: Entradas por periodo
    // ============================================================
    @GetMapping("/entradas/periodo")
    public ResponseEntity<List<ReporteEntradasPeriodoDTO>> getEntradasPorPeriodo(
            @RequestParam(required = false) LocalDate fechaInicio,
            @RequestParam(required = false) LocalDate fechaFin) {
        return ResponseEntity.ok(
                reporteService.entradasPorPeriodo(fechaInicio, fechaFin, usuarioEnSesion()));
    }

    // ============================================================
    // 5. REPORTE: Salidas por periodo
    // ============================================================
    @GetMapping("/salidas/periodo")
    public ResponseEntity<List<ReporteSalidasPeriodoDTO>> getSalidasPorPeriodo(
            @RequestParam(required = false) LocalDate fechaInicio,
            @RequestParam(required = false) LocalDate fechaFin) {
        return ResponseEntity.ok(
                reporteService.salidasPorPeriodo(fechaInicio, fechaFin, usuarioEnSesion()));
    }

    // ============================================================
    // 6. REPORTE: Materiales entregados por trabajador
    // ============================================================
    // Se busca por nombre escrito en la salida, no por id de directorio: no
    // existe un padrón de trabajadores que mantener.
    @GetMapping("/materiales/trabajador")
    public ResponseEntity<List<ReporteMaterialesTrabajadorDTO>> getMaterialesPorTrabajador(
            @RequestParam String persona) {
        return ResponseEntity.ok(
                reporteService.materialesPorTrabajador(persona, usuarioEnSesion()));
    }

    // ============================================================
    // 7. REPORTE: Materiales entregados por departamento
    // ============================================================
    @GetMapping("/materiales/departamento")
    public ResponseEntity<List<ReporteMaterialesDepartamentoDTO>> getMaterialesPorDepartamento(
            @RequestParam String departamento) {
        return ResponseEntity.ok(
                reporteService.materialesPorDepartamento(departamento, usuarioEnSesion()));
    }

    // ============================================================
    // 8. REPORTE: Historial de movimientos (kardex)
    // ============================================================
    @GetMapping("/historial/movimientos")
    public ResponseEntity<List<ReporteHistorialMovimientosDTO>> getHistorialMovimientos(
            @RequestParam(required = false) LocalDate fechaInicio,
            @RequestParam(required = false) LocalDate fechaFin) {
        return ResponseEntity.ok(
                reporteService.historialMovimientos(fechaInicio, fechaFin, usuarioEnSesion()));
    }

    // ============================================================
    // 9. REPORTE: Kardex de un producto
    // ============================================================
    @GetMapping("/kardex/producto/{productoId}")
    public ResponseEntity<ReporteKardexProductoDTO> getKarcedProducto(@PathVariable Long productoId) {
        ReporteKardexProductoDTO kardex =
                reporteService.kardexProducto(productoId, usuarioEnSesion());
        return kardex != null ? ResponseEntity.ok(kardex) : ResponseEntity.notFound().build();
    }

    // ============================================================
    // 10. REPORTE: Herramientas prestadas (aún no devueltas)
    // ============================================================
    @GetMapping("/herramientas/prestadas")
    public ResponseEntity<List<ReporteHerramientasPrestadasDTO>> getHerramientasPrestadas() {
        return ResponseEntity.ok(reporteService.herramientasPrestadas(usuarioEnSesion()));
    }

    // ============================================================
    // 11. REPORTE: Herramientas pendientes de devolución (vencidas)
    // ============================================================
    @GetMapping("/herramientas/pendientes-devolucion")
    public ResponseEntity<List<ReporteHerramientasPendientesDevolucionDTO>> getHerramientasPendientesDevolucion() {
        return ResponseEntity.ok(reporteService.herramientasPendientesDevolucion(usuarioEnSesion()));
    }

    // ============================================================
    // 12. REPORTE: Movimientos realizados por usuario
    // ============================================================
    @GetMapping("/movimientos/usuario/{usuarioId}")
    public ResponseEntity<ReporteMovimientosUsuarioDTO> getMovimientosPorUsuario(
            @PathVariable Integer usuarioId) {
        ReporteMovimientosUsuarioDTO dto =
                reporteService.movimientosPorUsuario(usuarioId, usuarioEnSesion());
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    // ============================================================
    // Utilidades
    // ============================================================

    /**
     * Usuario autenticado por JWT; {@code null} si no hay sesión
     * (la bitácora tolera el caso).
     */
    private Usuario usuarioEnSesion() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        return usuarioRepository.findByEmail(auth.getName()).orElse(null);
    }
}
