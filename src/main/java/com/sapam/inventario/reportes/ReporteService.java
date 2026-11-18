package com.sapam.inventario.reportes;

import com.sapam.inventario.entity.*;
import com.sapam.inventario.reportes.*;
import com.sapam.inventario.service.BitacoraService;
import com.sapam.inventario.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Genera los 13 reportes del SGIA a partir de la base de datos real.
 *
 * Es el único lugar donde se arman los datos: lo usan tanto la API REST
 * ({@code ReportController}) como la aplicación de escritorio (JavaFX), para
 * que ambos entreguen exactamente lo mismo.
 *
 * Los reportes que dependen de módulos aún no implementados (entradas de
 * almacén, salidas con folio, préstamos de herramientas) devuelven
 * lista vacía cuando no hay registros, que es el comportamiento correcto.
 */
@Service
public class ReporteService {

    private final ProductoRepository productoRepository;
    private final KardexRepository kardexRepository;
    private final MovimientoRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EntradaAlmacenRepository entradaAlmacenRepository;
    private final SalidaAlmacenRepository salidaAlmacenRepository;
    private final ProductoEntradaRepository productoEntradaRepository;
    private final ProductoSalidaRepository productoSalidaRepository;
    private final PrestamoHerramientaRepository prestamoHerramientaRepository;
    private final BitacoraService bitacoraService;

    public ReporteService(ProductoRepository productoRepository,
                          KardexRepository kardexRepository,
                          MovimientoRepository movimientoRepository,
                          UsuarioRepository usuarioRepository,
                          EntradaAlmacenRepository entradaAlmacenRepository,
                          SalidaAlmacenRepository salidaAlmacenRepository,
                          ProductoEntradaRepository productoEntradaRepository,
                          ProductoSalidaRepository productoSalidaRepository,
                          PrestamoHerramientaRepository prestamoHerramientaRepository,
                          BitacoraService bitacoraService) {
        this.productoRepository = productoRepository;
        this.kardexRepository = kardexRepository;
        this.movimientoRepository = movimientoRepository;
        this.usuarioRepository = usuarioRepository;
        this.entradaAlmacenRepository = entradaAlmacenRepository;
        this.salidaAlmacenRepository = salidaAlmacenRepository;
        this.productoEntradaRepository = productoEntradaRepository;
        this.productoSalidaRepository = productoSalidaRepository;
        this.prestamoHerramientaRepository = prestamoHerramientaRepository;
        this.bitacoraService = bitacoraService;
    }

    // ============================================================
    // 1. REPORTE: Existencias Actuales
    // ============================================================
    @Transactional(readOnly = true)
    public List<ReporteExistenciasActualesDTO> existenciasActuales(Usuario usuario) {
        List<ReporteExistenciasActualesDTO> existencias = new ArrayList<>();
        for (Producto p : productoRepository.findActivos()) {
            ReporteExistenciasActualesDTO dto = new ReporteExistenciasActualesDTO();
            dto.setProductoId(p.getId().longValue());
            dto.setCodigoBarras(p.getCodigoBarras());
            dto.setNombre(p.getNombre());
            dto.setSeccion(p.getSeccion());
            dto.setMarca(p.getMarca());
            dto.setModelo(p.getModelo());
            dto.setUnidadMedida(p.getUnidadMedida());
            dto.setExistenciaActual(p.getExistenciaActual());
            dto.setExistenciaMinima(p.getExistenciaMinima());
            dto.setExistenciaMaxima(p.getExistenciaMaxima());
            dto.setUbicacionFisica(p.getUbicacionFisica());
            dto.setEstado(p.getEstado());
            dto.setCodigoInterno(p.getCodigoInterno());
            existencias.add(dto);
        }
        registrarGeneracion("Existencias actuales", existencias.size(), usuario);
        return existencias;
    }

    // ============================================================
    // 2. REPORTE: Productos Agotados
    // ============================================================
    @Transactional(readOnly = true)
    public List<ReporteProductosAgotadosDTO> productosAgotados(Usuario usuario) {
        List<ReporteProductosAgotadosDTO> agotados = new ArrayList<>();
        for (Producto p : productoRepository.findAgotados()) {
            ReporteProductosAgotadosDTO dto = new ReporteProductosAgotadosDTO();
            dto.setProductoId(p.getId().longValue());
            dto.setCodigoBarras(p.getCodigoBarras());
            dto.setNombre(p.getNombre());
            dto.setSeccion(p.getSeccion());
            dto.setMarca(p.getMarca());
            dto.setExistenciaActual(p.getExistenciaActual());
            dto.setMotivo("AGOTADO");
            dto.setCodigoInterno(p.getCodigoInterno());
            agotados.add(dto);
        }
        registrarGeneracion("Productos agotados", agotados.size(), usuario);
        return agotados;
    }

