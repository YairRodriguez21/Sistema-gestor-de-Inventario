package com.sapam.inventario;

import com.sapam.inventario.config.MigracionPrestamos;
import com.sapam.inventario.entity.Herramienta;
import com.sapam.inventario.entity.PrestamoHerramienta;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.reportes.ReporteService;
import com.sapam.inventario.reportes.ReportesTabla;
import com.sapam.inventario.repository.AuditoriaSistemaRepository;
import com.sapam.inventario.repository.HerramientaRepository;
import com.sapam.inventario.repository.PrestamoHerramientaRepository;
import com.sapam.inventario.repository.UsuarioRepository;
import com.sapam.inventario.service.HerramientaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Módulo de herramientas: alta, préstamo y devolución.
 *
 * La regla que sostiene todo el módulo es que una herramienta solo puede tener
 * un préstamo abierto a la vez; si no, dos personas distintas quedarían
 * señaladas como responsables del mismo bien y nadie sabría a quién exigírselo.
 */
@SpringBootTest
class HerramientaTest {

    @Autowired private HerramientaService herramientaService;
    @Autowired private ReporteService reporteService;
    @Autowired private HerramientaRepository herramientaRepository;
    @Autowired private PrestamoHerramientaRepository prestamoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AuditoriaSistemaRepository auditoriaRepository;

