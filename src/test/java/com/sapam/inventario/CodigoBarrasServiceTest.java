package com.sapam.inventario;

import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.service.ProductoService;
import com.sapam.inventario.service.CodigoBarrasService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifica que el código de barras se genera, se lee y se sirve por HTTP de
 * verdad:
 *  - el texto codificado se puede decodificar de vuelta (ida y vuelta)
 *  - la imagen sale apaisada, que es la proporción de una etiqueta
 *  - los endpoints devuelven PNG real y 404 si no existe el registro
 *  - escanear el código encuentra el producto en la búsqueda
 *
 * El lector que se usa es de una sola dimensión: si estas pruebas fallan,
 * etiqueta que se imprime es etiqueta que no se puede leer.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CodigoBarrasServiceTest {

    @Autowired private CodigoBarrasService codigoBarrasService;
    @Autowired private ProductoService productoService;
    @Autowired private MockMvc mockMvc;

    // ------------------------------------------------------------
    // Generación y lectura
    // ------------------------------------------------------------

    @Test
    void elCodigoSePuedeDecodificarDeVuelta() {
        // Un código con la longitud de los reales: el de una herramienta
        String contenido = "HER-" + Long.toString(System.nanoTime(), 36).toUpperCase();

        BufferedImage imagen = codigoBarrasService.generar(contenido, CodigoBarrasService.TAMANO_DEFECTO);

        assertNotNull(imagen, "debe producir una imagen");
        assertEquals(CodigoBarrasService.TAMANO_DEFECTO, imagen.getWidth(), "debe respetar el ancho pedido");
        assertTrue(imagen.getHeight() < imagen.getWidth(),
                "la imagen debe salir apaisada, no cuadrada");
        assertTrue(imagen.getHeight() >= 80,
                "las barras necesitan alto para que el lector las agarre");

        Optional<String> leido = codigoBarrasService.decodificar(imagen);
        assertTrue(leido.isPresent(), "el código generado debe poder leerse");
        assertEquals(contenido, leido.get(), "el contenido de ida y vuelta debe ser idéntico");
    }

    @Test
    void elPngTieneLaCabeceraDeImagenPng() throws Exception {
        byte[] png = codigoBarrasService.png("10001", 160);

        assertTrue(png.length > 8, "debe tener contenido");
        // Firma PNG: 89 50 4E 47 0D 0A 1A 0A
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]);
        assertEquals((byte) 0x4E, png[2]);
        assertEquals((byte) 0x47, png[3]);

        // Además debe poderse interpretar como imagen real
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(imagen, "los bytes deben ser una imagen PNG válida");
    }

    @Test
    void unCodigoSinContenidoNoSeGenera() {
        assertThrows(IllegalArgumentException.class, () -> codigoBarrasService.generar(null, 320));
        assertThrows(IllegalArgumentException.class, () -> codigoBarrasService.generar("   ", 320));
    }

    @Test
    void elPngServidoSePuedeEscanearYDevuelveElCodigoDeLaEtiqueta() throws Exception {
        String codigoInterno = "INT-SERVED-" + System.nanoTime();
        String codigoBarras = Long.toString(System.nanoTime(), 36);
        Producto p = new Producto();
        p.setCodigoBarras(codigoBarras);
        p.setNombre("Producto servido");
        p.setExistenciaActual(1);
        p.setCodigoInterno(codigoInterno);
        p = productoService.guardar(p);

        // Se pide por el identificador interno (así estaban los enlaces viejos)
        // y lo que sale es la barra con el código que lleva la etiqueta.
        byte[] cuerpo = mockMvc.perform(get("/api/productos/codigo-barras/" + codigoInterno))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andReturn().getResponse().getContentAsByteArray();

        // Es exactamente lo que haría un lector apuntando a la etiqueta.
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(cuerpo));
        assertNotNull(imagen, "el cuerpo servido debe ser una imagen válida");
        assertEquals(Optional.of(codigoBarras), codigoBarrasService.decodificar(imagen),
                "el código servido debe ser el que lleva impresa la etiqueta");

        // Y se puede pedir también por ese mismo código de barras
        mockMvc.perform(get("/api/productos/codigo-barras/" + codigoBarras))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void unaImagenSinCodigoDevuelveVacio() {
        BufferedImage sinCodigo = new BufferedImage(80, 80, BufferedImage.TYPE_INT_RGB);
        assertTrue(codigoBarrasService.decodificar(sinCodigo).isEmpty(),
                "una imagen sin código no debe devolver contenido");
    }

    // ------------------------------------------------------------
    // Endpoints HTTP
    // ------------------------------------------------------------

    @Test
    void elEndpointDeUnProductoExistenteDevuelvePng() throws Exception {
        Producto p = new Producto();
        p.setCodigoBarras("TEST-BAR-" + System.nanoTime());
        p.setNombre("Producto con código");
        p.setExistenciaActual(5);
        p.setCodigoInterno("INT-TEST-" + System.nanoTime());
        p = productoService.guardar(p);

        mockMvc.perform(get("/api/productos/codigo-barras/" + p.getCodigoInterno()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void elEndpointDeUnProductoInexistenteDevuelve404() throws Exception {
        // Es público (permitAll en SecurityConfig): no debe pedir token,
        // pero tampoco inventar un código para algo que no existe.
        mockMvc.perform(get("/api/productos/codigo-barras/INT-QUE-NO-EXISTE"))
                .andExpect(status().isNotFound());
    }

    @Test
    void elEndpointDeHerramientaSinRegistroDevuelve404() throws Exception {
        mockMvc.perform(get("/api/herramientas/codigo-barras/HER-SIN-HERRAMIENTA"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/entradas/codigo-barras/FOLIO-INEXISTENTE"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/salidas/codigo-barras/FOLIO-INEXISTENTE"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------
    // Escaneo
    // ------------------------------------------------------------

    @Test
    void escanearElCodigoEncuentraElProducto() {
        String codigoBarras = Long.toString(System.nanoTime(), 36);
        Producto p = new Producto();
        p.setCodigoBarras(codigoBarras);
        p.setNombre("Producto escaneable");
        p.setExistenciaActual(1);
        p.setCodigoInterno("INT-SCAN-" + System.nanoTime());
        p = productoService.guardar(p);

        // Esto es exactamente lo que hace la app cuando el lector USB
        // escribe el contenido del código en la caja de búsqueda.
        Optional<Producto> encontrado = productoService.buscarPorCodigoBarrasOInterno(codigoBarras);

        assertTrue(encontrado.isPresent(), "el código escaneado debe encontrar el producto");
        assertEquals(p.getId(), encontrado.get().getId());
    }
}