    // ============================================================
    // 3. REPORTE: Productos por debajo del stock mínimo
    // ============================================================
    @Transactional(readOnly = true)
    public List<ReporteProductosStockMinimoDTO> productosStockMinimo(Usuario usuario) {
        List<ReporteProductosStockMinimoDTO> bajoStock = new ArrayList<>();
        for (Producto p : productoRepository.findBajoStockMinimo()) {
            int minima = (p.getExistenciaMinima() != null && p.getExistenciaMinima() > 0)
                    ? p.getExistenciaMinima() : 1;
            double porcentaje = Math.round(p.getExistenciaActual() * 1000.0 / minima) / 10.0;

            ReporteProductosStockMinimoDTO dto = new ReporteProductosStockMinimoDTO();
            dto.setProductoId(p.getId().longValue());
            dto.setCodigoBarras(p.getCodigoBarras());
            dto.setNombre(p.getNombre());
            dto.setSeccion(p.getSeccion());
            dto.setExistenciaActual(p.getExistenciaActual());
            dto.setExistenciaMinima(p.getExistenciaMinima());
            dto.setPorcentajeStock(porcentaje);
            dto.setCodigoInterno(p.getCodigoInterno());
            bajoStock.add(dto);
        }
        registrarGeneracion("Productos bajo stock mínimo", bajoStock.size(), usuario);
        return bajoStock;
    }

    // ============================================================
    // 4. REPORTE: Entradas por periodo
    // ============================================================
    @Transactional(readOnly = true)
    public List<ReporteEntradasPeriodoDTO> entradasPorPeriodo(LocalDate fechaInicio,
                                                              LocalDate fechaFin,
                                                              Usuario usuario) {
        LocalDate inicio = fechaInicio != null ? fechaInicio : LocalDate.now().minusMonths(1);
        LocalDate fin = fechaFin != null ? fechaFin : LocalDate.now();

        List<ReporteEntradasPeriodoDTO> entradas = new ArrayList<>();
        for (EntradaAlmacen e : entradaAlmacenRepository.findByFechaBetweenOrderByFechaDescHoraDesc(inicio, fin)) {
            ReporteEntradasPeriodoDTO dto = new ReporteEntradasPeriodoDTO();
            dto.setFolio(e.getFolio());
            dto.setFecha(e.getFecha());
            dto.setHora(e.getHora());
            dto.setUsuarioRegistro(e.getUsuarioRegistro() != null ? e.getUsuarioRegistro().getEmail() : null);
            dto.setObservaciones(e.getObservaciones());
            dto.setProductos(productosDeEntrada(e.getId()));
            entradas.add(dto);
        }
        registrarGeneracion("Entradas por periodo (" + inicio + " a " + fin + ")",
                entradas.size(), usuario);
        return entradas;
    }

    private List<ReporteEntradaProductoDTO> productosDeEntrada(Long entradaId) {
        List<ReporteEntradaProductoDTO> productos = new ArrayList<>();
        for (ProductoEntrada pe : productoEntradaRepository.findByEntradaAlmacenId(entradaId)) {
            Producto p = pe.getProducto();
            ReporteEntradaProductoDTO dto = new ReporteEntradaProductoDTO();
            dto.setCodigoBarras(p.getCodigoBarras());
            dto.setNombreProducto(p.getNombre());
            dto.setDescripcion(p.getDescripcion());
            dto.setCantidadIngresada(pe.getCantidad());
            dto.setUnidadMedida(p.getUnidadMedida());
            dto.setCodigoInterno(p.getCodigoInterno());
            productos.add(dto);
        }
        return productos;
    }

