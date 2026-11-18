package com.sapam.inventario.controller;

import com.sapam.inventario.entity.AuditoriaSistema;
import com.sapam.inventario.service.BitacoraService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Lectura de la bitácora del sistema por HTTP.
 *
 * Solo tiene {@code GET}: ni POST, ni PUT, ni DELETE. La bitácora se escribe
 * desde la aplicación de escritorio (y desde los servicios de entrada y salida),
 * y aquí únicamente se consulta, que es lo que pide el requisito de bitácora
 * ineliminable.
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final BitacoraService bitacoraService;

    public AuditoriaController(BitacoraService bitacoraService) {
        this.bitacoraService = bitacoraService;
    }

    /** Últimos 200 eventos, del más reciente al más antiguo. */
    @GetMapping("/ultimos")
    public List<BitacoraEventoDTO> ultimos() {
        return aDto(bitacoraService.ultimosEventos());
    }

    /**
     * Los 200 eventos más recientes de una acción concreta
     * (LOGIN, SALIDA_ALMACEN, ...).
     *
     * El tope vive en la consulta y no aquí: el filtro crece con la bitácora
     * y un llamador sin cuidado pediría cientos de miles de filas de una vez.
     */
    @GetMapping("/accion/{accion}")
    public List<BitacoraEventoDTO> porAccion(@PathVariable String accion) {
        return aDto(bitacoraService.porAccion(accion));
    }

    /** Los 200 eventos más recientes registrados por un usuario. */
    @GetMapping("/usuario/{usuarioId}")
    public List<BitacoraEventoDTO> porUsuario(@PathVariable Integer usuarioId) {
        return aDto(bitacoraService.porUsuario(usuarioId));
    }

    private static List<BitacoraEventoDTO> aDto(List<AuditoriaSistema> eventos) {
        return eventos.stream().map(BitacoraEventoDTO::new).collect(Collectors.toList());
    }
}
