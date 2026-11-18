package com.sapam.inventario.reportes;

import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.AuditoriaSistemaRepository;
import com.sapam.inventario.repository.EntradaAlmacenRepository;
import com.sapam.inventario.repository.PrestamoHerramientaRepository;
import com.sapam.inventario.repository.SalidaAlmacenRepository;
import com.sapam.inventario.repository.UsuarioRepository;
import com.sapam.inventario.service.ProductoService;
import com.sapam.inventario.service.SalidaAlmacenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que los 13 reportes se generan con datos reales de la base de datos
 * y no con datos de ejemplo:
 *  - existencias, agotados y bajo stock mínimo se calculan desde productos
 *  - el kardex del producto refleja los movimientos registrados
 *  - los reportes de módulos sin datos no muestran nada que no esté en la base
 *  - generar un reporte queda asentado en la bitácora
 *
 * Está en el paquete {@code reportes} porque algunos DTO de detalle son
 * internos al módulo y el test necesita leer sus campos.
 */
@SpringBootTest
class ReportesTest {

    @Autowired private ReporteService reporteService;
    @Autowired private ProductoService productoService;
    @Autowired private SalidaAlmacenService salidaAlmacenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AuditoriaSistemaRepository auditoriaRepository;
    @Autowired private SalidaAlmacenRepository salidaAlmacenRepository;
    @Autowired private EntradaAlmacenRepository entradaAlmacenRepository;
    @Autowired private PrestamoHerramientaRepository prestamoHerramientaRepository;

