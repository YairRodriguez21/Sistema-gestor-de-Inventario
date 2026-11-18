package com.sapam.inventario.config;

import com.sapam.inventario.entity.PrestamoHerramienta;
import com.sapam.inventario.repository.PrestamoHerramientaRepository;
import com.sapam.inventario.service.HerramientaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Completa los préstamos de herramienta que se hicieron antes de que el
 * préstamo tuviera folio y estado de entrega propios.
 *
 * La especificación pide registrar el folio del préstamo y el estado físico
 * al momento de la entrega. Las dos columnas se añaden sobre una tabla que ya
 * tiene filas —MySQL no deja añadir una columna NOT NULL sin valor por
 * defecto—, así que llegan vacías y este recorrido las rellena al arrancar:
 *
 * <ul>
 *   <li>al préstamo sin folio se le asigna uno con el mismo formato de las
 *       entradas ({@code ENT-}) y de las salidas ({@code SAL-});</li>
 *   <li>los préstamos todavía <b>abiertos</b> copian a {@code estadoEntrega}
 *       su {@code herramientaEstado}, que mientras estaban abiertos era
 *       exactamente el estado con el que salieron;</li>
 *   <li>los ya <b>devueltos</b> quedan sin estado de entrega, y así se
 *       muestran: ese dato se perdió cuando la devolución se guardó en el
 *       mismo campo, y rellenarlo con el estado de la devolución sería
 *       inventarse un dato de trazabilidad.</li>
 * </ul>
 *
 * Idempotente: solo toca filas cuyo folio esté vacío, así que arrancar dos
 * veces no asigna dos folios ni cambia nada de lo ya hecho. No deja evento en
 * la bitácora porque no es una acción del negocio, sino la reparación de un
 * campo que faltaba: la bitácora registra quién movió mercancía, no cómo se
 * montó la tabla.
 */
@Component
public class MigracionPrestamos implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(MigracionPrestamos.class);

    private final PrestamoHerramientaRepository prestamoRepository;

    public MigracionPrestamos(PrestamoHerramientaRepository prestamoRepository) {
        this.prestamoRepository = prestamoRepository;
    }

    @Override
    public void run(String... args) {
        int foliosAsignados = 0;
        int estadosRecuperados = 0;

        for (PrestamoHerramienta prestamo : prestamoRepository.findAll()) {
            boolean cambio = false;

            if (prestamo.getFolio() == null || prestamo.getFolio().isBlank()) {
                String folio = HerramientaService.candidatoFolio();
                while (prestamoRepository.findByFolio(folio).isPresent()) {
                    folio = HerramientaService.candidatoFolio();
                }
                prestamo.setFolio(folio);
                foliosAsignados++;
                cambio = true;
            }

            if (prestamo.getEstadoEntrega() == null && "PENDIENTE".equals(prestamo.getEstado())) {
                prestamo.setEstadoEntrega(prestamo.getHerramientaEstado());
                estadosRecuperados++;
                cambio = true;
            }

            if (cambio) {
                prestamoRepository.save(prestamo);
            }
        }

        if (foliosAsignados > 0 || estadosRecuperados > 0) {
            LOG.info("Préstamos de herramienta completados al arrancar: "
                    + "{} folio(s) asignado(s), {} estado(s) de entrega recuperado(s)",
                    foliosAsignados, estadosRecuperados);
        }
    }
}
