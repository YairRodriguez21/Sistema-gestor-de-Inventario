package com.sapam.inventario;

import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.repository.ProductoRepository;
import com.sapam.inventario.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El orden grueso del inventario: las cinco secciones que se ofrecen en la
 * pantalla y lo que pasa cuando un producto viene con una que no es de ellas.
 *
 * Si se guardara una sección inventada no llegaría a ningún botón y el
 * producto se quedaría huérfano, visible solo en la vista completa y sin que
 * nadie se diera cuenta.
 */
@SpringBootTest
class SeccionTest {

    @Autowired private ProductoService productoService;
    @Autowired private ProductoRepository productoRepository;

    private Producto productoDePrueba() {
        Producto producto = new Producto();
        producto.setNombre("Producto de sección " + System.nanoTime());
        return producto;
    }

    @Test
    void sonLasCincoQueLaPantallaOfreceYEnEseOrden() {
        assertEquals(List.of("Papelería", "Construcción", "Vehículos", "Uniformes", "Ferretería"),
                ProductoService.SECCIONES,
                "los botones de la fila de secciones salen de aquí");
    }

    @Test
    void unaSeccionDeLasCuatroSeGuardaYVuelveIgual() {
        Producto producto = productoDePrueba();
        producto.setSeccion("Construcción");

        Producto guardado = productoService.guardar(producto);

        assertEquals("Construcción", guardado.getSeccion());
        assertEquals("Construcción",
                productoRepository.findById(guardado.getId()).orElseThrow().getSeccion(),
                "la sección tiene que llegar a la base, no quedarse en la ventana");
    }

    @Test
    void unaSeccionInventadaNoSeGuarda() {
        Producto producto = productoDePrueba();
        producto.setSeccion("Oficina");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> productoService.guardar(producto));

        assertTrue(error.getMessage().contains("Oficina"), error.getMessage());
        assertTrue(error.getMessage().contains("Papelería"),
                "el error debe decir cuáles son las de verdad: " + error.getMessage());
        assertTrue(productoRepository.findByCodigoBarras(producto.getCodigoBarras()).isEmpty(),
                "un producto con sección inventada no debe acabar en la base");
    }

    @Test
    void laSeccionSeLimpiaDeLosEspaciosDeMas() {
        Producto producto = productoDePrueba();
        producto.setSeccion("  Uniformes  ");

        Producto guardado = productoService.guardar(producto);

        assertEquals("Uniformes", guardado.getSeccion(),
                "con espacios de sobra no coincidiría con el botón de la sección");
    }

    @Test
    void unProductoSinSeccionSigueQuedandoseEnLaBase() {
        Producto producto = productoDePrueba();

        Producto guardado = productoService.guardar(producto);

        assertNull(guardado.getSeccion(),
                "vacío se guarda como vacío, no como una sección elegida por el sistema");
        assertTrue(productoService.listarActivos().stream()
                        .anyMatch(p -> p.getId().equals(guardado.getId())),
                "un producto sin sección sigue en el inventario completo");
    }
}