    // ============================================================
    // 5. REPORTE: Salidas por periodo
    // ============================================================
    @Transactional(readOnly = true)
    public List<ReporteSalidasPeriodoDTO> salidasPorPeriodo(LocalDate fechaInicio,
                                                            LocalDate fechaFin,
                                                            Usuario usuario) {
        LocalDate inicio = fechaInicio != null ? fechaInicio : LocalDate.now().minusMonths(1);
        LocalDate fin = fechaFin != null ? fechaFin : LocalDate.now();

        List<ReporteSalidasPeriodoDTO> salidas = new ArrayList<>();
        for (SalidaAlmacen s : salidaAlmacenRepository.findByFechaBetweenOrderByFechaDescHoraDesc(inicio, fin)) {
            ReporteSalidasPeriodoDTO dto = new ReporteSalidasPeriodoDTO();
            dto.setFolio(s.getFolio());
            dto.setFecha(s.getFecha());
            dto.setHora(s.getHora());
            dto.setPersonaSolicita(s.getPersonaSolicita());
            dto.setPuestoSolicita(s.getPuestoSolicita());
            dto.setDepartamentoSolicita(s.getDepartamentoSolicita());
            dto.setMotivoSalida(s.getMotivoSalida());
            dto.setUsuarioResponsable(s.getUsuarioRegistro() != null
                    ? s.getUsuarioRegistro().getNombreCompleto() : null);
            dto.setProductos(productosDeSalida(s));
            salidas.add(dto);
        }
        registrarGeneracion("Salidas por periodo (" + inicio + " a " + fin + ")",
                salidas.size(), usuario);
        return salidas;
    }

    private List<ReporteSalidaProductoDTO> productosDeSalida(SalidaAlmacen s) {
        List<ReporteSalidaProductoDTO> productos = new ArrayList<>();
        String nombreRecibe = s.getPersonaRecibe();
        for (ProductoSalida ps : productoSalidaRepository.findBySalidaAlmacenId(s.getId())) {
            Producto p = ps.getProducto();
            ReporteSalidaProductoDTO dto = new ReporteSalidaProductoDTO();
            dto.setCodigoBarras(p.getCodigoBarras());
            dto.setNombreProducto(p.getNombre());
            dto.setCantidadEntregada(ps.getCantidad());
            dto.setNombreReceptor(nombreRecibe);
            dto.setCargoReceptor(s.getPuestoSolicita());
            dto.setAreaReceptor(s.getDepartamentoSolicita());
            dto.setCodigoInterno(p.getCodigoInterno());
            productos.add(dto);
        }
        return productos;
    }

    // ============================================================
    // 6. REPORTE: Materiales entregados por trabajador
    // ============================================================
    /**
     * Lo que se le entregó a una persona concreta.
     *
     * No hay directorio de trabajadores: el criterio es el nombre tal y como
     * quedó escrito en cada salida, así que sirve tanto para quien tiene ficha
     * como para cualquiera escrito a mano en el formulario. Puesto y
     * departamento son los de la última salida registrada a ese nombre.
     */
    @Transactional(readOnly = true)
    public List<ReporteMaterialesTrabajadorDTO> materialesPorTrabajador(String persona,
                                                                        Usuario usuario) {
        List<ReporteMaterialesTrabajadorDTO> materiales = new ArrayList<>();
        if (persona == null || persona.isBlank()) {
            return materiales;
        }

        List<SalidaAlmacen> salidas = salidaAlmacenRepository
                .findByPersonaSolicitaIgnoreCaseOrderByFechaDescHoraDesc(persona.trim());
        if (salidas.isEmpty()) {
            return materiales;
        }

        SalidaAlmacen ultima = salidas.get(0);
        ReporteMaterialesTrabajadorDTO trabajador = new ReporteMaterialesTrabajadorDTO();
        trabajador.setNombreTrabajador(ultima.getPersonaSolicita());
        trabajador.setCargoTrabajador(ultima.getPuestoSolicita());
        trabajador.setDepartamentoTrabajador(ultima.getDepartamentoSolicita());
        trabajador.setTotalMovimientos((long) salidas.size());
        trabajador.setFechaUltimoMovimiento(ultima.getFecha());
        trabajador.setMaterialesEntregados(materialesDeSalidas(salidas));
        materiales.add(trabajador);

        registrarGeneracion("Materiales por trabajador " + persona, salidas.size(), usuario);
        return materiales;
    }

