package com.sapam.inventario.controller;

import com.sapam.inventario.entity.PrestamoHerramienta;
import com.sapam.inventario.service.HerramientaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Préstamos de herramientas por HTTP.
 *
 * Solo lectura: el préstamo y su devolución viven en
 * {@code /api/herramientas/prestamo} y {@code /api/herramientas/devolver},
 * que es donde SecurityConfig les dio acceso al usuario operativo. Aquí se
 * consulta quién tiene qué y desde cuándo, que es lo que hace falta para
 * localizar el bien.
 */
@RestController
@RequestMapping("/api/prestamos")
public class PrestamoController {

    @Autowired
    private HerramientaService herramientaService;

    /** Todo lo que sigue fuera: la ventana del operativo solo mira estos. */
    @GetMapping("/activos")
    public List<PrestamoHerramienta> activos() {
        return herramientaService.prestamosActivos();
    }

    @GetMapping
    public List<PrestamoHerramienta> todos() {
        return herramientaService.prestamos();
    }

    @GetMapping("/{id}")
    public PrestamoHerramienta porId(@PathVariable Long id) {
        return herramientaService.prestamoPorId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Préstamo no encontrado: " + id));
    }
}
