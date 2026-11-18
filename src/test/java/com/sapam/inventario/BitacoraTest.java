package com.sapam.inventario;

import com.sapam.inventario.config.JwtTokenUtil;
import com.sapam.inventario.entity.AuditoriaSistema;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.AuditoriaSistemaRepository;
import com.sapam.inventario.repository.UsuarioRepository;
import com.sapam.inventario.service.BitacoraService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La bitácora del sistema: se escribe sola, se consulta con filtros y no se
 * puede borrar.
 *
 * Comprueba las tres cosas por separado:
 *  - que un evento recién registrado aparece y el orden es de más reciente a más antiguo,
 *  - que se puede filtrar por acción y por usuario,
 *  - que la API solo sirve {@code GET}: hay sesión para leer y nada que escriba.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BitacoraTest {

    @Autowired private BitacoraService bitacoraService;
    @Autowired private AuditoriaSistemaRepository auditoriaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private JwtTokenUtil jwtTokenUtil;
    @Autowired private MockMvc mockMvc;

    @Test
    void losUltimosEventosIncluyenElRecienteYVanDelMasNuevoAlMasViejo() {
        Usuario usuario = usuarioDePrueba();
        bitacoraService.registrar("PRUEBA_BITACORA", "PRUEBA",
                "evento de prueba para revisar el orden", "sin incidencias", usuario);

        List<AuditoriaSistema> eventos = bitacoraService.ultimosEventos();

        assertTrue(eventos.stream().anyMatch(e -> "PRUEBA_BITACORA".equals(e.getAccion())),
                "el evento recién registrado debe aparecer en los últimos eventos");

        assertFalse(eventos.isEmpty(), "debe haber eventos que mostrar");
        for (int i = 0; i < eventos.size() - 1; i++) {
            AuditoriaSistema actual = eventos.get(i);
            AuditoriaSistema siguiente = eventos.get(i + 1);
            boolean estaAtras = actual.getFecha().isBefore(siguiente.getFecha())
                    || (actual.getFecha().equals(siguiente.getFecha())
                        && actual.getHora().isBefore(siguiente.getHora()));
            assertFalse(estaAtras, "los eventos deben ir de más reciente a más antiguo");
        }
    }

    @Test
    void sePuedeFiltrarLaBitacoraPorAccionYPorUsuario() {
        Usuario usuario = usuarioDePrueba();
        bitacoraService.registrar("PRUEBA_FILTRO", "PRUEBA", "primera entrada", "", usuario);
        bitacoraService.registrar("PRUEBA_FILTRO", "PRUEBA", "segunda entrada", "", usuario);

        assertTrue(bitacoraService.porAccion("PRUEBA_FILTRO").size() >= 2,
                "el filtro por acción debe devolver todos los eventos con esa acción");

        assertTrue(bitacoraService.porAccion("ACCION_QUE_NO_EXISTE_EN_NINGUNA_PARTE").isEmpty(),
                "una acción sin eventos no debe devolver nada");

        assertTrue(bitacoraService.porUsuario(usuario.getId()).stream()
                        .anyMatch(e -> "PRUEBA_FILTRO".equals(e.getAccion())),
                "el filtro por usuario debe devolver sus eventos");
    }

    /**
     * Los tres filtros se acotan a 200, y el tope está en la consulta y no en
     * la ventana.
     *
     * Se llena la tabla con 205 eventos de la misma acción para que el tope
     * signifique algo: hoy hay menos de 200 y la prueba sería muda. Si el
     * límite estuviera solo en la pantalla o solo escrito en el javadoc, aquí
     * devolvería los 205 y se caería.
     *
     * Es el caso que no se nota ahora y sí dentro de un par de años: un
     * filtrado sin tope crece con la bitácora y acabaría devolviendo cientos
     * de miles de filas en una sola respuesta.
     */
    @Test
    void losFiltrosDeLaBitacoraSeAcotanA200Eventos() {
        Usuario usuario = usuarioDePrueba();
        String accion = "PRUEBA_TOPE_" + System.nanoTime();

        for (int i = 0; i < 205; i++) {
            AuditoriaSistema evento = new AuditoriaSistema();
            evento.setFecha(LocalDate.now());
            evento.setHora(LocalTime.now().plusNanos(i));
            evento.setUsuario(usuario);
            evento.setAccion(accion);
            evento.setModuloAfectado("PRUEBA");
            evento.setInformacionModificada("evento " + i + ", para probar el tope");
            evento.setObservaciones("sin incidencias");
            evento.setCodigoVerificacion("AUD-tope-" + System.nanoTime() + "-" + i);
            auditoriaRepository.save(evento);
        }

        assertTrue(auditoriaRepository.count() > 200,
                "hacen falta más de 200 eventos para que el tope signifique algo");

        assertEquals(200, bitacoraService.porAccion(accion).size(),
                "el filtro por acción no debe devolver la tabla entera");
        assertEquals(200, bitacoraService.porUsuario(usuario.getId()).size(),
                "el filtro por usuario tampoco");
        assertEquals(200, bitacoraService.ultimosEventos().size(),
                "la pantalla pide lo último, no toda la bitácora");
    }

    /**
     * La ventana avanza y retrocede por la bitácora sin cargarla entera.
     *
     * Se llena con más de una página y se comprueba que las dos no se pisan,
     * que van del más nuevo al más viejo y que la última se agota: eso es lo
     * que hace que la pantalla abra igual de rápido hoy que con cientos de
     * miles de eventos.
     *
     * Las horas se ponen a segundos distintos y no con {@code now()}: si dos
     * eventos cayeran en el mismo segundo, el orden de los empates no estaría
     * definido y la prueba saldría a veces mal sin que hubiera nada roto.
     */
    @Test
    void laBitacoraAvanzaPaginaAPaginaSinCargarseEntera() {
        Usuario usuario = usuarioDePrueba();
        String accion = "PRUEBA_PAGINA_" + System.nanoTime();

        List<AuditoriaSistema> creados = new java.util.ArrayList<>();
        for (int i = 0; i < 250; i++) {
            AuditoriaSistema evento = new AuditoriaSistema();
            evento.setFecha(LocalDate.now());
            evento.setHora(LocalTime.of(12, 0, 0).plusSeconds(i));
            evento.setUsuario(usuario);
            evento.setAccion(accion);
            evento.setModuloAfectado("PRUEBA");
            evento.setInformacionModificada("evento " + i);
            evento.setObservaciones("sin incidencias");
            evento.setCodigoVerificacion("AUD-pagina-" + System.nanoTime() + "-" + i);
            creados.add(auditoriaRepository.save(evento));
        }

        BitacoraService.Pagina primera = bitacoraService.eventos(accion, null, 0);
        BitacoraService.Pagina segunda = bitacoraService.eventos(accion, null, 1);

        assertEquals(250, primera.total(), "debe contar todos los que cumplen el filtro");
        assertEquals(2, primera.paginas(), "250 eventos caben en dos páginas de 200");
        assertEquals(200, primera.eventos().size(), "la primera se llena entera");
        assertEquals(50, segunda.eventos().size(), "la segunda se queda con lo que falta");
        assertEquals(0, primera.pagina());
        assertEquals(1, segunda.pagina());

        // Las dos ventanas no se pisan: lo último de la primera es más nuevo
        // que lo primero de la segunda.
        AuditoriaSistema ultimaDeLaPrimera = primera.eventos().get(199);
        AuditoriaSistema primeraDeLaSegunda = segunda.eventos().get(0);
        assertTrue(ultimaDeLaPrimera.getHora().isAfter(primeraDeLaSegunda.getHora()),
                "las páginas deben ir de más reciente a más antiguo");

        Set<Long> vistos = new HashSet<>();
        for (AuditoriaSistema evento : primera.eventos()) {
            assertTrue(vistos.add(evento.getId()), "evento repetido en la página 1");
        }
        for (AuditoriaSistema evento : segunda.eventos()) {
            assertTrue(vistos.add(evento.getId()), "evento repetido entre páginas");
        }
        assertEquals(250, vistos.size(), "entre las dos páginas deben estar los 250");

        // Pedir una página que ya no existe cae a la última en vez de dejar la
        // ventana en blanco.
        BitacoraService.Pagina fueraDeRango = bitacoraService.eventos(accion, null, 9);
        assertEquals(1, fueraDeRango.pagina(), "una página fuera de rango cae a la última");
        assertEquals(50, fueraDeRango.eventos().size());
    }

    /**
     * Los selectores se llenan con lo que existe en la bitácora entera y no
     * con lo que tocó en la página visible: si salieran de la página de turno,
     * cambiar de página cambiaría lo que se puede filtrar.
     */
    @Test
    void losFiltrosSeOfrecenSobreTodaLaHistoria() {
        Usuario usuario = usuarioDePrueba();
        String accion = "PRUEBA_FILTROS_" + System.nanoTime();

        for (int i = 0; i < 250; i++) {
            AuditoriaSistema evento = new AuditoriaSistema();
            evento.setFecha(LocalDate.now());
            evento.setHora(LocalTime.of(8, 0, 0).plusSeconds(i));
            evento.setUsuario(usuario);
            evento.setAccion(accion);
            evento.setModuloAfectado("PRUEBA");
            evento.setInformacionModificada("evento " + i);
            evento.setObservaciones("sin incidencias");
            evento.setCodigoVerificacion("AUD-filtro-" + System.nanoTime() + "-" + i);
            auditoriaRepository.save(evento);
        }

        assertTrue(bitacoraService.acciones().contains(accion),
                "la acción debe aparecer en su selector aunque esté en la página 2");

        // Y el filtro debe alcanzar más allá de lo que cabe en la primera
        // página: si solo se filtraran los 200 visibles, esto saldría vacío.
        BitacoraService.Pagina filtrada = bitacoraService.eventos(accion, null, 1);
        assertEquals(250, filtrada.total(), "el filtro se resuelve allá abajo, no sobre 200");
        assertEquals(50, filtrada.eventos().size());
    }

    @Test
    void laApiDeLaBitacoraAdmiteSoloLectura() throws Exception {
        String token = "Bearer " + jwtTokenUtil.generateToken(usuarioDePrueba().getEmail());

        mockMvc.perform(get("/api/auditoria/ultimos").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accion").exists())
                .andExpect(jsonPath("$[0].usuario").exists());

        mockMvc.perform(get("/api/auditoria/accion/PRUEBA_BITACORA").header("Authorization", token))
                .andExpect(status().isOk());

        // Ningún método de escritura está implementado: no existe handler.
        mockMvc.perform(post("/api/auditoria/ultimos").header("Authorization", token))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/auditoria/ultimos").header("Authorization", token))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void laApiDeLaBitacoraNoSeAbreSinSesion() throws Exception {
        mockMvc.perform(get("/api/auditoria/ultimos"))
                .andExpect(resultado -> {
                    int estado = resultado.getResponse().getStatus();
                    assertTrue(estado == 401 || estado == 403,
                            "la bitácora no debe leerse sin sesión; devolvió " + estado);
                });
    }

    /**
     * El requisito pide bitácora "sin posibilidad de eliminación". No basta
     * con que hoy nadie llame a borrar: hay que comprobar que quien escriba
     * código contra la bitácora no pueda hacerlo ni por error.
     *
     * Se miran los métodos <b>visibles</b> de la interfaz, es decir, los que
     * declara ella y los que hereda: si algún día alguien le hace extender de
     * {@link CrudRepository}, los {@code delete} vuelven aparecer aquí solos.
     */
    @Test
    void elRepositorioDeLaBitacoraNoTieneNingunMetodoDeBorrado() {
        Set<String> nombres = Arrays.stream(AuditoriaSistemaRepository.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        Set<String> borrados = nombres.stream()
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith("delete"))
                .collect(Collectors.toSet());
        assertTrue(borrados.isEmpty(),
                "la bitácora no puede declarar ni heredar ningún método de borrado, "
                        + "pero tiene: " + borrados);

        assertFalse(CrudRepository.class.isAssignableFrom(AuditoriaSistemaRepository.class),
                "la bitácora no debe extender de CrudRepository, que es de donde salen "
                        + "los delete; basta con Repository y los métodos que hace falta");

        // Y lo que sí tiene que poder: registrar y contar, o la bitácora no serviría.
        assertTrue(nombres.contains("save"), "la bitácora sí se debe poder escribir");
        assertTrue(nombres.contains("count"), "las pruebas cuentan la bitácora");
        assertTrue(nombres.contains("findTop200ByOrderByFechaDescHoraDesc"),
                "la lectura de los últimos eventos sigue ahí");
    }

    /** Usuario de prueba con sesión de administrador (rol que puede leer bitácora). */
    private Usuario usuarioDePrueba() {
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() == Usuario.Rol.admin)
                .findFirst()
                .orElseGet(() -> {
                    Usuario u = new Usuario();
                    u.setNombre("Tester Bitácora");
                    u.setEmail("tester-bitacora@prueba.com");
                    u.setPassword("123456");
                    u.setRol(Usuario.Rol.admin);
                    u.setActivo(true);
                    return usuarioRepository.save(u);
                });
    }
}