    // ============================================================
    // 7. REPORTE: Materiales entregados por departamento
    // ============================================================
    /**
     * Lo que ha salido de un área concreta. El criterio es el departamento que
     * cada salida registró, de modo que no exige mantener nada aparte.
     */
    @Transactional(readOnly = true)
    public List<ReporteMaterialesDepartamentoDTO> materialesPorDepartamento(String departamento,
                                                                            Usuario usuario) {
        List<ReporteMaterialesDepartamentoDTO> resultado = new ArrayList<>();
        if (departamento == null || departamento.isBlank()) {
            return resultado;
        }

        List<SalidaAlmacen> salidas = salidaAlmacenRepository
                .findByDepartamentoSolicitaIgnoreCaseOrderByFechaDescHoraDesc(departamento.trim());
        if (salidas.isEmpty()) {
            return resultado;
        }

        SalidaAlmacen ultima = salidas.get(0);
        ReporteMaterialesDepartamentoDTO dept = new ReporteMaterialesDepartamentoDTO();
        dept.setNombreDepartamento(ultima.getDepartamentoSolicita());
        dept.setAreaResponsable(ultima.getDepartamentoSolicita());
        dept.setTotalMovimientos((long) salidas.size());
        dept.setFechaUltimoMovimiento(ultima.getFecha());
        dept.setMaterialesEntregados(materialesDeSalidas(salidas));
        resultado.add(dept);

        registrarGeneracion("Materiales por departamento " + departamento, salidas.size(), usuario);
        return resultado;
    }

    /** Convierte salidas en la estructura de materiales entregados. */
    private List<ReporteMaterialEntregadoDTO> materialesDeSalidas(List<SalidaAlmacen> salidas) {
        List<ReporteMaterialEntregadoDTO> entregados = new ArrayList<>();
        for (SalidaAlmacen salida : salidas) {
            for (ProductoSalida ps : productoSalidaRepository.findBySalidaAlmacenId(salida.getId())) {
                Producto producto = ps.getProducto();
                ReporteMaterialEntregadoDTO dto = new ReporteMaterialEntregadoDTO();
                dto.setFolioSalida(salida.getFolio());
                dto.setFechaSalida(salida.getFecha());
                dto.setMotivoSalida(salida.getMotivoSalida());
                dto.setNombreProducto(producto.getNombre());
                dto.setCantidadEntregada(ps.getCantidad());
                dto.setCodigoBarras(producto.getCodigoBarras());
                entregados.add(dto);
            }
        }
        return entregados;
    }

    // ============================================================
    // 8. REPORTE: Historial de movimientos (kardex)
    // ============================================================
    @Transactional(readOnly = true)
    public List<ReporteHistorialMovimientosDTO> historialMovimientos(LocalDate fechaInicio,
                                                                     LocalDate fechaFin,
                                                                     Usuario usuario) {
        LocalDate inicio = fechaInicio != null ? fechaInicio : LocalDate.now().minusMonths(1);
        LocalDate fin = fechaFin != null ? fechaFin : LocalDate.now();

        List<ReporteHistorialMovimientosDTO> historial = new ArrayList<>();
        for (Kardex k : kardexRepository.findByFechaBetweenOrderByFechaDescHoraDesc(inicio, fin)) {
            ReporteHistorialMovimientosDTO dto = new ReporteHistorialMovimientosDTO();
            dto.setFechaMovimiento(k.getFecha());
            dto.setTipoMovimiento(k.getTipoMovimiento());
            dto.setFolio(k.getFolio());
            dto.setNombreProducto(k.getProducto() != null ? k.getProducto().getNombre() : null);
            dto.setCodigoBarras(k.getProducto() != null ? k.getProducto().getCodigoBarras() : null);
            dto.setCantidadMovida(k.getCantidadMovida());
            dto.setExistenciaAnterior(k.getExistenciaAnterior());
            dto.setExistenciaPosterior(k.getExistenciaPosterior());
            dto.setNombreUsuario(k.getUsuarioResponsable() != null ? k.getUsuarioResponsable().getEmail() : null);
            dto.setObservaciones(k.getObservaciones());
            dto.setCodigoInterno(k.getProducto() != null ? k.getProducto().getCodigoInterno() : null);
            historial.add(dto);
        }
        registrarGeneracion("Historial de movimientos", historial.size(), usuario);
        return historial;
    }

