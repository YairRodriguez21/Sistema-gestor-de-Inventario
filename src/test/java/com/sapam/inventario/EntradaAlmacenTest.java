package com.sapam.inventario;

import com.sapam.inventario.entity.EntradaAlmacen;
import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.entity.ProductoEntrada;
import com.sapam.inventario.repository.AuditoriaSistemaRepository;
import com.sapam.inventario.repository.KardexRepository;
import com.sapam.inventario.repository.ProductoRepository;
import com.sapam.inventario.repository.UsuarioRepository;
import com.sapam.inventario.service.EntradaAlmacenService;
import com.sapam.inventario.service.EntradaAlmacenService.Detalle;
import com.sapam.inventario.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Entradas de almacén.
 *
 * Comprueba el requisito central de trazabilidad: al entrar material queda
 * registrado qué entró, cuánto, bajo qué folio y quién lo registró, y que el
 * kardex refleje la existencia anterior y la posterior.
 */
@SpringBootTest
class EntradaAlmacenTest {

    @Autowired private EntradaAlmacenService entradaService;
    @Autowired private ProductoService productoService;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private KardexRepository kardexRepository;
    @Autowired private AuditoriaSistemaRepository auditoriaRepository;

    @Test
    void unaEntradaAumentaExistenciasYGeneraKardex() {
        long kardexInicial = kardexRepository.count();
        long bitacoraInicial = auditoriaRepository.count();

        var usuario = usuarioPrueba();

        Producto p = new Producto();
        p.setCodigoBarras("TEST-ENT-" + System.nanoTime());
        p.setNombre("Producto para entrada");
        p.setExistenciaActual(10);
        p = productoService.guardar(p);

        EntradaAlmacen entrada = entradaService.registrar(
                usuario.getId(), "Compra de prueba",
                List.of(new Detalle(p.getId(), 5, "lote A")));

        assertNotNull(entrada.getFolio(), "la entrada debe tener folio");
        assertTrue(entrada.getFolio().startsWith("ENT-"), "el folio debe ser de entrada");

        // La existencia sube
        assertEquals(15, productoRepository.findById(p.getId()).get().getExistenciaActual(),
                "entrar 5 sobre 10 debe dejar 15");

        // El kardex refleja el antes y el después con el mismo folio
        var kardex = kardexRepository.findByProductoIdOrderByFechaDescHoraDesc(p.getId());
        assertEquals(1, kardex.size(), "la entrada debe generar una línea de kardex");
        assertEquals("ENTRADA", kardex.get(0).getTipoMovimiento());
        assertEquals(10, kardex.get(0).getExistenciaAnterior());
        assertEquals(15, kardex.get(0).getExistenciaPosterior());
        assertEquals(5, kardex.get(0).getCantidadMovida());
        assertEquals(entrada.getFolio(), kardex.get(0).getFolio(),
                "el kardex debe llevar el mismo folio que la entrada");
        assertNotNull(kardex.get(0).getUsuarioResponsable(),
                "el kardex debe registrar quién hizo el movimiento");

        assertTrue(kardexRepository.count() > kardexInicial, "el kardex debe haber crecido");
        assertTrue(auditoriaRepository.count() > bitacoraInicial, "la bitácora debe haber crecido");

        // Queda la línea de detalle (qué producto y cuánto)
        Set<ProductoEntrada> lineas = entrada.getProductosIngresados();
        assertNotNull(lineas, "la entrada debe guardar sus productos");
        assertEquals(1, lineas.size());
        assertEquals(5, lineas.iterator().next().getCantidad());

        // Y se puede recuperar por folio (es lo que escanea el lector)
        EntradaAlmacen recuperada = entradaService.buscarPorFolio(entrada.getFolio());
        assertNotNull(recuperada, "la entrada debe poder buscarse por folio");
        assertEquals(entrada.getId(), recuperada.getId());
    }

    @Test
    void unaEntradaConVariosProductosEscribeUnaLineaPorProducto() {
        var usuario = usuarioPrueba();

        Producto a = new Producto();
        a.setCodigoBarras("TEST-ENT-A-" + System.nanoTime());
        a.setNombre("Entrada multi A");
        a.setExistenciaActual(1);
        a = productoService.guardar(a);

        Producto b = new Producto();
        b.setCodigoBarras("TEST-ENT-B-" + System.nanoTime());
        b.setNombre("Entrada multi B");
        b.setExistenciaActual(2);
        b = productoService.guardar(b);

        EntradaAlmacen entrada = entradaService.registrar(
                usuario.getId(), null,
                List.of(new Detalle(a.getId(), 3, null),
                        new Detalle(b.getId(), 4, null)));

        assertEquals(4, productoRepository.findById(a.getId()).get().getExistenciaActual());
        assertEquals(6, productoRepository.findById(b.getId()).get().getExistenciaActual());

        assertEquals(1, kardexRepository.findByProductoIdOrderByFechaDescHoraDesc(a.getId()).size());
        assertEquals(1, kardexRepository.findByProductoIdOrderByFechaDescHoraDesc(b.getId()).size(),
                "cada producto de la entrada debe tener su propia línea de kardex");

        assertEquals(2, entrada.getProductosIngresados().size(),
                "la entrada debe registrar los dos productos");
    }

    @Test
    void unaEntradaInvalidaNoTocaNada() {
        var usuario = usuarioPrueba();

        Producto p = new Producto();
        p.setCodigoBarras("TEST-ENT-INV-" + System.nanoTime());
        p.setNombre("Producto de entrada inválida");
        p.setExistenciaActual(7);
        p = productoService.guardar(p);
        final Producto producto = p; // efectivamente final para usarlo en los lambdas

        long kardexInicial = kardexRepository.count();

        // Cantidad no permitida: se rechaza ANTES de modificar existencias
        assertThrows(IllegalArgumentException.class, () -> entradaService.registrar(
                usuario.getId(), null,
                List.of(new Detalle(producto.getId(), 0, null))),
                "una cantidad 0 debe rechazarse");

        assertEquals(7, productoRepository.findById(producto.getId()).get().getExistenciaActual(),
                "una entrada rechazada no debe cambiar el stock");
        assertEquals(kardexInicial, kardexRepository.count(),
                "una entrada rechazada no debe dejar líneas de kardex");

        // Sin productos tampoco
        assertThrows(IllegalArgumentException.class, () -> entradaService.registrar(
                usuario.getId(), null, List.of()),
                "una entrada vacía debe rechazarse");

        assertEquals(kardexInicial, kardexRepository.count());
    }

    private com.sapam.inventario.entity.Usuario usuarioPrueba() {
        return usuarioRepository.findAll().stream().findFirst().orElseGet(() -> {
            var u = new com.sapam.inventario.entity.Usuario();
            u.setNombre("Tester Entradas");
            u.setEmail("entradas@prueba.com");
            u.setPassword("123");
            u.setRol(com.sapam.inventario.entity.Usuario.Rol.admin);
            u.setActivo(true);
            return usuarioRepository.save(u);
        });
    }
}
