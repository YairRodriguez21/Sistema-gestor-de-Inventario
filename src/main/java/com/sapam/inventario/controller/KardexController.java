package com.sapam.inventario.controller;

import com.sapam.inventario.service.KardexService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Lectura del kardex automático por HTTP.
 *
 * Es de solo lectura y a propósito: el kardex lo escriben solo los servicios de
 * entrada y de salida cuando mueven existencias. Que exista un POST de kardex
 * significaría poder rellenarlo a mano y romper la trazabilidad, así que
 * tampoco se propone en la configuración de seguridad.
 */
@RestController
@RequestMapping("/api/kardex")
public class KardexController {

    private final KardexService kardexService;

    public KardexController(KardexService kardexService) {
        this.kardexService = kardexService;
    }

    /** Historial completo de un producto, del movimiento más reciente al primero. */
    @GetMapping("/producto/{productoId}")
    public List<KardexLineaDTO> porProducto(@PathVariable Integer productoId) {
        return kardexService.historialPorProducto(productoId).stream()
                .map(KardexLineaDTO::new)
                .collect(Collectors.toList());
    }
}
