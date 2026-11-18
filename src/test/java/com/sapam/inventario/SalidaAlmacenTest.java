package com.sapam.inventario;

import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.entity.SalidaAlmacen;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.reportes.ReporteService;
import com.sapam.inventario.reportes.ReporteSalidasPeriodoDTO;
import com.sapam.inventario.repository.AuditoriaSistemaRepository;
import com.sapam.inventario.repository.KardexRepository;
import com.sapam.inventario.repository.MovimientoRepository;
import com.sapam.inventario.repository.ProductoRepository;
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
 * Salidas de almacén.
 *
 * Antes el botón "Salida" solo descontaba existencias y escribía el kardex; no
 * dejaba registro en {@code salidas_almacen}, que es la tabla que lee el
 * reporte "salidas por periodo", así que una salida registrada no aparecía en
 * el reporte. Aquí se comprueba que un solo camino deja todas las trazas.
 */
@SpringBootTest
class SalidaAlmacenTest {

    @Autowired private SalidaAlmacenService salidaAlmacenService;
    @Autowired private ProductoService productoService;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private SalidaAlmacenRepository salidaAlmacenRepository;
    @Autowired private MovimientoRepository movimientoRepository;
    @Autowired private KardexRepository kardexRepository;
    @Autowired private AuditoriaSistemaRepository auditoriaRepository;
    @Autowired private ReporteService reporteService;

