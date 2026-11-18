package com.sapam.inventario.controller;

import com.sapam.inventario.entity.Herramienta;
import com.sapam.inventario.entity.PrestamoHerramienta;
import com.sapam.inventario.service.HerramientaService;
import com.sapam.inventario.service.SesionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * Herramientas por HTTP.
 *
 * Dos cosas quedan fuera del cuerpo de la petición:
 *
 * <ul>
 *   <li>el <strong>código de la etiqueta</strong>: lo genera el sistema y no se teclea, para
 *       que nadie pueda dar de alta dos herramientas con la misma etiqueta;</li>
 *   <li><strong>quién entrega</strong>: en un préstamo es la cuenta con la que
 *       se firmó la petición, no un valor elegible. Quién recibe sí es texto
 *       libre, igual que en las salidas, porque el que toma la herramienta casi
 *       nunca tiene cuenta en el sistema.</li>
 * </ul>
 *
 * {@code POST /prestamo} y {@code POST /devolver} existen porque son las
 * operaciones del usuario operativo; el resto de la ventana es de
 * administrador (alta, edición y baja lógica).
 */
@RestController
@RequestMapping("/api/herramientas")
public class HerramientaController {

    @Autowired
    private HerramientaService herramientaService;

    @Autowired
    private SesionService sesionService;

    @GetMapping
    public List<Herramienta> listar() {
        return herramientaService.listarTodas();
    }

    @GetMapping("/{id}")
    public Herramienta porId(@PathVariable Long id) {
        return herramientaService.porId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Herramienta no encontrada: " + id));
    }

    @PostMapping
    public Herramienta alta(@RequestBody Herramienta herramienta) {
        // El código, la fecha de registro, el estado y la fecha de vuelta no son
        // campos que se puedan escribir: los pone el sistema. Si vinieran en la
        // petición se ignoran, igual que en la ventana de escritorio.
        herramienta.setId(null);
        herramienta.setCodigoBarras(null);
        herramienta.setFechaRegistro(null);
        herramienta.setEstado(null);
        herramienta.setFechaEstimadaDevolucion(null);

        try {
            return herramientaService.alta(herramienta, sesionService.actual());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Herramienta modificar(@PathVariable Long id, @RequestBody Herramienta herramienta) {
        herramienta.setId(id);
        try {
            return herramientaService.modificar(herramienta, sesionService.actual());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** Baja lógica: la fila no se borra, solo se marca en estado BAJAS. */
    @DeleteMapping("/{id}")
    public void baja(@PathVariable Long id) {
        try {
            herramientaService.baja(id, sesionService.actual());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/prestamo")
    public PrestamoHerramienta prestar(@RequestBody SolicitudPrestamoDTO solicitud) {
        try {
            return herramientaService.prestar(
                    solicitud.getHerramientaId(),
                    solicitud.getQuienRecibe(),
                    solicitud.getAreaResponsable(),
                    solicitud.getDestino(),
                    solicitud.getFechaEstimadaDevolucion(),
                    solicitud.getObservaciones(),
                    sesionService.actual());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/devolver")
    public PrestamoHerramienta devolver(@RequestBody SolicitudDevolucionDTO solicitud) {
        try {
            return herramientaService.devolver(
                    solicitud.getPrestamoId(),
                    solicitud.getFechaDevolucion(),
                    solicitud.getEstadoFisico(),
                    solicitud.getObservaciones(),
                    sesionService.actual());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Cuerpo de un préstamo.
     *
     * Sin campo de quien entrega: eso lo pone la sesión. Si fuera elegible,
     * quien presta podría firmar por otra cuenta y negarlo después.
     */
    public static class SolicitudPrestamoDTO {
        private Long herramientaId;
        private String quienRecibe;
        private String areaResponsable;
        private String destino;
        private LocalDate fechaEstimadaDevolucion;
        private String observaciones;

        public Long getHerramientaId() { return herramientaId; }
        public void setHerramientaId(Long herramientaId) { this.herramientaId = herramientaId; }
        public String getQuienRecibe() { return quienRecibe; }
        public void setQuienRecibe(String quienRecibe) { this.quienRecibe = quienRecibe; }
        public String getAreaResponsable() { return areaResponsable; }
        public void setAreaResponsable(String areaResponsable) { this.areaResponsable = areaResponsable; }
        public String getDestino() { return destino; }
        public void setDestino(String destino) { this.destino = destino; }
        public LocalDate getFechaEstimadaDevolucion() { return fechaEstimadaDevolucion; }
        public void setFechaEstimadaDevolucion(LocalDate fechaEstimadaDevolucion) { this.fechaEstimadaDevolucion = fechaEstimadaDevolucion; }
        public String getObservaciones() { return observaciones; }
        public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    }

    /** Cuerpo de una devolución. */
    public static class SolicitudDevolucionDTO {
        private Long prestamoId;
        private LocalDate fechaDevolucion;
        private String estadoFisico;
        private String observaciones;

        public Long getPrestamoId() { return prestamoId; }
        public void setPrestamoId(Long prestamoId) { this.prestamoId = prestamoId; }
        public LocalDate getFechaDevolucion() { return fechaDevolucion; }
        public void setFechaDevolucion(LocalDate fechaDevolucion) { this.fechaDevolucion = fechaDevolucion; }
        public String getEstadoFisico() { return estadoFisico; }
        public void setEstadoFisico(String estadoFisico) { this.estadoFisico = estadoFisico; }
        public String getObservaciones() { return observaciones; }
        public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    }
}
