package com.sapam.inventario;

import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.repository.ProductoRepository;
import com.sapam.inventario.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El código de barras ya no lo teclea el usuario: lo asigna el sistema en la
 * serie interna que arranca en 10001.
 *
 * Antes el formulario lo exigía a mano aunque el código sí se generaba solo, y un
 * campo olvidado bloqueaba el alta. Aquí se comprueba que el alta ya no pide
 * código, que dos productos no chocan y que un código ajeno no arrastra el
 * correlativo.
 */
@SpringBootTest
class CodigoBarrasTest {

    @Autowired private ProductoService productoService;
    @Autowired private ProductoRepository productoRepository;

    private Producto productoSinCodigo() {
        Producto p = new Producto();
        p.setNombre("Producto sin código de barras");
        p.setExistenciaActual(1);
        return p;
    }

    private Producto conCodigoExterno(String codigo) {
        Producto p = new Producto();
        p.setCodigoBarras(codigo);
        p.setNombre("Producto con código de proveedor");
        p.setExistenciaActual(1);
        return p;
    }

    @Test
    void alGuardarSinCodigoElSistemaAsignaElSiguienteDeLaSerie() {
        Producto guardado = productoService.guardar(productoSinCodigo());

        assertNotNull(guardado.getCodigoBarras(), "el alta no puede quedarse sin código");
        assertTrue(guardado.getCodigoBarras().matches("1\\d{4}"),
                "debe continuar la serie interna, llegó: " + guardado.getCodigoBarras());
        assertTrue(productoRepository.findByCodigoBarras(guardado.getCodigoBarras()).isPresent(),
                "el código generado debe existir en la base");
    }

    @Test
    void dosProductosSinCodigoNoSeLlevanElMismo() {
        String primero = productoService.guardar(productoSinCodigo()).getCodigoBarras();
        String segundo = productoService.guardar(productoSinCodigo()).getCodigoBarras();

        assertNotEquals(primero, segundo, "la serie no puede repetir código");
        assertTrue(productoRepository.findByCodigoBarras(segundo).isPresent());
    }

    @Test
    void unCodigoAjenoNoArrastraElCorrelativo() {
        productoService.guardar(conCodigoExterno("99001"));

        String generado = productoService.guardar(productoSinCodigo()).getCodigoBarras();

        assertNotEquals("99002", generado,
                "un código de proveedor no debe mover el correlativo interno");
        assertTrue(generado.matches("1\\d{4}"),
                "debe volver a la serie interna, llegó: " + generado);
    }

    @Test
    void unCodigoExternoDelClienteSeRespeta() {
        Producto guardado = productoService.guardar(conCodigoExterno("  7501234567890  "));

        assertEquals("7501234567890", guardado.getCodigoBarras(),
                "si llega un código escrito solo debe recortarse, no sustituirse");
    }

    @Test
    void unCodigoRepetidoSigueSiendoError() {
        Producto guardado = productoService.guardar(conCodigoExterno("99002"));

        Producto duplicado = conCodigoExterno(guardado.getCodigoBarras());
        duplicado.setNombre("Otro producto con el mismo código");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> productoService.guardar(duplicado));
        assertTrue(error.getMessage().contains("Ya existe un producto con el código"),
                error.getMessage());
    }

    @Test
    void sinCodigoAsignadoNoSeObligaAlUsuarioATeclarlo() {
        // El sistema asigna el código, así que la única validación que puede
        // saltar aquí es la del nombre.
        Producto sinNombre = new Producto();
        sinNombre.setExistenciaActual(1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> productoService.guardar(sinNombre));
        assertTrue(error.getMessage().contains("nombre"), error.getMessage());
    }
}
