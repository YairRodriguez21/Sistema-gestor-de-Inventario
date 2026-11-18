package com.sapam.inventario.controller;

import com.sapam.inventario.repository.EntradaAlmacenRepository;
import com.sapam.inventario.repository.HerramientaRepository;
import com.sapam.inventario.repository.ProductoRepository;
import com.sapam.inventario.repository.SalidaAlmacenRepository;
import com.sapam.inventario.service.CodigoBarrasService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * Devuelve la imagen PNG del código de barras de cada registro.
 *
 * Estas cuatro rutas ya estaban declaradas en {@code SecurityConfig} como
 * públicas. El texto que codifica la barra es el mismo identificador que la
 * aplicación busca al escanear —el que devuelve
 * {@code getCodigoEscaneable()}—:
 *
 * <ul>
 *   <li>producto → su código de barras ({@code 10001})</li>
 *   <li>herramienta → su código ({@code HER-...})</li>
 *   <li>entrada / salida → su folio</li>
 * </ul>
 *
 * Si el registro no existe responde 404: nunca se imprime un código que no
 * corresponda a algo real.
 */
@RestController
public class CodigoBarrasController {

    private final CodigoBarrasService codigoBarrasService;
    private final ProductoRepository productoRepository;
    private final EntradaAlmacenRepository entradaAlmacenRepository;
    private final SalidaAlmacenRepository salidaAlmacenRepository;
    private final HerramientaRepository herramientaRepository;

    public CodigoBarrasController(CodigoBarrasService codigoBarrasService,
                        ProductoRepository productoRepository,
                        EntradaAlmacenRepository entradaAlmacenRepository,
                        SalidaAlmacenRepository salidaAlmacenRepository,
                        HerramientaRepository herramientaRepository) {
        this.codigoBarrasService = codigoBarrasService;
        this.productoRepository = productoRepository;
        this.entradaAlmacenRepository = entradaAlmacenRepository;
        this.salidaAlmacenRepository = salidaAlmacenRepository;
        this.herramientaRepository = herramientaRepository;
    }

    // ============================================================
    // Productos
    // ============================================================
    @GetMapping("/api/productos/codigo-barras/{codigo}")
    public ResponseEntity<byte[]> codigoBarrasProducto(@PathVariable String codigo) {
        // Se acepta el código de barras (lo que lleva la etiqueta) y también el
        // interno, por si quedan enlaces o etiquetas viejas con ese valor.
        return productoRepository.findByCodigoBarras(codigo)
                .or(() -> productoRepository.findByCodigoInterno(codigo))
                .map(p -> png(p.getCodigoEscaneable()))
                .orElseGet(ResponseEntity.notFound()::build);
    }

    // ============================================================
    // Herramientas
    // ============================================================
    @GetMapping("/api/herramientas/codigo-barras/{codigo}")
    public ResponseEntity<byte[]> codigoBarrasHerramienta(@PathVariable String codigo) {
        return herramientaRepository.findByCodigoBarras(codigo)
                .map(h -> png(h.getCodigoEscaneable()))
                .orElseGet(ResponseEntity.notFound()::build);
    }

    // ============================================================
    // Entradas de almacén
    // ============================================================
    @GetMapping("/api/entradas/codigo-barras/{folio}")
    public ResponseEntity<byte[]> codigoBarrasEntrada(@PathVariable String folio) {
        return entradaAlmacenRepository.findByFolio(folio)
                .map(e -> png(e.getFolio()))
                .orElseGet(ResponseEntity.notFound()::build);
    }

    // ============================================================
    // Salidas de almacén
    // ============================================================
    @GetMapping("/api/salidas/codigo-barras/{folio}")
    public ResponseEntity<byte[]> codigoBarrasSalida(@PathVariable String folio) {
        return salidaAlmacenRepository.findByFolio(folio)
                .map(s -> png(s.getFolio()))
                .orElseGet(ResponseEntity.notFound()::build);
    }

    // ============================================================
    // Utilidades
    // ============================================================

    private ResponseEntity<byte[]> png(String contenido) {
        try {
            byte[] imagen = codigoBarrasService.png(contenido, CodigoBarrasService.TAMANO_DEFECTO);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"codigo-de-barras.png\"")
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                    .body(imagen);
        } catch (IllegalArgumentException e) {
            // Contenido no codificable
            return ResponseEntity.badRequest().build();
        }
    }
}
