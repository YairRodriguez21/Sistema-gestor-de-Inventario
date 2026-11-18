package com.sapam.inventario.config;

import com.sapam.inventario.entity.Herramienta;
import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.repository.HerramientaRepository;
import com.sapam.inventario.repository.ProductoRepository;
import com.sapam.inventario.service.BitacoraService;
import com.sapam.inventario.service.HerramientaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Pone al día los códigos que ya estaban en la base cuando cambió la forma de
 * imprimirlos. Dos trabajos, ambos de una sola vez y ambos con su asiento en
 * la bitácora:
 *
 * <ul>
 *   <li>Los productos de datos semilla sin {@code codigoInterno} lo reciben aquí,
 *       porque ese campo solo se genera en {@code @PrePersist}, que no se
 *       dispara al rellenar registros que ya existían.</li>
 *   <li>Las herramientas cuyo código es un UUID largo ({@code HER-} más 36
 *       caracteres) lo acortan al formato corto que asigna el sistema: en
 *       código de barras, 40 caracteres salen una barra de media etiqueta que
 *       el lector no agarra, así que ninguna herramienta se queda sin etiqueta
 *       legible.</li>
 * </ul>
 */
@Component
public class CodigosBackfill implements ApplicationRunner {

    /** Más allá de esto el código ya no cabe en una etiqueta chica. */
    private static final int LIMITE_CODIGO_LARGO = 16;

    private static final Logger log = LoggerFactory.getLogger(CodigosBackfill.class);

    private final ProductoRepository productoRepository;
    private final HerramientaRepository herramientaRepository;
    private final BitacoraService bitacoraService;

    public CodigosBackfill(ProductoRepository productoRepository,
                           HerramientaRepository herramientaRepository,
                           BitacoraService bitacoraService) {
        this.productoRepository = productoRepository;
        this.herramientaRepository = herramientaRepository;
        this.bitacoraService = bitacoraService;
    }

    @Override
    public void run(ApplicationArguments args) {
        rellenarProductosSinCodigo();
        acortarCodigosLargosDeHerramientas();
    }

    /** Completa el código interno de los productos que llegaron sin él. */
    private void rellenarProductosSinCodigo() {
        int corregidos = 0;

        for (Producto producto : productoRepository.findAll()) {
            if (producto.getCodigoInterno() != null && !producto.getCodigoInterno().isBlank()) {
                continue;
            }

            String codigoInterno = "INT-" + UUID.randomUUID();
            producto.setCodigoInterno(codigoInterno);

            try {
                productoRepository.save(producto);
                bitacoraService.registrar(
                        BitacoraService.MODIFICAR_PRODUCTO,
                        "INVENTARIO",
                        "Código: " + producto.getCodigoBarras()
                                + " | Nombre: " + producto.getNombre()
                                + " | Identificador interno: " + codigoInterno,
                        "Relleno automático del identificador interno en un producto de datos semilla",
                        null);
                corregidos++;
            } catch (Exception e) {
                log.warn("No se pudo asignar el identificador al producto {}: {}",
                        producto.getCodigoBarras(), e.getMessage());
            }
        }

        if (corregidos > 0) {
            log.info("Se asignó identificador interno a {} producto(s) que no tenían.", corregidos);
        }
    }

    /** Acorta los códigos de etiqueta largos al formato corto del sistema. */
    private void acortarCodigosLargosDeHerramientas() {
        int acortadas = 0;

        for (Herramienta herramienta : herramientaRepository.findAll()) {
            String anterior = herramienta.getCodigoBarras();
            if (anterior == null || anterior.length() <= LIMITE_CODIGO_LARGO) {
                continue;
            }

            String nuevo;
            do {
                nuevo = HerramientaService.candidatoCodigo();
            } while (herramientaRepository.findByCodigoBarras(nuevo).isPresent());

            herramienta.setCodigoBarras(nuevo);

            try {
                herramientaRepository.save(herramienta);
                bitacoraService.registrar(
                        BitacoraService.MODIFICAR_HERRAMIENTA,
                        "HERRAMIENTAS",
                        "Código: " + nuevo + " | Nombre: " + herramienta.getNombre()
                                + " | Anterior: " + anterior,
                        "Código acortado para que quepa en la barra de la etiqueta",
                        null);
                acortadas++;
            } catch (Exception e) {
                log.warn("No se pudo acortar el código de la herramienta {}: {}",
                        anterior, e.getMessage());
            }
        }

        if (acortadas > 0) {
            log.info("Se acortaron {} código(s) de herramienta que eran demasiado largos.",
                    acortadas);
        }
    }
}