    // ============================================================
    // 9. REPORTE: Kardex de un producto
    // ============================================================
    /** Devuelve {@code null} cuando el producto no existe. */
    @Transactional(readOnly = true)
    public ReporteKardexProductoDTO kardexProducto(Long productoId, Usuario usuario) {
        Optional<Producto> productoOpt = productoRepository.findById(productoId.intValue());
        if (productoOpt.isEmpty()) {
            return null;
        }
        Producto p = productoOpt.get();

        ReporteKardexProductoDTO kardex = new ReporteKardexProductoDTO();
        kardex.setProductoId(p.getId().longValue());
        kardex.setCodigoBarras(p.getCodigoBarras());
        kardex.setNombreProducto(p.getNombre());
        kardex.setMarca(p.getMarca());
        kardex.setModelo(p.getModelo());
        kardex.setSeccion(p.getSeccion());

        List<ReporteMovimientoKardexDTO> movimientos = new ArrayList<>();
        for (Kardex k : kardexRepository.findByProductoIdOrderByFechaDescHoraDesc(p.getId())) {
            ReporteMovimientoKardexDTO dto = new ReporteMovimientoKardexDTO();
            dto.setFecha(k.getFecha());
            dto.setHora(k.getHora());
            dto.setTipoMovimiento(k.getTipoMovimiento());
            dto.setFolio(k.getFolio());
            dto.setCantidadMovida(k.getCantidadMovida());
            dto.setExistenciaAnterior(k.getExistenciaAnterior());
            dto.setExistenciaPosterior(k.getExistenciaPosterior());
            dto.setNombreUsuario(k.getUsuarioResponsable() != null ? k.getUsuarioResponsable().getEmail() : null);
            dto.setObservaciones(k.getObservaciones());
            dto.setCodigoInterno(p.getCodigoInterno());
            movimientos.add(dto);
        }
        kardex.setMovimientos(movimientos);

        registrarGeneracion("Kardex del producto " + p.getNombre(), movimientos.size(), usuario);
        return kardex;
    }

    // ============================================================
    // 10. REPORTE: Herramientas prestadas (aún no devueltas)
    // ============================================================
    @Transactional(readOnly = true)
    public List<ReporteHerramientasPrestadasDTO> herramientasPrestadas(Usuario usuario) {
        List<ReportePrestamoHerramientaDTO> prestamos = new ArrayList<>();
        for (PrestamoHerramienta ph
                : prestamoHerramientaRepository.findByFechaDevolucionIsNullOrderByFechaEstimadaDevolucionAsc()) {
            ReportePrestamoHerramientaDTO dto = new ReportePrestamoHerramientaDTO();
            dto.setFolio(ph.getFolio());
            dto.setNombreHerramienta(ph.getHerramienta() != null ? ph.getHerramienta().getNombre() : null);
            dto.setNumeroSerie(ph.getHerramienta() != null ? ph.getHerramienta().getNumeroSerie() : null);
            dto.setPersonaRecibe(ph.getPersonaRecibe());
            dto.setFechaEntrega(ph.getFechaEntrega() != null ? ph.getFechaEntrega().toLocalDate() : null);
            dto.setFechaEstimadaDevolucion(ph.getFechaEstimadaDevolucion());
            dto.setFechaDevolucion(ph.getFechaDevolucion());
            dto.setEstado(ph.getEstado());
            dto.setHoraEntrega(ph.getFechaEntrega() != null ? formatearHora(ph.getFechaEntrega().toLocalTime()) : null);
            dto.setCodigoBarras(ph.getHerramienta() != null ? ph.getHerramienta().getCodigoBarras() : null);
            prestamos.add(dto);
        }

        ReporteHerramientasPrestadasDTO dto = new ReporteHerramientasPrestadasDTO();
        dto.setPrestamosActivos(prestamos);

        registrarGeneracion("Herramientas prestadas", prestamos.size(), usuario);
        return List.of(dto);
    }

    // ============================================================
    // 11. REPORTE: Herramientas pendientes de devolución (vencidas)
    // ============================================================
    @Transactional(readOnly = true)
    public List<ReporteHerramientasPendientesDevolucionDTO> herramientasPendientesDevolucion(Usuario usuario) {
        LocalDate hoy = LocalDate.now();
        List<ReportePrestamoPendienteDTO> prestamos = new ArrayList<>();
        for (PrestamoHerramienta ph : prestamoHerramientaRepository
                .findByFechaDevolucionIsNullAndFechaEstimadaDevolucionBeforeOrderByFechaEstimadaDevolucionAsc(hoy)) {

            ReportePrestamoPendienteDTO dto = new ReportePrestamoPendienteDTO();
            dto.setFolio(ph.getFolio());
            dto.setNombreHerramienta(ph.getHerramienta() != null ? ph.getHerramienta().getNombre() : null);
            dto.setNumeroSerie(ph.getHerramienta() != null ? ph.getHerramienta().getNumeroSerie() : null);
            dto.setPersonaRecibe(ph.getPersonaRecibe());
            dto.setFechaEntrega(ph.getFechaEntrega() != null ? ph.getFechaEntrega().toLocalDate() : null);
            dto.setFechaEstimadaDevolucion(ph.getFechaEstimadaDevolucion());
            dto.setFechaVencimiento(ph.getFechaEstimadaDevolucion());
            dto.setDiasVencido(ChronoUnit.DAYS.between(ph.getFechaEstimadaDevolucion(), hoy));
            dto.setCodigoBarras(ph.getHerramienta() != null ? ph.getHerramienta().getCodigoBarras() : null);
            prestamos.add(dto);
        }

        ReporteHerramientasPendientesDevolucionDTO dto = new ReporteHerramientasPendientesDevolucionDTO();
        dto.setPrestamosPendientes(prestamos);

        registrarGeneracion("Herramientas pendientes de devolución", prestamos.size(), usuario);
        return List.of(dto);
    }