    private Usuario usuarioDePrueba() {
        return usuarioRepository.findAll().stream().findFirst().orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre("Tester Reportes");
            u.setEmail("reportes@prueba.com");
            u.setPassword("123");
            u.setRol(Usuario.Rol.admin);
            u.setActivo(true);
            return usuarioRepository.save(u);
        });
    }

    private Producto crearProducto(String prefijo, int existencia, int minimo) {
        Producto p = new Producto();
        p.setCodigoBarras(prefijo + "-" + System.nanoTime());
        p.setNombre("Producto " + prefijo);
        p.setExistenciaActual(existencia);
        p.setExistenciaMinima(minimo);
        return productoService.guardar(p);
    }

    @Test
    void existenciasActualesIncluyeElProductoCreado() {
        Producto creado = crearProducto("RPT-EXI", 12, 5);

        List<ReporteExistenciasActualesDTO> existencias =
                reporteService.existenciasActuales(usuarioDePrueba());

        assertTrue(existencias.stream().anyMatch(e -> e.getProductoId().equals(creado.getId().longValue())),
                "el reporte debe incluir el producto recién creado");
        assertTrue(existencias.stream().noneMatch(e -> "BAJAS".equals(e.getEstado())),
                "los productos dados de baja no deben aparecer en existencias");
    }

    @Test
    void reporteDeAgotadosSoloDevuelveExistenciaCero() {
        Producto agotado = crearProducto("RPT-AGO", 0, 5);
        Producto conStock = crearProducto("RPT-STK", 8, 5);

        List<ReporteProductosAgotadosDTO> agotados =
                reporteService.productosAgotados(usuarioDePrueba());

        assertTrue(agotados.stream().anyMatch(a -> a.getProductoId().equals(agotado.getId().longValue())),
                "un producto con existencia 0 debe salir en el reporte de agotados");
        assertTrue(agotados.stream().noneMatch(a -> a.getProductoId().equals(conStock.getId().longValue())),
                "un producto con existencia no debe salir en el reporte de agotados");
        assertTrue(agotados.stream().allMatch(a -> a.getExistenciaActual() == 0),
                "todos los registros del reporte deben tener existencia 0");
    }

    @Test
    void reporteDeStockMinimoExcluyeLosAgotados() {
        Producto bajo = crearProducto("RPT-MIN", 3, 5);     // 3 <= 5 -> sí
        Producto agotado = crearProducto("RPT-MIN0", 0, 5); // agotado -> no (va al otro reporte)

        List<ReporteProductosStockMinimoDTO> bajoStock =
                reporteService.productosStockMinimo(usuarioDePrueba());

        assertTrue(bajoStock.stream().anyMatch(b -> b.getProductoId().equals(bajo.getId().longValue())),
                "un producto por debajo del mínimo debe aparecer");
        assertTrue(bajoStock.stream().noneMatch(b -> b.getProductoId().equals(agotado.getId().longValue())),
                "los agotados no deben duplicarse en este reporte");
        assertTrue(bajoStock.stream().allMatch(b -> b.getPorcentajeStock() != null
                        && b.getPorcentajeStock() <= 100.0),
                "el porcentaje de stock no debe superar 100");
    }

    @Test
    void kardexDelProductoReflejaElMovimientoReal() {
        Usuario usuario = usuarioDePrueba();
        Producto p = crearProducto("RPT-KDX", 10, 2);

        salidaAlmacenService.registrar(p.getId(), 4, usuario.getId(), "Ana Torres",
                "Prueba reporte", null, "Operaciones", "OTRO", "test");

        ReporteKardexProductoDTO kardex = reporteService.kardexProducto(p.getId().longValue(), usuario);

        assertNotNull(kardex, "el producto existe, el reporte no puede ser null");
        assertEquals(p.getNombre(), kardex.getNombreProducto());
        assertEquals(1, kardex.getMovimientos().size(),
                "el reporte debe mostrar el movimiento recién registrado");
        assertEquals(10, kardex.getMovimientos().get(0).getExistenciaAnterior());
        assertEquals(6, kardex.getMovimientos().get(0).getExistenciaPosterior());
        assertEquals("SALIDA", kardex.getMovimientos().get(0).getTipoMovimiento());
    }

    @Test
    void reporteDeProductoInexistenteDevuelveNull() {
        assertNull(reporteService.kardexProducto(999999L, usuarioDePrueba()));
        assertNull(reporteService.movimientosPorUsuario(999999, usuarioDePrueba()));
    }

    @Test
    void losReportesDeModulosSinDatosNoInventanInformacion() {
        Usuario usuario = usuarioDePrueba();

        // Antes exigíamos que estos dos vieran vacío: los módulos no tenían
        // pantalla y no se podía dar nada de alta. Ahora HerramientaTest da de
        // alta de verdad, así que se compara con la tabla, igual que con las
        // entradas y las salidas: el reporte no debe mostrar una fila más de la
        // que consta en la base.
        assertEquals(
                prestamoHerramientaRepository
                        .findByFechaDevolucionIsNullOrderByFechaEstimadaDevolucionAsc().size(),
                reporteService.herramientasPrestadas(usuario).get(0).getPrestamosActivos().size(),
                "el reporte de herramientas prestadas solo debe devolver lo que hay en la tabla");

        // Las entradas y las salidas se comparan con la tabla en lugar de exigir
        // que estén vacías: otros tests crean registros reales y este test debe
        // seguir valiendo en cualquier orden. Lo que se comprueba es que el
        // reporte no muestre una fila más de la que consta en la base.
        assertEquals(entradaAlmacenRepository.count(),
                reporteService.entradasPorPeriodo(null, null, usuario).size(),
                "el reporte de entradas solo debe devolver lo que hay en la tabla");
        assertEquals(salidaAlmacenRepository.count(),
                reporteService.salidasPorPeriodo(null, null, usuario).size(),
                "el reporte de salidas solo debe devolver lo que hay en la tabla");
    }

    @Test
    void elReporteDeSalidasEntregaElDetalleDeCadaLinea() {
        Usuario usuario = usuarioDePrueba();
        Producto producto = crearProducto("RPT-SAL", 10, 2);

        var salida = salidaAlmacenService.registrar(
                producto.getId(), 3, usuario.getId(), "Ana Torres",
                "Carlos López", "Técnico", "Almacén", "MANTENIMIENTO_PREVENTIVO", null);

        ReporteSalidasPeriodoDTO fila = reporteService.salidasPorPeriodo(null, null, usuario).stream()
                .filter(r -> salida.getFolio().equals(r.getFolio()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("la salida no aparece en el reporte"));

        assertEquals("MANTENIMIENTO_PREVENTIVO", fila.getMotivoSalida());
        assertEquals("Carlos López", fila.getPersonaSolicita());
        assertEquals("Técnico", fila.getPuestoSolicita());
        assertEquals("Almacén", fila.getDepartamentoSolicita());

        assertEquals(1, fila.getProductos().size(), "el reporte debe detallar los productos entregados");
        assertEquals(producto.getCodigoBarras(), fila.getProductos().get(0).getCodigoBarras());
        assertEquals(3, fila.getProductos().get(0).getCantidadEntregada());
        assertNotNull(fila.getProductos().get(0).getNombreReceptor(),
                "el detalle debe indicar quién recibió");
        assertEquals("Ana Torres", fila.getProductos().get(0).getNombreReceptor(),
                "el receptor es el texto escrito, no una cuenta del sistema");
        // "Ana Torres" no existe en la tabla usuarios: quien recibe el material
        // no tiene por qué tener acceso para entrar al sistema.
    }

    @Test
    void losReportesDeMaterialesSeCalculanDeLasSalidas() {
        // No hay directorio de trabajadores: el criterio es el nombre tal como
        // quedó escrito en la salida. Así sirve para cualquiera, aunque su
        // nombre solo se haya tecleado a mano en el formulario.
        Usuario usuario = usuarioDePrueba();
        Producto producto = crearProducto("RPT-MAT", 20, 2);

        salidaAlmacenService.registrar(producto.getId(), 2, usuario.getId(), "Ana Torres",
                "Juan Pérez Escrito A Mano", "Operario", "Mantenimiento",
                "MANTENIMIENTO_CORRECTIVO", null);

        // La búsqueda distingue acentos pero no mayúsculas: sirve tanto si se
        // elige del selector como si se escribe con otras mayúsculas.
        List<ReporteMaterialesTrabajadorDTO> porTrabajador =
                reporteService.materialesPorTrabajador("JUAN PÉREZ ESCRITO A MANO", usuario);
        assertEquals(1, porTrabajador.size());
        assertEquals("Juan Pérez Escrito A Mano", porTrabajador.get(0).getNombreTrabajador(),
                "debe devolver el nombre tal como quedó escrito en la salida");
        assertEquals("Operario", porTrabajador.get(0).getCargoTrabajador());
        assertEquals(1L, porTrabajador.get(0).getTotalMovimientos());
        assertNotNull(porTrabajador.get(0).getFechaUltimoMovimiento());
        assertEquals(1, porTrabajador.get(0).getMaterialesEntregados().size());
        assertEquals(producto.getNombre(),
                porTrabajador.get(0).getMaterialesEntregados().get(0).getNombreProducto());

        List<ReporteMaterialesDepartamentoDTO> porDepto =
                reporteService.materialesPorDepartamento("Mantenimiento", usuario);
        assertEquals(1, porDepto.size());
        assertEquals("Mantenimiento", porDepto.get(0).getNombreDepartamento());
        assertEquals(1L, porDepto.get(0).getTotalMovimientos());
        assertEquals(1, porDepto.get(0).getMaterialesEntregados().size());
    }

    @Test
    void reporteDeTrabajadorODepartamentoSinSalidasNoFalla() {
        // Quien no ha solicitado nada no aparece, y eso no debe romper el
        // reporte ni su exportación: devuelve lista vacía.
        assertTrue(reporteService.materialesPorTrabajador("Nadie Solicito Nada", usuarioDePrueba())
                .isEmpty());
        assertTrue(reporteService.materialesPorDepartamento("Departamento Inexistente",
                usuarioDePrueba()).isEmpty());
        assertTrue(reporteService.materialesPorTrabajador("   ", usuarioDePrueba()).isEmpty());
        assertTrue(reporteService.materialesPorDepartamento(null, usuarioDePrueba()).isEmpty());
    }

    @Test
    void generarReporteQuedaEnLaBitacora() {
        Usuario usuario = usuarioDePrueba();
        long previos = auditoriaRepository.findTop200ByAccionOrderByFechaDescHoraDesc("GENERAR_REPORTE").size();

        reporteService.existenciasActuales(usuario);

        long despues = auditoriaRepository.findTop200ByAccionOrderByFechaDescHoraDesc("GENERAR_REPORTE").size();
        assertTrue(despues > previos,
                "la bitácora debe registrar la generación de reportes");
    }
}
