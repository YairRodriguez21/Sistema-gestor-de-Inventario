package com.sapam.inventario.controller;

import com.sapam.inventario.entity.EntradaAlmacen;
import com.sapam.inventario.service.EntradaAlmacenService;
import com.sapam.inventario.service.SesionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Entradas de almacén por HTTP.
 *
 * Lo importante de este controlador es lo que <em>no</em> tiene: el cuerpo de
 * la petición no trae ningún campo de usuario. Quien queda como responsable de
 * la entrada sale de la sesión que firma el token, igual que en la ventana de
 * escritorio. Además no hay PUT ni DELETE: una entrada registrada no se edita
 * ni se borra, solo se anula con otra que refleje lo que pasó de verdad.
 */
@RestController
@RequestMapping("/api/entradas")
public class EntradaController {

    @Autowired
    private EntradaAlmacenService entradaAlmacenService;

    @Autowired
    private SesionService sesionService;

    @GetMapping
    public List<EntradaAlmacenDTO> listar() {
        return entradaAlmacenService.listarTodas().stream()
                .map(EntradaAlmacenDTO::new)
                .toList();
    }

    /** El folio es el código de barras del papel impreso de la entrada. */
    @GetMapping("/folio/{folio}")
    public EntradaAlmacenDTO porFolio(@PathVariable String folio) {
        EntradaAlmacen entrada = entradaAlmacenService.buscarPorFolio(folio);
        if (entrada == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Entrada no encontrada: " + folio);
        }
        return new EntradaAlmacenDTO(entrada);
    }

    @PostMapping
    public EntradaAlmacenDTO registrar(@RequestBody SolicitudEntradaDTO solicitud) {
        if (solicitud.getProductos() == null || solicitud.getProductos().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La entrada debe incluir al menos un producto");
        }

        EntradaAlmacen entrada = entradaAlmacenService.registrar(
                sesionService.actualId(),
                solicitud.getObservaciones(),
                solicitud.getProductos());

        return new EntradaAlmacenDTO(entrada);
    }

    /**
     * Cuerpo de una entrada.
     *
     * Sin {@code usuarioId}: la cuenta responsable la pone el token. Las líneas
     * se reciben como {@link EntradaAlmacenService.Detalle}, el mismo registro
     * que usa el servicio, para que no haya dos formas de nombrar lo mismo.
     */
    public static class SolicitudEntradaDTO {
        private String observaciones;
        private List<EntradaAlmacenService.Detalle> productos;

        public String getObservaciones() { return observaciones; }
        public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
        public List<EntradaAlmacenService.Detalle> getProductos() { return productos; }
        public void setProductos(List<EntradaAlmacenService.Detalle> productos) { this.productos = productos; }
    }
}