    // ============================================================
    // 12. REPORTE: Movimientos realizados por usuario
    // ============================================================
    /** Devuelve {@code null} cuando el usuario no existe. */
    @Transactional(readOnly = true)
    public ReporteMovimientosUsuarioDTO movimientosPorUsuario(Integer usuarioId, Usuario usuario) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findById(usuarioId);
        if (usuarioOpt.isEmpty()) {
            return null;
        }
        Usuario u = usuarioOpt.get();
        List<Movimiento> movimientos = movimientoRepository.findByUsuarioIdOrderByFechaDesc(usuarioId);

        ReporteMovimientosUsuarioDTO dto = new ReporteMovimientosUsuarioDTO();
        dto.setNombreUsuario(u.getNombre());
        dto.setEmailUsuario(u.getEmail());
        dto.setTotalMovimientos((long) movimientos.size());

        if (!movimientos.isEmpty()) {
            dto.setFechaUltimoMovimiento(fechaDe(movimientos, 0));
            dto.setFechaPrimerMovimiento(fechaDe(movimientos, movimientos.size() - 1));
        }

        // Resumen agrupado por tipo de movimiento
        Map<String, Long> porTipo = new LinkedHashMap<>();
        for (Movimiento m : movimientos) {
            porTipo.merge(m.getTipo().name().toUpperCase(), 1L, Long::sum);
        }
        List<ReporteMovimientoUsuarioResumenDTO> resumen = new ArrayList<>();
        for (Map.Entry<String, Long> e : porTipo.entrySet()) {
            ReporteMovimientoUsuarioResumenDTO fila = new ReporteMovimientoUsuarioResumenDTO();
            fila.setTipoMovimiento(e.getKey());
            fila.setCantidad(e.getValue());
            fila.setFecha(LocalDate.now());
            fila.setModulo("INVENTARIO");
            resumen.add(fila);
        }
        dto.setMovimientos(resumen);

        registrarGeneracion("Movimientos del usuario " + u.getEmail(), movimientos.size(), usuario);
        return dto;
    }

    // ============================================================
    // Utilidades
    // ============================================================

    /**
     * Fecha del movimiento en la posición dada, o {@code null} si la lista está
     * vacía (por ejemplo, un trabajador que todavía no ha recibido material).
     */
    private LocalDate fechaDe(List<Movimiento> movimientos, int posicion) {
        if (movimientos == null || posicion < 0 || posicion >= movimientos.size()) {
            return null;
        }
        Movimiento m = movimientos.get(posicion);
        return m != null && m.getFecha() != null ? m.getFecha().toLocalDate() : null;
    }

    /** HH:mm:ss -> HH:mm (el DTO reporta solo horas y minutos). */
    private String formatearHora(LocalTime hora) {
        return String.format("%02d:%02d", hora.getHour(), hora.getMinute());
    }

    /**
     * Deja constancia en la bitácora de que se generó un reporte
     * (requisito: la bitácora debe registrar la generación de reportes).
     * Nunca debe romper el reporte si la auditoría falla.
     */
    private void registrarGeneracion(String nombreReporte, int registros, Usuario usuario) {
        try {
            bitacoraService.registrar(BitacoraService.GENERAR_REPORTE, "REPORTES",
                    "Reporte: " + nombreReporte + " | Registros: " + registros,
                    "Generado desde la aplicación", usuario);
        } catch (Exception ignored) {
            // La auditoría nunca debe impedir entregar el reporte
        }
    }
}