    private Usuario usuarioDePrueba() {
        return usuarioRepository.findAll().stream().findFirst().orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre("Tester Salidas");
            u.setEmail("salidas@prueba.com");
            u.setPassword("123");
            u.setRol(Usuario.Rol.usuario);
            u.setActivo(true);
            return usuarioRepository.save(u);
        });
    }

    private Producto productoCon(int existencia) {
        Producto p = new Producto();
        p.setCodigoBarras("TEST-SAL-" + System.nanoTime());
        p.setNombre("Producto prueba salida");
        p.setExistenciaActual(existencia);
        return productoService.guardar(p);
    }

    @Test
    void quienRecibeTambienDejaSuPuestoYSuArea() {
        Usuario usuario = usuarioDePrueba();
        Producto producto = productoCon(10);

        SalidaAlmacen salida = salidaAlmacenService.registrar(
                producto.getId(), 2, usuario.getId(), "Ana Torres",
                "Técnico de campo", "Mantenimiento",
                "Carlos López", "Jefe de turno", "Almacén",
                "MANTENIMIENTO_PREVENTIVO", null);

        assertEquals("Ana Torres", salida.getPersonaRecibe());
        assertEquals("Técnico de campo", salida.getPuestoRecibe(),
                "la especificación pide también el cargo de quien recibe");
        assertEquals("Mantenimiento", salida.getDepartamentoRecibe(),
                "y su área de adscripción");

        // Sin esos datos la salida sigue valiendo: son opcionales, porque el
        // material que retira alguien de fuera no tiene área dentro.
        SalidaAlmacen sinDatos = salidaAlmacenService.registrar(
                producto.getId(), 1, usuario.getId(), "Roberto Mantenimiento",
                "Carlos López", "Jefe de turno", "Almacén", "OTRO", null);
        assertNull(sinDatos.getPuestoRecibe());
        assertNull(sinDatos.getDepartamentoRecibe());
    }

    @Test
    void laSalidaDejaTrazasEnTodosLosRegistros() {
        Usuario usuario = usuarioDePrueba();
        Producto producto = productoCon(10);

        long movimientosAntes = movimientoRepository.count();
        long kardexAntes = kardexRepository.count();
        long bitacoraAntes = auditoriaRepository.count();

        SalidaAlmacen salida = salidaAlmacenService.registrar(
                producto.getId(), 4, usuario.getId(), "Ana Torres",
                "Carlos López", "Técnico", "Almacén", "REPARACION", "Cambio de boquilla");

        // Existencia descontada
        assertEquals(6, productoRepository.findById(producto.getId()).get().getExistenciaActual(),
                "la existencia debe descontarse");

        // Salida formal con folio y motivo
        assertNotNull(salida.getFolio());
        assertTrue(salida.getFolio().startsWith("SAL-"), "el folio debe seguir el formato SAL-");
        assertEquals("REPARACION", salida.getMotivoSalida());
        assertEquals("Carlos López", salida.getPersonaSolicita());
        assertEquals("Ana Torres", salida.getPersonaRecibe(),
                "quien recibe debe guardarse como texto, no como usuario del sistema");
        assertEquals(1, salida.getProductosEntregados().size(), "la salida debe llevar su línea de producto");
        assertEquals(4, salida.getProductosEntregados().iterator().next().getCantidad());

        // Movimiento (alimenta los reportes de materiales y de movimientos)
        assertEquals(movimientosAntes + 1, movimientoRepository.count(),
                "la salida debe crear su movimiento");

        // Kardex
        assertEquals(kardexAntes + 1, kardexRepository.count(), "la salida debe crear su línea de kardex");
        var kardex = kardexRepository.findByProductoIdOrderByFechaDescHoraDesc(producto.getId());
        assertEquals("SALIDA", kardex.get(0).getTipoMovimiento());
        assertEquals(10, kardex.get(0).getExistenciaAnterior());
        assertEquals(6, kardex.get(0).getExistenciaPosterior());
        assertEquals(salida.getFolio(), kardex.get(0).getFolio(),
                "kardex y salida deben compartir folio");

        // Bitácora
        assertTrue(auditoriaRepository.count() > bitacoraAntes, "la salida debe quedar en la bitácora");

        // Y sobre todo: aparece en el reporte de salidas por periodo
        assertTrue(reporteService.salidasPorPeriodo(null, null, usuario).stream()
                        .anyMatch(r -> salida.getFolio().equals(r.getFolio())),
                "la salida debe aparecer en el reporte de salidas por periodo");
    }

    @Test
    void unaSalidaSinStockSuficienteNoDejaNingunRastro() {
        Usuario usuario = usuarioDePrueba();
        Producto producto = productoCon(2);

        long salidasAntes = salidaAlmacenRepository.count();
        long kardexAntes = kardexRepository.count();
        long movimientosAntes = movimientoRepository.count();
        long bitacoraAntes = auditoriaRepository.count();

        var excepcion = assertThrows(IllegalArgumentException.class, () ->
                salidaAlmacenService.registrar(producto.getId(), 5, usuario.getId(), "Ana Torres",
                        "Carlos López", null, "Almacén", "OTRO", null));

        assertTrue(excepcion.getMessage().contains("Stock insuficiente"), excepcion.getMessage());
        assertEquals(2, productoRepository.findById(producto.getId()).get().getExistenciaActual(),
                "el stock no debe tocarse");
        assertEquals(salidasAntes, salidaAlmacenRepository.count(), "no debe crearse la salida");
        assertEquals(kardexAntes, kardexRepository.count(), "no debe escribirse kardex");
        assertEquals(movimientosAntes, movimientoRepository.count(), "no debe crearse el movimiento");
        assertEquals(bitacoraAntes, auditoriaRepository.count(), "no debe registrarse en la bitácora");
    }

    @Test
    void elMotivoDebeEstarEntreLosAdmitidos() {
        Usuario usuario = usuarioDePrueba();
        Producto producto = productoCon(5);

        var excepcion = assertThrows(IllegalArgumentException.class, () ->
                salidaAlmacenService.registrar(producto.getId(), 1, usuario.getId(), "Ana Torres",
                        "Carlos López", null, "Almacén", "PARA_LLEVAR_A_CASA", null));

        assertTrue(excepcion.getMessage().contains("Motivo no válido"), excepcion.getMessage());
        assertTrue(excepcion.getMessage().contains("MANTENIMIENTO_PREVENTIVO"),
                "el error debe indicar los motivos admitidos");
        assertEquals(5, productoRepository.findById(producto.getId()).get().getExistenciaActual(),
                "un motivo inválido no debe descontar existencias");
    }

    @Test
    void elReporteDeSalidasMuestraAlResponsableDeLaSesion() {
        // El reporte no tenía ninguna columna con quién procesó la salida: se iba
        // el material y no constaba en el papel quién estaba al mando. Ahora
        // "Responsable" sale de usuario_registro, que es la sesión con la que se
        // registró, y no de un campo que alguien pudiera rellenar con otro nombre.
        Usuario usuario = usuarioDePrueba();
        Producto producto = productoCon(10);

        SalidaAlmacen salida = salidaAlmacenService.registrar(
                producto.getId(), 2, usuario.getId(), "Ana Torres",
                "Carlos López", null, "Almacén", "INSTALACION", null);

        ReporteSalidasPeriodoDTO fila = reporteService.salidasPorPeriodo(null, null, usuario).stream()
                .filter(r -> salida.getFolio().equals(r.getFolio()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("la salida no aparece en el reporte"));

        assertNotNull(fila.getUsuarioResponsable(), "la columna Responsable no debe salir en blanco");
        assertEquals(usuario.getNombreCompleto(), fila.getUsuarioResponsable(),
                "el responsable debe ser la cuenta con la que se registró, sin poder cambiarlo");
    }

    @Test
    void sinQuienSolicitaNoSeRegistraLaSalida() {
        Usuario usuario = usuarioDePrueba();
        Producto producto = productoCon(5);

        var excepcion = assertThrows(IllegalArgumentException.class, () ->
                salidaAlmacenService.registrar(producto.getId(), 1, usuario.getId(), "Ana Torres",
                        "   ", null, null, "OTRO", null));

        assertTrue(excepcion.getMessage().contains("quién solicita"), excepcion.getMessage());
        assertEquals(5, productoRepository.findById(producto.getId()).get().getExistenciaActual());
    }

    @Test
    void sinQuienRecibeNoSeRegistraLaSalida() {
        // "Quién recibe" es el "quién" de la especificación: sin él la salida
        // no queda trazable, igual que sin quien solicita.
        Usuario usuario = usuarioDePrueba();
        Producto producto = productoCon(5);

        var excepcion = assertThrows(IllegalArgumentException.class, () ->
                salidaAlmacenService.registrar(producto.getId(), 1, usuario.getId(), "   ",
                        "Carlos López", null, "Almacén", "OTRO", null));

        assertTrue(excepcion.getMessage().contains("quién recibe"), excepcion.getMessage());
        assertEquals(5, productoRepository.findById(producto.getId()).get().getExistenciaActual(),
                "un receptor ausente no debe descontar existencias");
    }

    @Test
    void quienRecibePuedeSerCualquieraAunqueNoTengaUsuario() {
        // Antes receptor era una cuenta del sistema y solo se podía elegir entre
        // las pocas que tienen acceso: el trabajador que se lleva el material
        // quedaba fuera del registro. Ahora basta con escribir su nombre.
        Usuario usuario = usuarioDePrueba();
        Producto producto = productoCon(10);

        SalidaAlmacen salida = salidaAlmacenService.registrar(
                producto.getId(), 2, usuario.getId(), "Trabajador Sin Acceso",
                "Otra Persona", null, "Almacén", "INSTALACION", null);

        assertEquals("Trabajador Sin Acceso", salida.getPersonaRecibe());
        assertNotEquals(salida.getPersonaRecibe(), salida.getPersonaSolicita(),
                "recibe y solicita deben quedar como dos personas distintas");
        // Que el nombre quede en la salida basta: el pinta del reporte lo
        // comprueba ReportesTest, que sí está en el paquete de los DTO.
    }

    @Test
    void sinDepartamentoNoSeRegistraLaSalida() {
        // El departamento alimenta el reporte "materiales entregados a un
        // departamento": si no se indica, esa salida quedaría fuera de él.
        Usuario usuario = usuarioDePrueba();
        Producto producto = productoCon(5);

        var excepcion = assertThrows(IllegalArgumentException.class, () ->
                salidaAlmacenService.registrar(producto.getId(), 1, usuario.getId(), "Ana Torres",
                        "Carlos López", null, "   ", "OTRO", null));

        assertTrue(excepcion.getMessage().contains("departamento"), excepcion.getMessage());
        assertEquals(5, productoRepository.findById(producto.getId()).get().getExistenciaActual(),
                "un departamento ausente no debe descontar existencias");
    }

    @Test
    void losSeisMotivosDeLaEspecificacionEstanAdmitidos() {
        assertEquals(6, SalidaAlmacenService.MOTIVOS.size());
        assertTrue(SalidaAlmacenService.MOTIVOS.containsAll(java.util.List.of(
                "REPARACION", "MANTENIMIENTO_PREVENTIVO", "MANTENIMIENTO_CORRECTIVO",
                "INSTALACION", "ADMINISTRATIVO", "OTRO")),
                "falta algún motivo de la especificación");
        assertTrue(SalidaAlmacenService.MOTIVOS.stream().allMatch(m -> m.length() <= 50),
                "ningún motivo debe exceder la columna motivo_salida");
    }

    @Test
    void laSalidaSePuedeBuscarPorFolio() {
        Usuario usuario = usuarioDePrueba();
        Producto producto = productoCon(10);

        SalidaAlmacen salida = salidaAlmacenService.registrar(
                producto.getId(), 1, usuario.getId(), "Ana Torres",
                "María Gómez", null, "Almacén", "INSTALACION", null);

        assertEquals(salida.getFolio(), salidaAlmacenService.buscarPorFolio(salida.getFolio()).getFolio(),
                "el folio debe servir para recuperar la salida");
        assertNull(salidaAlmacenService.buscarPorFolio("SAL-9999999999999"));
    }

    @Test
    void elReporteDeSalidasDevuelveElDetalleDeCadaLinea() {
        Usuario usuario = usuarioDePrueba();
        Producto producto = productoCon(10);

        SalidaAlmacen salida = salidaAlmacenService.registrar(
                producto.getId(), 3, usuario.getId(), "Ana Torres",
                "Carlos López", "Técnico", "Almacén", "MANTENIMIENTO_PREVENTIVO", null);

        ReporteSalidasPeriodoDTO fila = reporteService.salidasPorPeriodo(null, null, usuario).stream()
                .filter(r -> salida.getFolio().equals(r.getFolio()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("la salida no aparece en el reporte"));

        assertEquals("MANTENIMIENTO_PREVENTIVO", fila.getMotivoSalida());
        assertEquals("Carlos López", fila.getPersonaSolicita());
        assertEquals("Almacén", fila.getDepartamentoSolicita());
        assertNotNull(fila.getFolio());
    }

    // ==================================================================
    // Salida en bloque con orden de trabajo
    // ==================================================================

    /**
     * Surtir una lista era una salida por producto: diez renglones significaban
     * diez ventanas, diez folios y ningún papel que los juntara. Aquí se
     * comprueba que todos salen bajo el mismo folio y con la misma orden.
     */
    @Test
    void laSalidaEnBloqueSacaVariosProductosConUnSoloFolio() {
        Usuario usuario = usuarioDePrueba();
        Producto uno = productoCon(10);
        Producto dos = productoCon(8);

        long kardexAntes = kardexRepository.count();

        SalidaAlmacen salida = salidaAlmacenService.registrar(
                usuario.getId(), "OT-4521", "Carlos López", "Jefe de turno", "Almacén",
                "Ana Torres", "REPARACION", null, "Tanda completa",
                List.of(new SalidaAlmacenService.Detalle(uno.getId(), 2),
                        new SalidaAlmacenService.Detalle(dos.getId(), 3)));

        assertEquals(2, salida.getProductosEntregados().size(),
                "la salida debe llevar todos los renglones de la lista");
        assertEquals(8, productoRepository.findById(uno.getId()).get().getExistenciaActual(),
                "al primero se le descuentan sus 2");
        assertEquals(5, productoRepository.findById(dos.getId()).get().getExistenciaActual(),
                "al segundo se le descuentan sus 3, sin abrir otra salida");
        assertEquals("OT-4521", salida.getOrdenTrabajo(),
                "la orden de trabajo debe guardarse con la salida");

        assertEquals(kardexAntes + 2, kardexRepository.count(),
                "una línea de kardex por producto, todas con el mismo folio");
        kardexRepository.findByProductoIdOrderByFechaDescHoraDesc(uno.getId()).stream()
                .filter(k -> salida.getFolio().equals(k.getFolio()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("el primer producto no quedó en el kardex"));
        kardexRepository.findByProductoIdOrderByFechaDescHoraDesc(dos.getId()).stream()
                .filter(k -> salida.getFolio().equals(k.getFolio()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("el segundo producto no quedó en el kardex"));
    }

    /**
     * La orden de trabajo es lo que amarra la salida con el documento que la
     * autoriza: sin ella el papel no dice con qué derecho se fue el material.
     */
    @Test
    void sinOrdenDeTrabajoNoSeRegistraLaSalida() {
        Usuario usuario = usuarioDePrueba();
        Producto producto = productoCon(5);

        long salidasAntes = salidaAlmacenRepository.count();
        long kardexAntes = kardexRepository.count();

        var excepcion = assertThrows(IllegalArgumentException.class, () ->
                salidaAlmacenService.registrar(usuario.getId(), "   ",
                        "Carlos López", null, "Almacén", "Ana Torres",
                        "REPARACION", null, null,
                        List.of(new SalidaAlmacenService.Detalle(producto.getId(), 1))));

        assertTrue(excepcion.getMessage().contains("orden de trabajo"), excepcion.getMessage());
        assertEquals(5, productoRepository.findById(producto.getId()).get().getExistenciaActual(),
                "sin orden no se descuenta nada");
        assertEquals(salidasAntes, salidaAlmacenRepository.count(),
                "sin orden no se crea la salida");
        assertEquals(kardexAntes, kardexRepository.count(),
                "sin orden no se escribe kardex");
    }

    /**
     * "OTRO" es el único motivo que no dice nada por sí solo: quien lo elige
     * tiene que escribir de qué se trata, o el registro queda mudo.
     */
    @Test
    void elMotivoOtroExigeEscribirDeQueSeTrata() {
        Usuario usuario = usuarioDePrueba();
        Producto producto = productoCon(5);

        long salidasAntes = salidaAlmacenRepository.count();

        var excepcion = assertThrows(IllegalArgumentException.class, () ->
                salidaAlmacenService.registrar(usuario.getId(), "OT-9",
                        "Carlos López", null, "Almacén", "Ana Torres",
                        "OTRO", "   ", null,
                        List.of(new SalidaAlmacenService.Detalle(producto.getId(), 1))));

        assertTrue(excepcion.getMessage().contains("OTRO"), excepcion.getMessage());
        assertEquals(5, productoRepository.findById(producto.getId()).get().getExistenciaActual());
        assertEquals(salidasAntes, salidaAlmacenRepository.count());

        // Con la explicación escrita sí pasa
        SalidaAlmacen salida = salidaAlmacenService.registrar(usuario.getId(), "OT-9",
                "Carlos López", null, "Almacén", "Ana Torres",
                "OTRO", "Retiro de material para la mudanza de oficina", null,
                List.of(new SalidaAlmacenService.Detalle(producto.getId(), 1)));

        assertEquals("Retiro de material para la mudanza de oficina", salida.getMotivoOtro());
    }

    /**
     * En una lista, si el último producto no alcanza no debe descontarse
     * ninguno de los anteriores: una salida a medias deja el almacén en números
     * que nadie puede explicar.
     */
    @Test
    void siAlgunProductoNoAlcanzaNoSaleNadaDeLaLista() {
        Usuario usuario = usuarioDePrueba();
        Producto suficiente = productoCon(10);
        Producto escaso = productoCon(1);

        long salidasAntes = salidaAlmacenRepository.count();
        long kardexAntes = kardexRepository.count();

        var excepcion = assertThrows(IllegalArgumentException.class, () ->
                salidaAlmacenService.registrar(usuario.getId(), "OT-12",
                        "Carlos López", null, "Almacén", "Ana Torres",
                        "REPARACION", null, null,
                        List.of(new SalidaAlmacenService.Detalle(suficiente.getId(), 2),
                                new SalidaAlmacenService.Detalle(escaso.getId(), 5))));

        assertTrue(excepcion.getMessage().contains("Stock insuficiente"), excepcion.getMessage());
        assertEquals(10, productoRepository.findById(suficiente.getId()).get().getExistenciaActual(),
                "el primero iba bien, pero no debe descontarse");
        assertEquals(1, productoRepository.findById(escaso.getId()).get().getExistenciaActual());
        assertEquals(salidasAntes, salidaAlmacenRepository.count(),
                "una salida a medias no debe crearse");
        assertEquals(kardexAntes, kardexRepository.count());
    }
}
