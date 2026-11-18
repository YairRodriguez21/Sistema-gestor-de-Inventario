package com.sapam.inventario;

import com.sapam.inventario.config.JwtTokenUtil;
import com.sapam.inventario.entity.Herramienta;
import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.ProductoRepository;
import com.sapam.inventario.repository.UsuarioRepository;
import com.sapam.inventario.service.BitacoraService;
import com.sapam.inventario.service.HerramientaService;
import com.sapam.inventario.service.ProductoService;
import com.sapam.inventario.service.SalidaAlmacenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La API por HTTP.
 *
 * Comprueba sobre todo lo que la API <em>no</em> deja hacer, porque ahí es
 * donde se juega la trazabilidad:
 *
 * <ul>
 *   <li>nada se registra sin sesión, y el responsable sale del token y no del
 *       cuerpo de la petición, para que nadie firme por otra cuenta;</li>
 *   <li>el código de una herramienta lo pone el sistema, no el cliente, porque
 *       es el de la etiqueta impresa;</li>
 *   <li>editar y dar de baja es de administrador;</li>
 *   <li>la lista de usuarios nunca devuelve la contraseña.</li>
 * </ul>
 *
 * Y comprueba que los endpoints que {@code SecurityConfig} anunciaba para el
 * usuario operativo existen de verdad: antes eran reglas que apuntaban a
 * rutas que no había.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiRestTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenUtil jwtTokenUtil;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ProductoService productoService;
    @Autowired private HerramientaService herramientaService;
    @Autowired private BitacoraService bitacoraService;

    // ---------------------------------------------------------------- sesión

    @Test
    void lasEntradasYLasSalidasNoSeRegistranSinSesion() throws Exception {
        String cuerpo = "{\"productoId\":1,\"cantidad\":1,\"personaRecibe\":\"x\","
                + "\"personaSolicita\":\"x\",\"departamentoSolicita\":\"x\","
                + "\"motivoSalida\":\"" + SalidaAlmacenService.MOTIVOS.get(0) + "\"}";

        for (String ruta : List.of("/api/entradas", "/api/salidas")) {
            mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(resultado -> {
                        int estado = resultado.getResponse().getStatus();
                        assertTrue(estado == 401 || estado == 403,
                                ruta + " no debe registrarse sin sesión; devolvió " + estado);
                    });
        }
    }

    @Test
    void lasRutasQueQuedaronSinReglaExigenSesion() throws Exception {
        // Se quitaron del SecurityConfig porque apuntaban a endpoints que no
        // existen; el punto es que al quitarlas no hayan quedado abiertas.
        for (String ruta : List.of("/api/admin/ping", "/api/roles",
                "/api/consulta/x", "/api/operativo/x")) {
            mockMvc.perform(get(ruta))
                    .andExpect(resultado -> {
                        int estado = resultado.getResponse().getStatus();
                        assertTrue(estado == 401 || estado == 403,
                                ruta + " sigue exigiendo sesión; devolvió " + estado);
                    });
        }
    }

    @Test
    void losEndpointsQueAnunciaSeguridadParaElOperativoSiExisten() throws Exception {
        Usuario operativo = usuario("operativo-api-" + System.nanoTime() + "@prueba.com",
                Usuario.Rol.usuario);

        for (String ruta : List.of("/api/prestamos/activos")) {
            mockMvc.perform(get(ruta).header("Authorization", token(operativo)))
                    .andExpect(status().isOk());
        }
    }

    // ------------------------------------------------- responsable de sesión

    @Test
    void laEntradaQuedaAFavorDeQuienFirmoLaSesion() throws Exception {
        Usuario operativo = usuario("operativo-ent-" + System.nanoTime() + "@prueba.com",
                Usuario.Rol.usuario);
        Producto producto = productoDePrueba();
        int existia = productoRepository.findById(producto.getId()).get().getExistenciaActual();

        // "usuarioId" no existe en el cuerpo: si hiciera caso el cliente podría
        // registrar una entrada a nombre de otra cuenta y negarla después.
        String cuerpo = """
                {"usuarioId": 999999,
                 "observaciones": "entrada hecha por la API",
                 "productos": [{"productoId": %d, "cantidad": 4}]}
                """.formatted(producto.getId());

        mockMvc.perform(post("/api/entradas")
                        .header("Authorization", token(operativo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folio").value(startsWith("ENT-")))
                .andExpect(jsonPath("$.responsable").value(operativo.getNombreCompleto()))
                .andExpect(jsonPath("$.productos[0].producto").value(producto.getNombre()));

        assertEquals(existia + 4,
                productoRepository.findById(producto.getId()).get().getExistenciaActual(),
                "la entrada registrada por la API debe subir la existencia igual que por la ventana");
    }

    @Test
    void laSalidaSeRegistraConOperativoPeroNoConConsulta() throws Exception {
        Usuario operativo = usuario("operativo-sal-" + System.nanoTime() + "@prueba.com",
                Usuario.Rol.usuario);
        Usuario consulta = usuario("consulta-sal-" + System.nanoTime() + "@prueba.com",
                Usuario.Rol.consulta);
        Producto producto = productoDePrueba();

        String cuerpo = """
                {"productoId": %d, "cantidad": 1,
                 "personaRecibe": "Obrero de taller",
                 "puestoRecibe": "Operario",
                 "departamentoRecibe": "Campo norte",
                 "personaSolicita": "Jefe de mantenimiento",
                 "puestoSolicita": "Jefe",
                 "departamentoSolicita": "Mantenimiento",
                 "motivoSalida": "%s"}
                """.formatted(producto.getId(), SalidaAlmacenService.MOTIVOS.get(0));

        mockMvc.perform(post("/api/salidas")
                        .header("Authorization", token(operativo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folio").value(startsWith("SAL-")))
                .andExpect(jsonPath("$.responsable").value(operativo.getNombreCompleto()))
                .andExpect(jsonPath("$.personaRecibe").value("Obrero de taller"))
                .andExpect(jsonPath("$.puestoRecibe").value("Operario"))
                .andExpect(jsonPath("$.departamentoRecibe").value("Campo norte"));

        // El rol Consulta existe para leer reportes, no para mover almacén.
        mockMvc.perform(post("/api/salidas")
                        .header("Authorization", token(consulta))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isForbidden());
    }

    // ----------------------------------------------------- código de etiqueta

    /**
     * El código de etiqueta lo pone el sistema y así lo deja el servicio; aquí
     * solo se comprueba con la única cuenta que sí puede dar de alta.
     */
    @Test
    void elCodigoNiSePideNiSeAceptaEnElAltaDeUnaHerramienta() throws Exception {
        Usuario admin = usuario("admin-her-" + System.nanoTime() + "@prueba.com",
                Usuario.Rol.admin);

        String cuerpo = """
                {"nombre": "Taladro por API", "marca": "Bosch",
                 "codigoBarras": "INT-QUE-NO-DEBE-QUEDAR",
                 "fechaEstimadaDevolucion": "2030-01-01",
                 "estado": "BAJAS"}
                """;

        mockMvc.perform(post("/api/herramientas")
                        .header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoBarras").value(startsWith("HER-")))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.fechaEstimadaDevolucion").doesNotExist());
    }

    // ------------------------------------------------------------ prestamo

    @Test
    void elOperativoPuedePrestarYDevolverPorApi() throws Exception {
        Usuario operativo = usuario("operativo-pre-" + System.nanoTime() + "@prueba.com",
                Usuario.Rol.usuario);
        Herramienta herramienta = herramientaNueva();

        String prestamo = """
                {"herramientaId": %d,
                 "quienRecibe": "Roberto Mantenimiento",
                 "areaResponsable": "Almacén",
                 "destino": "Obra norte",
                 "fechaEstimadaDevolucion": "%s"}
                """.formatted(herramienta.getId(), LocalDate.now().plusDays(5));

        mockMvc.perform(post("/api/herramientas/prestamo")
                        .header("Authorization", token(operativo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prestamo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personaRecibe").value("Roberto Mantenimiento"))
                .andExpect(jsonPath("$.destino").value("Obra norte"))
                .andExpect(jsonPath("$.personaEntrega").value(operativo.getNombre()));

        Long prestamoId = herramientaService.prestamoActivo(herramienta.getId()).orElseThrow().getId();

        mockMvc.perform(post("/api/herramientas/devolver")
                        .header("Authorization", token(operativo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prestamoId": %d, "fechaDevolucion": "%s",
                                 "estadoFisico": "BUENO"}
                                """.formatted(prestamoId, LocalDate.now())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DEVUELTO"));

        assertTrue(herramientaService.prestamoActivo(herramienta.getId()).isEmpty(),
                "la devolución por la API debe cerrar el préstamo");
    }

    // ---------------------------------------------------- edición y baja

    /**
     * El operativo solo presta y devuelve. No da de alta, no edita y no da de
     * baja: meter piezas al catálogo y sacarlas es cosa de administrador, y
     * quien mueve almacén no decide eso.
     */
    @Test
    void elOperativoSoloPrestaYNiDeAltaNiEditaNiDaDeBaja() throws Exception {
        Usuario operativo = usuario("operativo-ed-" + System.nanoTime() + "@prueba.com",
                Usuario.Rol.usuario);
        Usuario admin = usuario("admin-ed-" + System.nanoTime() + "@prueba.com",
                Usuario.Rol.admin);
        Herramienta herramienta = herramientaNueva();

        // Ver la ficha sí: es lo que hace el operativo antes de prestarla.
        mockMvc.perform(get("/api/herramientas/" + herramienta.getId())
                        .header("Authorization", token(operativo)))
                .andExpect(status().isOk());

        // El alta tampoco. La ventana ya le oculta el botón a ese rol; aquí se
        // cierra la misma puerta para quien la llame con su token.
        mockMvc.perform(post("/api/herramientas")
                        .header("Authorization", token(operativo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Alta que no debe pasar\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/herramientas/" + herramienta.getId())
                        .header("Authorization", token(operativo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Cambiada por el operativo\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/herramientas/" + herramienta.getId())
                        .header("Authorization", token(operativo)))
                .andExpect(status().isForbidden());

        assertEquals("ACTIVO", herramientaService.porId(herramienta.getId()).get().getEstado(),
                "un rechazo no debe ni editar ni dar de baja");

        mockMvc.perform(delete("/api/herramientas/" + herramienta.getId())
                        .header("Authorization", token(admin)))
                .andExpect(status().isOk());

        assertEquals("BAJAS", herramientaService.porId(herramienta.getId()).get().getEstado(),
                "la baja de administrador debe ser lógica y dejar el registro en pie");
    }

    // -------------------------------------------------------------- usuarios

    @Test
    void laListaDeUsuariosNoFugaLasContrasenas() throws Exception {
        Usuario admin = usuario("admin-us-" + System.nanoTime() + "@prueba.com",
                Usuario.Rol.admin);

        mockMvc.perform(get("/api/usuarios").header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].password").doesNotExist())
                .andExpect(jsonPath("$[*].email").exists());

        mockMvc.perform(get("/api/usuarios/" + admin.getId())
                        .header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.rol").value("admin"));
    }

    @Test
    void laBajaDeUnUsuarioEsLogicaYQuedaEnLaBitacora() throws Exception {
        Usuario admin = usuario("admin-baj-" + System.nanoTime() + "@prueba.com",
                Usuario.Rol.admin);
        String email = "cuenta-baja-" + System.nanoTime() + "@prueba.com";

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Cuenta por API", "email": "%s",
                                 "password": "123456", "rol": "OPERATIVO"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.rol").value("usuario"))
                .andExpect(jsonPath("$.password").doesNotExist());

        Integer id = usuarioRepository.findByEmail(email).orElseThrow().getId();

        mockMvc.perform(delete("/api/usuarios/" + id)
                        .header("Authorization", token(admin)))
                .andExpect(status().isOk());

        Usuario dadoDeBaja = usuarioRepository.findById(id).orElseThrow();
        assertFalse(dadoDeBaja.getActivo(),
                "la baja debe ser lógica: la cuenta deja de entrar pero la fila se conserva");
        assertNotNull(usuarioRepository.findById(id).orElse(null),
                "borrar la cuenta destruiría la trazabilidad de todo lo que ya registró");
        assertTrue(bitacoraService.porAccion(BitacoraService.BAJA_USUARIO).stream()
                        .anyMatch(e -> e.getInformacionModificada() != null
                                && e.getInformacionModificada().contains(email)),
                "la baja debe quedar en la bitácora con la cuenta afectada");
    }

    // -------------------------------------------------------------- productos

    @Test
    void laFichaDeUnProductoLlevaLaSeccionYNoLaCategoriaBorrada() throws Exception {
        Usuario admin = usuario("admin-seccion-" + System.nanoTime() + "@prueba.com",
                Usuario.Rol.admin);

        Producto producto = productoDePrueba();
        producto.setSeccion("Ferretería");
        productoService.guardar(producto);

        // La categoría se borró del inventario (sección, categoria, entidad,
        // tabla): ni la ficha ni el listado deben traerla en el JSON, y la
        // sección sí. Antes este mismo campo anidado hacía pesar la ficha
        // kilobytes porque categoría volvía a incluir sus productos.
        String ficha = mockMvc.perform(get("/api/productos/" + producto.getId())
                        .header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seccion").value("Ferretería"))
                .andExpect(jsonPath("$.categoria").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertTrue(ficha.length() < 2000,
                "la ficha de un producto pesó " + ficha.length() + " caracteres");

        mockMvc.perform(get("/api/productos").header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].categoria").doesNotExist());
    }

    // --------------------------------------------------------- baja de producto

    /**
     * El botón de eliminar (y el DELETE de la API) es una baja lógica: el
     * producto se quita del listado, que es lo único que ve quien lo pulsó, y
     * su ficha se conserva con estado BAJAS para el kardex y la bitácora.
     */
    @Test
    void elListadoDeLaApiDejaFueraLoDadoDeBajaPeroConservaLaFicha() throws Exception {
        Usuario admin = usuario("admin-baja-" + System.nanoTime() + "@prueba.com",
                Usuario.Rol.admin);

        Producto producto = productoDePrueba();
        mockMvc.perform(delete("/api/productos/" + producto.getId())
                        .header("Authorization", token(admin)))
                .andExpect(status().isOk());

        String listado = mockMvc.perform(get("/api/productos")
                        .header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(listado.contains("\"id\":" + producto.getId() + ","),
                "el producto dado de baja sigue saliendo en el listado del inventario");

        mockMvc.perform(get("/api/productos/" + producto.getId())
                        .header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BAJAS"));
    }

    // ------------------------------------------------------- matriz de roles

    /**
     * La ventana oculta los botones que no le tocan a cada rol, pero eso solo
     * evita la duda: si la API permitiera lo mismo que la ventana oculta,
     * cualquiera con un token podría hacerlo por fuera. Aquí se comprueba la
     * otra mitad de la regla.
     *
     * El operativo ahora lee kardex y reportes —antes eran de Administrador y
     * de Consulta, y le devolvían 403 a quien debía usarlos— y ya no lee la
     * bitácora ni las cuentas.
     */
    @Test
    void laApiSeAjustaALoQueCadaRolPuedeVerEnPantalla() throws Exception {
        Usuario operativo = usuario("operativo-matriz-" + System.nanoTime() + "@prueba.com",
                Usuario.Rol.usuario);
        Usuario consulta = usuario("consulta-matriz-" + System.nanoTime() + "@prueba.com",
                Usuario.Rol.consulta);
        Usuario admin = usuario("admin-matriz-" + System.nanoTime() + "@prueba.com",
                Usuario.Rol.admin);
        Producto producto = productoDePrueba();

        // Mueve almacén: necesita el kardex para saber qué pasó con la
        // existencia, y el reporte para saber qué hay.
        mockMvc.perform(get("/api/kardex/producto/" + producto.getId())
                        .header("Authorization", token(operativo)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reportes/existencias-actuales")
                        .header("Authorization", token(operativo)))
                .andExpect(status().isOk());

        // La bitácora es la pantalla de quién hizo qué: no es de operativo.
        mockMvc.perform(get("/api/auditoria/ultimos")
                        .header("Authorization", token(operativo)))
                .andExpect(status().isForbidden());

        // Tampoco administra cuentas.
        mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", token(operativo)))
                .andExpect(status().isForbidden());

        // Consulta lee la bitácora —es de solo lectura, y ese es su papel—
        // pero no administra cuentas.
        mockMvc.perform(get("/api/auditoria/ultimos")
                        .header("Authorization", token(consulta)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", token(consulta)))
                .andExpect(status().isForbidden());

        // Administrador entra a todo.
        mockMvc.perform(get("/api/auditoria/ultimos")
                        .header("Authorization", token(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", token(admin)))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------- auxiliares

    private String token(Usuario usuario) {
        return "Bearer " + jwtTokenUtil.generateToken(usuario.getEmail());
    }

    private Usuario usuario(String email, Usuario.Rol rol) {
        return usuarioRepository.findByEmail(email).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNombre("Tester API " + rol.name());
            u.setEmail(email);
            u.setPassword("123456");
            u.setRol(rol);
            u.setActivo(true);
            return usuarioRepository.save(u);
        });
    }

    private Producto productoDePrueba() {
        Producto p = new Producto();
        p.setCodigoBarras("TEST-API-" + System.nanoTime());
        p.setNombre("Producto para la API");
        p.setExistenciaActual(50);
        return productoService.guardar(p);
    }

    private Herramienta herramientaNueva() {
        Herramienta h = new Herramienta();
        h.setNombre("Herramienta para la API " + System.nanoTime());
        return herramientaService.alta(h,
                usuario("admin-her-" + System.nanoTime() + "@prueba.com", Usuario.Rol.admin));
    }
}