    private Usuario usuarioDePrueba() {
        return usuarioRepository.findAll().stream().findFirst().orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre("Tester Herramientas");
            u.setEmail("herramientas@prueba.com");
            u.setPassword("123");
            u.setRol(Usuario.Rol.usuario);
            u.setActivo(true);
            return usuarioRepository.save(u);
        });
    }

    private Herramienta herramientaDePrueba() {
        Herramienta h = new Herramienta();
        h.setNombre("Taladro de prueba " + System.nanoTime());
        h.setMarca("Bosch");
        h.setNumeroSerie("SN-" + System.nanoTime());
        return herramientaService.alta(h, usuarioDePrueba());
    }

    @Test
    void elAltaGeneraElCodigoSinQueNadieLoTeclee() {
        Herramienta herramienta = herramientaDePrueba();

        assertNotNull(herramienta.getCodigoBarras(), "el código lo asigna el sistema");
        assertTrue(herramienta.getCodigoBarras().startsWith("HER-"),
                "el código debe seguir el formato HER-: " + herramienta.getCodigoBarras());
        assertNotNull(herramienta.getFechaRegistro(), "el alta debe dejar su fecha");
        assertEquals("NUEVO", herramienta.getEstadoFisico(), "estado por defecto");
        assertFalse(herramientaRepository.findByCodigoBarras(herramienta.getCodigoBarras()).isEmpty(),
                "el código debe servir para recuperar la ficha, que es lo que hace el lector");
    }

    @Test
    void elCodigoDeHerramientaEsCortoParaQueQuepaEnLaEtiqueta() {
        Herramienta herramienta = herramientaDePrueba();

        assertTrue(herramienta.getCodigoEscaneable().length() <= 16,
                "con 40 caracteres la barra ocupa media etiqueta y el lector no la agarra: "
                        + herramienta.getCodigoEscaneable());
        assertEquals(herramienta.getCodigoBarras(), herramienta.getCodigoEscaneable(),
                "la etiqueta lleva el código que asigna el sistema");
    }

    @Test
    void sinNombreNoSeDaDeAlta() {
        Herramienta herramienta = new Herramienta();
        herramienta.setNombre("   ");

        var excepcion = assertThrows(IllegalArgumentException.class,
                () -> herramientaService.alta(herramienta, usuarioDePrueba()));
        assertTrue(excepcion.getMessage().contains("nombre"), excepcion.getMessage());
    }

    @Test
    void laBitacoraRegistraElAltaElPrestamoYLaDevolucion() {
        Usuario operador = usuarioDePrueba();
        Herramienta herramienta = herramientaDePrueba();
        long eventosAntes = auditoriaRepository.count();

        PrestamoHerramienta prestamo = herramientaService.prestar(
                herramienta.getId(), "Roberto Mantenimiento", "Almacén",
                LocalDate.now().plusDays(3), "Trabajo en campo", operador);

        assertEquals(operador.getNombre(), prestamo.getPersonaEntrega(),
                "quién entrega es quien tiene la sesión abierta");
        assertEquals("PENDIENTE", prestamo.getEstado());

        herramientaService.devolver(prestamo.getId(), LocalDate.now(), "BUENO",
                "Todo en orden", operador);

        assertEquals(eventosAntes + 2, auditoriaRepository.count(),
                "el préstamo y la devolución deben quedar en la bitácora");
    }

    @Test
    void unaHerramientaNoSePuedePrestarDosVeces() {
        Usuario operador = usuarioDePrueba();
        Herramienta herramienta = herramientaDePrueba();

        herramientaService.prestar(herramienta.getId(), "Roberto Mantenimiento", null,
                LocalDate.now().plusDays(7), null, operador);

        var excepcion = assertThrows(IllegalArgumentException.class, () ->
                herramientaService.prestar(herramienta.getId(), "Roberto Mantenimiento", null,
                        LocalDate.now().plusDays(7), null, operador));

        assertTrue(excepcion.getMessage().contains("ya está prestada"), excepcion.getMessage());
        assertEquals(1, prestamoRepository.findByFechaDevolucionIsNullOrderByFechaEstimadaDevolucionAsc()
                .stream().filter(p -> p.getHerramienta().getId().equals(herramienta.getId())).count(),
                "no debe crearse un segundo préstamo abierto");
    }

    @Test
    void laDevolucionCierraElPrestamoYDejaElEstadoDelBien() {
        Usuario operador = usuarioDePrueba();
        Herramienta herramienta = herramientaDePrueba();

        PrestamoHerramienta prestamo = herramientaService.prestar(
                herramienta.getId(), "Roberto Mantenimiento", "Taller",
                LocalDate.now().plusDays(2), null, operador);

        assertNotNull(herramientaRepository.findById(herramienta.getId()).get()
                .getFechaEstimadaDevolucion(), "prestada, la ficha debe decir cuándo vuelve");

        herramientaService.devolver(prestamo.getId(), LocalDate.now(), "MALO",
                "Rota la broca", operador);

        assertTrue(herramientaService.prestamoActivo(herramienta.getId()).isEmpty(),
                "al devolver no debe quedar préstamo abierto");

        Herramienta despues = herramientaRepository.findById(herramienta.getId()).get();
        assertEquals("MALO", despues.getEstadoFisico(),
                "el estado del bien debe ser el con el que regresa");
        assertNull(despues.getFechaEstimadaDevolucion(), "ya no debe estar señalada fecha de vuelta");

        PrestamoHerramienta cerrado = prestamoRepository.findById(prestamo.getId()).get();
        assertEquals("DEVUELTO", cerrado.getEstado());
        assertEquals("MALO", cerrado.getHerramientaEstado());
        assertNotNull(cerrado.getObservaciones());
    }

    /**
     * La especificación pide registrar el folio del préstamo, con el mismo
     * criterio que las entradas ({@code ENT-}) y las salidas ({@code SAL-}):
     * lo asigna el sistema, nadie lo teclea.
     */
    @Test
    void elPrestamoTieneSuFolioAsignadoPorElSistema() {
        Herramienta herramienta = herramientaDePrueba();

        PrestamoHerramienta prestamo = herramientaService.prestar(
                herramienta.getId(), "Roberto Mantenimiento", "Taller",
                LocalDate.now().plusDays(2), null, usuarioDePrueba());

        assertNotNull(prestamo.getFolio(), "el préstamo no puede quedar sin folio");
        assertTrue(prestamo.getFolio().startsWith("PRE-"),
                "el folio debe seguir el formato PRE-: " + prestamo.getFolio());
        assertTrue(prestamo.getFolio().length() <= 16,
                "el folio del ticket debe ser corto para que el código de barras "
                        + "quepa en el papel: " + prestamo.getFolio());
        assertTrue(prestamoRepository.findByFolio(prestamo.getFolio()).isPresent(),
                "el folio debe servir para recuperar el préstamo");
    }

    /**
     * El ticket de préstamo lleva el destino y su folio sirve para volver a
     * encontrar el préstamo cuando el usuario escanea el papel para devolver.
     */
    @Test
    void elTicketGuardaElDestinoYSePuedeBuscarPorSuFolio() {
        Herramienta herramienta = herramientaDePrueba();

        PrestamoHerramienta prestamo = herramientaService.prestar(
                herramienta.getId(), "Roberto Mantenimiento", "Almacén",
                "  Obra norte  ", LocalDate.now().plusDays(3),
                "Taladro para la cuadrilla", usuarioDePrueba());

        assertEquals("Obra norte", prestamo.getDestino(),
                "el destino se limpia y queda impreso en el ticket");

        PrestamoHerramienta porFolio = herramientaService
                .prestamoPorFolio(prestamo.getFolio())
                .orElseThrow(() -> new AssertionError(
                        "el folio del ticket debe recuperar el préstamo"));
        assertEquals(prestamo.getId(), porFolio.getId());
        assertEquals("Roberto Mantenimiento", porFolio.getPersonaRecibe());
        assertEquals(herramienta.getId(), porFolio.getHerramienta().getId(),
                "el ticket necesita la herramienta aunque la transacción ya haya cerrado");
    }

    /**
     * La especificación pide dos estados: el físico al momento de la entrega y
     * el de la devolución. Si se guardaran en el mismo campo, cerrar el
     * préstamo borraría el primero y no se podría comparar cómo salió con cómo
     * volvió.
     */
    @Test
    void elEstadoAlEntregarSeConservaCuandoSeDevuelve() {
        Usuario operador = usuarioDePrueba();
        Herramienta herramienta = herramientaDePrueba();
        herramienta.setEstadoFisico("REGULAR");
        herramientaRepository.save(herramienta);

        PrestamoHerramienta prestamo = herramientaService.prestar(
                herramienta.getId(), "Roberto Mantenimiento", "Taller",
                LocalDate.now().plusDays(2), null, operador);
        assertEquals("REGULAR", prestamo.getEstadoEntrega(),
                "el préstamo congela el estado con el que sale la herramienta");

        herramientaService.devolver(prestamo.getId(), LocalDate.now(), "MALO",
                "Se rompió en uso", operador);

        PrestamoHerramienta cerrado = prestamoRepository.findById(prestamo.getId()).get();
        assertEquals("MALO", cerrado.getHerramientaEstado(), "este es el de la devolución");
        assertEquals("REGULAR", cerrado.getEstadoEntrega(),
                "y el de la entrega no debe pisarse con él");
    }

    /** Si al volver no se informa estado, la herramienta regresa como salió. */
    @Test
    void devolverSinEstadoConservaElDeLaEntrega() {
        Usuario operador = usuarioDePrueba();
        Herramienta herramienta = herramientaDePrueba();
        herramienta.setEstadoFisico("REGULAR");
        herramientaRepository.save(herramienta);

        PrestamoHerramienta prestamo = herramientaService.prestar(
                herramienta.getId(), "Roberto Mantenimiento", "Taller",
                LocalDate.now().plusDays(2), null, operador);

        herramientaService.devolver(prestamo.getId(), LocalDate.now(), null, null, operador);

        PrestamoHerramienta cerrado = prestamoRepository.findById(prestamo.getId()).get();
        assertEquals("REGULAR", cerrado.getHerramientaEstado());
        assertEquals("REGULAR", herramientaRepository.findById(herramienta.getId()).get()
                .getEstadoFisico(), "el estado del bien debe seguir diciendo lo mismo");
    }

    /** La especificación pide también quién recibe la devolución. */
    @Test
    void elPrestamoGuardaQuienRecibeLaDevolucion() {
        Usuario operador = usuarioDePrueba();
        Herramienta herramienta = herramientaDePrueba();

        PrestamoHerramienta prestamo = herramientaService.prestar(
                herramienta.getId(), "Roberto Mantenimiento", "Taller",
                LocalDate.now().plusDays(2), null, operador);
        assertNull(prestamo.getUsuarioDevolucion(), "aún no se ha devuelto");

        herramientaService.devolver(prestamo.getId(), LocalDate.now(), "BUENO", null, operador);

        PrestamoHerramienta cerrado = prestamoRepository.findById(prestamo.getId()).get();
        assertNotNull(cerrado.getUsuarioDevolucion(),
                "al cerrar el préstamo debe quedar la cuenta que recibió la vuelta");
        assertEquals(operador.getId(), cerrado.getUsuarioDevolucion().getId());
    }

    /** Quién dio de alta la herramienta queda en la ficha, y la edición no lo borra. */
    @Test
    void elAltaDejaQuienLaRegistroYLaEdicionNoLoBorra() {
        Usuario operador = usuarioDePrueba();
        Herramienta herramienta = herramientaDePrueba();

        assertEquals(operador.getId(), herramienta.getUsuarioRegistro().getId(),
                "la ficha debe decir quién metió la herramienta al almacén");

        herramienta.setAreaResponsable("Mantenimiento");
        Herramienta editada = herramientaService.modificar(herramienta, operador);

        assertEquals(operador.getId(), editada.getUsuarioRegistro().getId(),
                "editar no debe borrar un dato histórico");
    }

    /**
     * Los préstamos hechos antes de que existiera el folio no se quedan en
     * blanco: al arrancar la migración se los asigna, y no vuelve a tocarlos.
     */
    @Test
    void laMigracionRellenaLosPrestamosQueNoTienenFolio() {
        Herramienta herramienta = herramientaDePrueba();

        PrestamoHerramienta viejo = new PrestamoHerramienta();
        viejo.setHerramienta(herramienta);
        viejo.setPersonaRecibe("Roberto Mantenimiento");
        viejo.setFechaEntrega(java.time.LocalDateTime.now());
        viejo.setFechaEstimadaDevolucion(LocalDate.now().plusDays(3));
        viejo.setEstado("PENDIENTE");
        viejo.setHerramientaEstado("REGULAR");
        viejo = prestamoRepository.save(viejo);
        assertNull(viejo.getFolio(), "así quedaban los préstamos antiguos");

        MigracionPrestamos migracion = new MigracionPrestamos(prestamoRepository);
        migracion.run();

        PrestamoHerramienta rellenado = prestamoRepository.findById(viejo.getId()).get();
        assertTrue(rellenado.getFolio().startsWith("PRE-"),
                "la migración le pone folio con el mismo formato: " + rellenado.getFolio());
        assertTrue(rellenado.getFolio().length() <= 16,
                "el folio migrado también debe caber en el código del ticket: "
                        + rellenado.getFolio());
        assertEquals("REGULAR", rellenado.getEstadoEntrega(),
                "estando abierto, su estado al salir era el que tenía guardado");

        String folio = rellenado.getFolio();
        migracion.run();
        assertEquals(folio, prestamoRepository.findById(viejo.getId()).get().getFolio(),
                "es idempotente: una segunda pasada no le asigna otro folio");
    }

    @Test
    void noSePuedeDevolverDosVeces() {
        Usuario operador = usuarioDePrueba();
        Herramienta herramienta = herramientaDePrueba();

        PrestamoHerramienta prestamo = herramientaService.prestar(
                herramienta.getId(), "Roberto Mantenimiento", null,
                LocalDate.now().plusDays(2), null, operador);
        herramientaService.devolver(prestamo.getId(), LocalDate.now(), "BUENO", null, operador);

        var excepcion = assertThrows(IllegalArgumentException.class, () ->
                herramientaService.devolver(prestamo.getId(), LocalDate.now(), "BUENO",
                        null, operador));

        assertTrue(excepcion.getMessage().contains("ya fue devuelto"), excepcion.getMessage());
    }

    @Test
    void laDevolucionNoPuedeSerAnteriorALaEntrega() {
        Usuario operador = usuarioDePrueba();
        Herramienta herramienta = herramientaDePrueba();

        PrestamoHerramienta prestamo = herramientaService.prestar(
                herramienta.getId(), "Roberto Mantenimiento", null,
                LocalDate.now().plusDays(1), null, operador);

        var excepcion = assertThrows(IllegalArgumentException.class, () ->
                herramientaService.devolver(prestamo.getId(),
                        LocalDate.now().minusDays(3), "BUENO", null, operador));

        assertTrue(excepcion.getMessage().contains("anterior"), excepcion.getMessage());
    }

    @Test
    void noSePuedePrestarParaAyer() {
        Usuario operador = usuarioDePrueba();
        Herramienta herramienta = herramientaDePrueba();

        var excepcion = assertThrows(IllegalArgumentException.class, () ->
                herramientaService.prestar(herramienta.getId(), "Roberto Mantenimiento", null,
                        LocalDate.now().minusDays(1), null, operador));

        assertTrue(excepcion.getMessage().contains("anterior a hoy"), excepcion.getMessage());
        assertTrue(herramientaService.prestamoActivo(herramienta.getId()).isEmpty(),
                "un préstamo rechazado no debe quedar abierto");
    }

    @Test
    void laBusquedaEncuentraLaHerramientaPorSuCodigo() {
        Herramienta herramienta = herramientaDePrueba();

        assertEquals(1, herramientaService.buscar(herramienta.getCodigoBarras()).size(),
                "el lector escribe el código de la etiqueta en la caja de búsqueda");
        assertFalse(herramientaService.buscar(herramienta.getNombre()).isEmpty(),
                "por nombre también debe encontrarla");
        assertTrue(herramientaService.buscar("esto-no-existe-999").isEmpty());
    }

    @Test
    void laBusquedaTambienEncuentraPorNumeroDeSerie() {
        Herramienta herramienta = herramientaDePrueba();

        assertEquals(1, herramientaService.buscar(herramienta.getNumeroSerie()).size(),
                "la serie va escrita en la herramienta: escanearla debe bastar");

        // Dos herramientas pueden compartir serie (el campo es opcional y se
        // teclea a mano): la búsqueda tiene que devolver las dos, no quedarse
        // con la primera que encuentre.
        Herramienta otra = herramientaDePrueba();
        otra.setNumeroSerie(herramienta.getNumeroSerie());
        herramientaService.modificar(otra, usuarioDePrueba());

        assertEquals(2, herramientaService.buscar(herramienta.getNumeroSerie()).size(),
                "una serie compartida debe devolver las dos herramientas");
    }

    @Test
    void lasHerramientasPrestadasYLasVencidasAparecenEnSusReportes() {
        Usuario operador = usuarioDePrueba();
        Herramienta herramienta = herramientaDePrueba();

        PrestamoHerramienta prestamo = herramientaService.prestar(
                herramienta.getId(), "Roberto Mantenimiento", "Almacén",
                LocalDate.now().plusDays(5), null, operador);

        // Se pasa por ReportesTabla, que es el camino de verdad: ahí es donde
        // los DTO internos se aplanan a filas con su encabezado.
        ReportesTabla.Tabla prestadas =
                ReportesTabla.herramientasPrestadas(reporteService.herramientasPrestadas(operador));
        assertTrue(prestadas.filas().stream()
                        .anyMatch(f -> herramienta.getCodigoBarras().equals(f.get(9))),
                "el préstamo debe aparecer en el reporte de herramientas prestadas");
        assertTrue(prestadas.filas().stream()
                        .anyMatch(f -> f.get(0).startsWith("PRE-")),
                "la fila lleva el folio del préstamo, que asigna el sistema");

        // Se le reclasa la fecha para simular un préstamo ya vencido: es lo que
        // hace que el reporte de pendientes tenga filas que pedir.
        prestamo.setFechaEstimadaDevolucion(LocalDate.now().minusDays(3));
        prestamoRepository.save(prestamo);

        ReportesTabla.Tabla vencidas = ReportesTabla.herramientasPendientesDevolucion(
                reporteService.herramientasPendientesDevolucion(operador));
        assertTrue(vencidas.filas().stream()
                        .anyMatch(f -> herramienta.getCodigoBarras().equals(f.get(7))),
                "el préstamo vencido debe aparecer en el reporte de pendientes");
    }

    /**
     * Quién se la lleva no se elige de la lista de usuarios: se escribe a mano,
     * igual que quien recibe una salida, porque el trabajador que toma una
     * herramienta casi nunca tiene cuenta en el sistema.
     */
    @Test
    void quienRecibeSeAnotaComoTextoLibre() {
        Herramienta herramienta = herramientaDePrueba();

        PrestamoHerramienta prestamo = herramientaService.prestar(
                herramienta.getId(), "   Roberto el de Mantenimiento   ", "Almacén",
                LocalDate.now().plusDays(3), null, usuarioDePrueba());

        assertEquals("Roberto el de Mantenimiento", prestamo.getPersonaRecibe(),
                "el nombre se limpia y se guarda tal cual");
        assertEquals("Almacén", prestamo.getAreaResponsable());
    }

    @Test
    void sinNombreDeQuienRecibeNoSePresta() {
        Herramienta herramienta = herramientaDePrueba();

        var excepcion = assertThrows(IllegalArgumentException.class, () ->
                herramientaService.prestar(herramienta.getId(), "   ", null,
                        LocalDate.now().plusDays(3), null, usuarioDePrueba()));

        assertTrue(excepcion.getMessage().contains("recibe"), excepcion.getMessage());
        assertTrue(herramientaService.prestamoActivo(herramienta.getId()).isEmpty(),
                "un préstamo rechazado no debe quedar abierto");
    }

    /**
     * El código es el que lleva la etiqueta impresa: si la edición lo
     * cambiara, esa etiqueta dejaría de leerse y habría que reimprimirla toda.
     */
    @Test
    void laEdicionNoCambiaElCodigoNiLaFechaDeRegistro() {
        Usuario operador = usuarioDePrueba();
        Herramienta herramienta = herramientaDePrueba();
        String codigo = herramienta.getCodigoBarras();
        // Se lee de la base y no del objeto en memoria: allí la fecha ya tiene
        // la precisión con la que se va a volver a guardar.
        var registro = herramientaRepository.findById(herramienta.getId()).get().getFechaRegistro();

        herramienta.setNombre("Taladro reubicado");
        herramienta.setAreaResponsable("Mantenimiento");
        Herramienta editada = herramientaService.modificar(herramienta, operador);

        assertEquals(codigo, editada.getCodigoBarras(),
                "la etiqueta impresa seguiría leyendo el código viejo");
        assertEquals(registro, editada.getFechaRegistro(), "el alta no se rehace");
        assertEquals("Taladro reubicado", editada.getNombre());
        assertEquals("Mantenimiento", editada.getAreaResponsable());
    }

    @Test
    void sinNombreNoSePuedeEditar() {
        Usuario operador = usuarioDePrueba();
        Herramienta herramienta = herramientaDePrueba();
        herramienta.setNombre("  ");

        var excepcion = assertThrows(IllegalArgumentException.class,
                () -> herramientaService.modificar(herramienta, operador));
        assertTrue(excepcion.getMessage().contains("nombre"), excepcion.getMessage());
    }

    /** Una herramienta con el préstamo abierto está con alguien: no se de baja. */
    @Test
    void laBajaDeUnaHerramientaPrestadaNoSePermite() {
        Usuario operador = usuarioDePrueba();
        Herramienta herramienta = herramientaDePrueba();
        herramientaService.prestar(herramienta.getId(), "Roberto el de Mantenimiento",
                null, LocalDate.now().plusDays(3), null, operador);

        var excepcion = assertThrows(IllegalArgumentException.class,
                () -> herramientaService.baja(herramienta.getId(), operador));

        assertTrue(excepcion.getMessage().contains("devolución"), excepcion.getMessage());
        assertEquals("ACTIVO", herramientaRepository.findById(herramienta.getId()).get().getEstado(),
                "el intento rechazado no debe dar de baja nada");
    }

    @Test
    void laBajaEsLogicaYSeConservaLaTrazabilidad() {
        Usuario operador = usuarioDePrueba();
        Herramienta herramienta = herramientaDePrueba();
        long eventosAntes = auditoriaRepository.count();

        herramientaService.baja(herramienta.getId(), operador);

        Herramienta despues = herramientaRepository.findById(herramienta.getId()).get();
        assertEquals("BAJAS", despues.getEstado());
        assertNotNull(herramientaRepository.findById(herramienta.getId()).orElse(null),
                "la fila no se borra: la trazabilidad manda");

        assertTrue(herramientaService.listarTodas().stream()
                        .noneMatch(h -> h.getId().equals(herramienta.getId())),
                "una herramienta de baja desaparece de la pantalla");
        assertTrue(herramientaService.buscar(herramienta.getCodigoBarras()).isEmpty(),
                "el código de una herramienta de baja ya no la trae en la búsqueda");

        assertEquals(eventosAntes + 1, auditoriaRepository.count(),
                "la baja debe quedar en la bitácora");
    }
}
