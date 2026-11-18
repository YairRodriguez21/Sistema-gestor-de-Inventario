package com.sapam.inventario;

import com.sapam.inventario.config.JwtTokenUtil;
import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.AuditoriaSistemaRepository;
import com.sapam.inventario.repository.KardexRepository;
import com.sapam.inventario.repository.ProductoRepository;
import com.sapam.inventario.repository.UsuarioRepository;
import com.sapam.inventario.service.ProductoService;
import com.sapam.inventario.service.SalidaAlmacenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica los requisitos de trazabilidad del SGIA:
 *  - kardex automático con existencia anterior/posterior
 *  - bitácora que registra la acción sin posibilidad de borrado
 *  - no se permiten productos duplicados
 *  - fecha/estado del producto no quedan en NULL
 */
@SpringBootTest
@AutoConfigureMockMvc
class TrazabilidadTest {

    @Autowired private ProductoService productoService;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private SalidaAlmacenService salidaAlmacenService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private KardexRepository kardexRepository;
    @Autowired private AuditoriaSistemaRepository auditoriaRepository;
    @Autowired private JwtTokenUtil jwtTokenUtil;
    @Autowired private MockMvc mockMvc;

    @Test
    void salidaGeneraKardexYBitacora() {
        long kardexInicial = kardexRepository.count();
        long bitacoraInicial = auditoriaRepository.count();

        // H2 arranca vacío: crear el usuario responsable del movimiento
        var usuario = usuarioRepository.findAll().stream().findFirst().orElseGet(() -> {
            var u = new Usuario();
            u.setNombre("Tester");
            u.setEmail("tester@prueba.com");
            u.setPassword("123");
            u.setRol(Usuario.Rol.admin);
            u.setActivo(true);
            return usuarioRepository.save(u);
        });

        Producto p = new Producto();
        p.setCodigoBarras("TEST-KDX-" + System.nanoTime());
        p.setNombre("Producto prueba kardex");
        p.setExistenciaActual(10);
        p = productoService.guardar(p);

        // Requisito: fecha y estado no deben guardarse en NULL
        assertNotNull(p.getFechaCreacion(), "fecha_creacion no debe ser NULL");
        assertNotNull(p.getUltimaActualizacion(), "ultima_actualizacion no debe ser NULL");
        assertEquals("ACTIVO", p.getEstado(), "estado debe ser ACTIVO por defecto");
        assertNotNull(p.getCodigoInterno(), "codigoInterno no debe ser NULL");

        salidaAlmacenService.registrar(p.getId(), 3, usuario.getId(), "Ana Torres",
                "Prueba", null, "Operaciones", "OTRO", "test");

        var kardex = kardexRepository.findByProductoIdOrderByFechaDescHoraDesc(p.getId());
        assertEquals(1, kardex.size(), "la salida debe generar una línea de kardex");
        assertEquals(10, kardex.get(0).getExistenciaAnterior());
        assertEquals(7, kardex.get(0).getExistenciaPosterior());
        assertEquals(-3, kardex.get(0).getCantidadMovida());
        assertEquals("SALIDA", kardex.get(0).getTipoMovimiento());
        assertNotNull(kardex.get(0).getUsuarioResponsable(), "kardex debe guardar quién hizo el movimiento");

        assertTrue(kardexRepository.count() > kardexInicial, "kardex debe haber crecido");
        assertTrue(auditoriaRepository.count() > bitacoraInicial, "bitácora debe haber crecido");

        assertEquals(7, productoRepository.findById(p.getId()).get().getExistenciaActual(),
                "la existencia debe descontarse");
    }

    /**
     * El historial de kardex también se sirve por HTTP (solo lectura, y solo
     * para administrador: es de lo más sensible que hay).
     *
     * Sirve para verificar el kardex desde fuera de la aplicación de escritorio,
     * por ejemplo escaneando el código del producto.
     */
    @Test
    void laApiDeKardexSirveElHistorialDelProducto() throws Exception {
        Producto p = new Producto();
        p.setCodigoBarras("TEST-KDX-API-" + System.nanoTime());
        p.setNombre("Producto prueba kardex por API");
        p.setExistenciaActual(10);
        p = productoService.guardar(p);

        var usuario = usuarioAdministrador();
        salidaAlmacenService.registrar(p.getId(), 4, usuario.getId(), "Ana Torres",
                "Prueba", null, "Operaciones", "OTRO", "test por API");

        String token = "Bearer " + jwtTokenUtil.generateToken(usuario.getEmail());

        mockMvc.perform(get("/api/kardex/producto/" + p.getId()).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productoId").value(p.getId()))
                .andExpect(jsonPath("$[0].tipoMovimiento").value("SALIDA"))
                .andExpect(jsonPath("$[0].cantidadMovida").value(-4))
                .andExpect(jsonPath("$[0].existenciaAnterior").value(10))
                .andExpect(jsonPath("$[0].existenciaPosterior").value(6))
                .andExpect(jsonPath("$[0].responsable").value(usuario.getNombre()));

        // Sin sesión no se lee.
        int estado = mockMvc.perform(get("/api/kardex/producto/" + p.getId()))
                .andReturn().getResponse().getStatus();
        assertTrue(estado == 401 || estado == 403,
                "el kardex no debe leerse sin sesión; devolvió " + estado);
    }

    /** Usuario con sesión de administrador (rol que puede leer el kardex). */
    private Usuario usuarioAdministrador() {
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() == Usuario.Rol.admin)
                .findFirst()
                .orElseGet(() -> {
                    var u = new Usuario();
                    u.setNombre("Tester Admin");
                    u.setEmail("tester-admin-kardex@prueba.com");
                    u.setPassword("123");
                    u.setRol(Usuario.Rol.admin);
                    u.setActivo(true);
                    return usuarioRepository.save(u);
                });
    }

    @Test
    void noSePermitenProductosDuplicados() {
        Producto a = new Producto();
        a.setCodigoBarras("TEST-DUP-" + System.nanoTime());
        a.setNombre("Duplicado A");
        productoService.guardar(a);

        Producto b = new Producto();
        b.setCodigoBarras(a.getCodigoBarras());
        b.setNombre("Duplicado B");

        assertThrows(IllegalArgumentException.class, () -> productoService.guardar(b),
                "un código de barras repetido debe rechazarse con mensaje claro");
    }

    @Test
    void eliminarProductoEsBajaLogica() {
        Producto nuevo = new Producto();
        nuevo.setCodigoBarras("TEST-BAJA-" + System.nanoTime());
        nuevo.setNombre("Producto a dar de baja");
        Producto producto = productoService.guardar(nuevo);
        assertTrue(productoService.listarActivos().stream().anyMatch(a -> a.getId().equals(producto.getId())),
                "recién creado aparece en el inventario");

        productoService.eliminar(producto.getId());

        assertTrue(productoRepository.findById(producto.getId()).isPresent(),
                "el producto no debe borrarse de la BD (baja lógica para conservar auditoría)");
        assertEquals("BAJAS", productoRepository.findById(producto.getId()).get().getEstado());
        assertFalse(productoService.listarActivos().stream().anyMatch(a -> a.getId().equals(producto.getId())),
                "se va de la lista que pinta la tabla: si no, el botón de eliminar "
                        + "parece no hacer nada y el usuario repite el clic");
        assertTrue(productoService.listarTodos().stream().anyMatch(a -> a.getId().equals(producto.getId())),
                "listarTodos() lo sigue trayendo para el kardex y la bitácora");
    }
}
