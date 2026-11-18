package com.sapam.inventario.controller;

import com.sapam.inventario.entity.SalidaAlmacen;
import com.sapam.inventario.service.SalidaAlmacenService;
import com.sapam.inventario.service.SesionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Salidas de almacén por HTTP.
 *
 * {@code SalidaAlmacenService} es el único camino de salida: este controlador
 * no toca existencias ni kardex por su cuenta, solo le pasa lo que viene en la
 * petición y el responsable de la sesión. Al igual que la entrada, no admite
 * PUT ni DELETE: una salida registrada no se edita ni se borra.
 */
@RestController
@RequestMapping("/api/salidas")
public class SalidaController {

    @Autowired
    private SalidaAlmacenService salidaAlmacenService;

    @Autowired
    private SesionService sesionService;

    @GetMapping
    public List<SalidaAlmacenDTO> listar() {
        return salidaAlmacenService.listarTodas().stream()
                .map(SalidaAlmacenDTO::new)
                .toList();
    }

    /** El folio es el texto que codifica el código de barras de la salida impresa. */
    @GetMapping("/folio/{folio}")
    public SalidaAlmacenDTO porFolio(@PathVariable String folio) {
        SalidaAlmacen salida = salidaAlmacenService.buscarPorFolio(folio);
        if (salida == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Salida no encontrada: " + folio);
        }
        return new SalidaAlmacenDTO(salida);
    }

    @PostMapping
    public SalidaAlmacenDTO registrar(@RequestBody SolicitudSalidaDTO solicitud) {
        SalidaAlmacen salida = salidaAlmacenService.registrar(
                solicitud.getProductoId(),
                solicitud.getCantidad(),
                sesionService.actualId(),
                solicitud.getPersonaRecibe(),
                solicitud.getPuestoRecibe(),
                solicitud.getDepartamentoRecibe(),
                solicitud.getPersonaSolicita(),
                solicitud.getPuestoSolicita(),
                solicitud.getDepartamentoSolicita(),
                solicitud.getMotivoSalida(),
                solicitud.getObservaciones());

        return new SalidaAlmacenDTO(salida);
    }

    /**
     * Cuerpo de una salida.
     *
     * Sin {@code usuarioRegistroId}: quién entrega y responde del material es
     * la cuenta con la que se firmó la petición, no un valor elegible. Si lo
     * fuera, quien registra podría firmar por otra cuenta y negarlo después.
     */
    public static class SolicitudSalidaDTO {
        private Integer productoId;
        private Integer cantidad;
        private String personaRecibe;
        private String puestoRecibe;
        private String departamentoRecibe;
        private String personaSolicita;
        private String puestoSolicita;
        private String departamentoSolicita;
        private String motivoSalida;
        private String observaciones;

        public Integer getProductoId() { return productoId; }
        public void setProductoId(Integer productoId) { this.productoId = productoId; }
        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
        public String getPersonaRecibe() { return personaRecibe; }
        public void setPersonaRecibe(String personaRecibe) { this.personaRecibe = personaRecibe; }
        public String getPuestoRecibe() { return puestoRecibe; }
        public void setPuestoRecibe(String puestoRecibe) { this.puestoRecibe = puestoRecibe; }
        public String getDepartamentoRecibe() { return departamentoRecibe; }
        public void setDepartamentoRecibe(String departamentoRecibe) { this.departamentoRecibe = departamentoRecibe; }
        public String getPersonaSolicita() { return personaSolicita; }
        public void setPersonaSolicita(String personaSolicita) { this.personaSolicita = personaSolicita; }
        public String getPuestoSolicita() { return puestoSolicita; }
        public void setPuestoSolicita(String puestoSolicita) { this.puestoSolicita = puestoSolicita; }
        public String getDepartamentoSolicita() { return departamentoSolicita; }
        public void setDepartamentoSolicita(String departamentoSolicita) { this.departamentoSolicita = departamentoSolicita; }
        public String getMotivoSalida() { return motivoSalida; }
        public void setMotivoSalida(String motivoSalida) { this.motivoSalida = motivoSalida; }
        public String getObservaciones() { return observaciones; }
        public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    }
}
