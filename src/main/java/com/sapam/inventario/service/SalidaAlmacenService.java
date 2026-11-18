package com.sapam.inventario.service;

import com.sapam.inventario.entity.Movimiento;
import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.entity.ProductoSalida;
import com.sapam.inventario.entity.SalidaAlmacen;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.MovimientoRepository;
import com.sapam.inventario.repository.ProductoRepository;
import com.sapam.inventario.repository.SalidaAlmacenRepository;
import com.sapam.inventario.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Salidas de almacén.
 *
 * Este es el único camino por el que sale material. Antes el botón "Salida" de
 * la tabla escribía el movimiento y el kardex pero no la fila de
 * {@code salidas_almacen}, y como el reporte "salidas por periodo" lee
 * precisamente esa tabla, una salida registrada no aparecía en el reporte.
 *
 * Ahora un mismo folio deja las cuatro trazas: existencia descontada,
 * {@code movimientos}, kardex y {@code salidas_almacen} con sus líneas de
 * productos, más la bitácora.
 *
 * Surtir una lista era una salida por producto: para entregar diez renglones
 * había que abrir y cerrar el formulario diez veces, con diez folios distintos
 * y ningún papel que los agrupara. Ahora la salida es en bloque, igual que la
 * entrada: un folio, varios productos, un solo ticket.
 *
 * Quien registra es quien responde: el reporte de salidas por periodo saca de
 * aquí su columna "Responsable", leída de {@code usuario_registro}. No es un
 * dato que se rellena, así que no hay manera de firmar por una cuenta ajena.
 */
@Service
public class SalidaAlmacenService {

    /** Motivos que admite la especificación para una salida. */
    public static final List<String> MOTIVOS = List.of(
            "REPARACION",
            "MANTENIMIENTO_PREVENTIVO",
            "MANTENIMIENTO_CORRECTIVO",
            "INSTALACION",
            "ADMINISTRATIVO",
            "OTRO");

    /**
     * El motivo que no basta por sí solo.
     *
     * Los otros cinco dicen de qué se trata; "OTRO" solo dice que no es
     * ninguno de ellos, así que quien elige este motivo tiene que escribir
     * además de qué se trata.
     */
    public static final String MOTIVO_OTRO = "OTRO";

    /** Una línea de la salida: qué producto sale y en qué cantidad. */
    public record Detalle(Integer productoId, Integer cantidad) {}

    @Autowired private SalidaAlmacenRepository salidaAlmacenRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private MovimientoRepository movimientoRepository;
    @Autowired private KardexService kardexService;
    @Autowired private BitacoraService bitacoraService;

    // ==================================================================
    // Camino nuevo: salida en bloque con orden de trabajo
    // ==================================================================

    /**
     * Registra una salida con todos los productos de la lista, una sola vez.
     *
     * @param usuarioRegistroId  cuenta que entrega el material y responde
     * @param ordenTrabajo       orden que autoriza la salida: obligatoria
     * @param personaSolicita    quién solicita
     * @param puestoSolicita     cargo de quien solicita, opcional
     * @param departamentoSolicita área: obligatorio, alimenta el reporte por
     *                           departamento
     * @param personaRecibe      quién se lleva el material
     * @param motivoSalida       uno de {@link #MOTIVOS}
     * @param motivoOtro         qué es, cuando el motivo es {@link #MOTIVO_OTRO}
     * @param observaciones      opcional
     * @param detalles           productos que salen; al menos uno
     * @return la salida guardada, con su folio
     */
    @Transactional
    public SalidaAlmacen registrar(Integer usuarioRegistroId,
                                   String ordenTrabajo,
                                   String personaSolicita,
                                   String puestoSolicita,
                                   String departamentoSolicita,
                                   String personaRecibe,
                                   String motivoSalida,
                                   String motivoOtro,
                                   String observaciones,
                                   List<Detalle> detalles) {

        if (ordenTrabajo == null || ordenTrabajo.isBlank()) {
            throw new IllegalArgumentException("Debe indicar el número de orden de trabajo");
        }
        if (MOTIVO_OTRO.equals(motivoSalida) && (motivoOtro == null || motivoOtro.isBlank())) {
            throw new IllegalArgumentException(
                    "El motivo es OTRO: escriba en qué consiste la salida");
        }

        return registrarInterno(usuarioRegistroId, ordenTrabajo.trim(),
                personaSolicita, puestoSolicita, departamentoSolicita,
                personaRecibe, null, null,
                motivoSalida, motivoOtro, observaciones, detalles);
    }

    // ==================================================================
    // Compatibilidad: salida de un solo producto
    // ==================================================================
    //
    // Son las firmas que ya usaban la API REST y las pruebas. Siguen
    // funcionando igual y no piden orden de trabajo: esa la exige el
    // formulario, que es donde de verdad se controla. El puesto y el área de
    // quien recibe solo llegan por aquí; el formulario ya no los pregunta.

    /**
     * Registra una salida sin anotar el puesto ni el área de quien recibe.
     *
     * Son opcionales, así que esta firma es la que ya usaban los llamadores
     * antiguos y los clientes de la API: solo cambia para quien quiera dejarlos.
     */
    @Transactional
    public SalidaAlmacen registrar(Integer productoId,
                                   Integer cantidad,
                                   Integer usuarioRegistroId,
                                   String personaRecibe,
                                   String personaSolicita,
                                   String puestoSolicita,
                                   String departamentoSolicita,
                                   String motivoSalida,
                                   String observaciones) {
        return registrar(productoId, cantidad, usuarioRegistroId, personaRecibe,
                null, null, personaSolicita, puestoSolicita, departamentoSolicita,
                motivoSalida, observaciones);
    }

    /**
     * Registra una salida formal de un producto.
     *
     * @param productoId         producto que sale
     * @param cantidad           unidades (> 0 y menor o igual al stock)
     * @param usuarioRegistroId  usuario que hace el registro: entrega el material
     *                           y responde de la salida
     * @param personaRecibe      quién recibe el material, en texto libre
     * @param puestoRecibe       cargo o puesto de quien recibe, opcional
     * @param departamentoRecibe área de adscripción de quien recibe, opcional
     * @param personaSolicita    quién solicita la salida
     * @param motivoSalida       uno de {@link #MOTIVOS}
     * @param observaciones      opcional
     * @return la salida guardada, con su folio
     */
    @Transactional
    public SalidaAlmacen registrar(Integer productoId,
                                   Integer cantidad,
                                   Integer usuarioRegistroId,
                                   String personaRecibe,
                                   String puestoRecibe,
                                   String departamentoRecibe,
                                   String personaSolicita,
                                   String puestoSolicita,
                                   String departamentoSolicita,
                                   String motivoSalida,
                                   String observaciones) {
        return registrarInterno(usuarioRegistroId, null,
                personaSolicita, puestoSolicita, departamentoSolicita,
                personaRecibe, puestoRecibe, departamentoRecibe,
                motivoSalida, null, observaciones,
                List.of(new Detalle(productoId, cantidad)));
    }

    // ==================================================================
    // El camino por el que pasa todo
    // ==================================================================

    /**
     * Valida y escribe la salida con todas sus líneas.
     *
     * Corre dentro de la transacción que abre el método público que la llama,
     * así que o entra entera o no entra.
     *
     * El orden de las validaciones importa: el motivo inválido se detecta
     * antes de mirar el stock, y ninguna de ellas toca una existencia. Si algo
     * falla a mitad de camino no queda ni un descuento suelto, porque
     * KardexService guarda en su propia transacción (REQUIRES_NEW) y esas
     * líneas no se podrían deshacer después.
     */
    private SalidaAlmacen registrarInterno(Integer usuarioRegistroId,
                                           String ordenTrabajo,
                                           String personaSolicita,
                                           String puestoSolicita,
                                           String departamentoSolicita,
                                           String personaRecibe,
                                           String puestoRecibe,
                                           String departamentoRecibe,
                                           String motivoSalida,
                                           String motivoOtro,
                                           String observaciones,
                                           List<Detalle> detalles) {

        // ---- 1) Validar todo ANTES de tocar existencias ---------------------

        if (detalles == null || detalles.isEmpty()) {
            throw new IllegalArgumentException("La salida debe incluir al menos un producto");
        }
        for (Detalle detalle : detalles) {
            if (detalle.cantidad() == null || detalle.cantidad() <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
            }
        }
        if (personaSolicita == null || personaSolicita.isBlank()) {
            throw new IllegalArgumentException("Debe indicar quién solicita la salida");
        }
        // Quién recibe es el "quién" de la especificación: texto libre, porque
        // quien se lleva el material no tiene por qué tener usuario en el sistema.
        if (personaRecibe == null || personaRecibe.isBlank()) {
            throw new IllegalArgumentException("Debe indicar quién recibe el material");
        }
        // El departamento es obligatorio porque sobre él se calcula el reporte
        // "materiales entregados a un departamento".
        if (departamentoSolicita == null || departamentoSolicita.isBlank()) {
            throw new IllegalArgumentException("Debe indicar el área o departamento que solicita");
        }
        if (motivoSalida == null || !MOTIVOS.contains(motivoSalida)) {
            throw new IllegalArgumentException(
                    "Motivo no válido: " + motivoSalida + ". Use uno de: " + String.join(", ", MOTIVOS));
        }

        List<Producto> productos = new ArrayList<>();
        for (Detalle detalle : detalles) {
            productos.add(productoRepository.findById(detalle.productoId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado")));
        }

        // Todo el stock revisado de corrido: si el octavo producto no alcanza,
        // los siete anteriores no deben haberse descontado.
        for (int i = 0; i < detalles.size(); i++) {
            Producto producto = productos.get(i);
            int cantidad = detalles.get(i).cantidad();
            if (producto.getExistenciaActual() < cantidad) {
                throw new IllegalArgumentException(
                        "Stock insuficiente. Stock actual: " + producto.getExistenciaActual()
                                + (productos.size() > 1 ? " — " + producto.getNombre() : ""));
            }
        }

        Usuario usuarioRegistro = usuarioRepository.findById(usuarioRegistroId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // ---- 2) Registrar ---------------------------------------------------
        String folio = "SAL-" + System.currentTimeMillis();

        SalidaAlmacen salida = new SalidaAlmacen();
        salida.setFolio(folio);
        salida.setFecha(LocalDate.now());
        salida.setHora(LocalTime.now());
        salida.setUsuarioRegistro(usuarioRegistro);
        salida.setUsuarioEntrega(usuarioRegistro); // el que registra es el que entrega
        salida.setPersonaRecibe(personaRecibe.trim());
        salida.setPuestoRecibe(vacioANull(puestoRecibe));
        salida.setDepartamentoRecibe(vacioANull(departamentoRecibe));
        salida.setPersonaSolicita(personaSolicita.trim());
        salida.setPuestoSolicita(vacioANull(puestoSolicita));
        salida.setDepartamentoSolicita(vacioANull(departamentoSolicita));
        salida.setMotivoSalida(motivoSalida);
        salida.setMotivoOtro(vacioANull(motivoOtro));
        salida.setOrdenTrabajo(vacioANull(ordenTrabajo));
        salida.setObservaciones(vacioANull(observaciones));

        Set<ProductoSalida> lineas = new HashSet<>();
        int totalUnidades = 0;

        for (int i = 0; i < detalles.size(); i++) {
            Detalle detalle = detalles.get(i);
            Producto producto = productos.get(i);

            int existenciaAnterior = producto.getExistenciaActual();
            int existenciaNueva = existenciaAnterior - detalle.cantidad();
            producto.setExistenciaActual(existenciaNueva);
            productoRepository.save(producto);

            ProductoSalida linea = new ProductoSalida();
            linea.setSalidaAlmacen(salida);
            linea.setProducto(producto);
            linea.setCantidad(detalle.cantidad());
            linea.setObservaciones(vacioANull(observaciones));
            lineas.add(linea);

            // Movimiento: alimenta el reporte de movimientos por usuario. Los de
            // materiales por trabajador y por departamento salen de la salida misma.
            Movimiento movimiento = new Movimiento();
            movimiento.setProducto(producto);
            movimiento.setTipo(Movimiento.TipoMovimiento.salida);
            movimiento.setCantidad(detalle.cantidad());
            movimiento.setStockResultante(existenciaNueva);
            movimiento.setUsuario(usuarioRegistro);
            movimiento.setObservaciones("Folio " + folio
                    + " | Solicita: " + salida.getPersonaSolicita()
                    + " | Recibe: " + salida.getPersonaRecibe()
                    + (ordenTrabajo == null ? "" : " | OT: " + ordenTrabajo)
                    + " | Motivo: " + motivoSalida
                    + (observaciones == null || observaciones.isBlank() ? "" : " | " + observaciones.trim()));
            movimiento.setFecha(LocalDateTime.now());
            movimientoRepository.save(movimiento);

            kardexService.registrar(producto, "SALIDA", folio, -detalle.cantidad(),
                    existenciaAnterior, existenciaNueva, usuarioRegistro,
                    "Solicita: " + salida.getPersonaSolicita()
                            + " | Recibe: " + salida.getPersonaRecibe()
                            + (ordenTrabajo == null ? "" : " | OT: " + ordenTrabajo)
                            + " | Motivo: " + motivoSalida
                            + (observaciones == null || observaciones.isBlank() ? "" : ". " + observaciones.trim()));

            totalUnidades += detalle.cantidad();
        }

        salida.setProductosEntregados(lineas);
        SalidaAlmacen guardada = salidaAlmacenRepository.save(salida);

        StringBuilder resumen = new StringBuilder();
        for (int i = 0; i < detalles.size(); i++) {
            if (i > 0) {
                resumen.append(" | ");
            }
            resumen.append(productos.get(i).getNombre())
                    .append(": -").append(detalles.get(i).cantidad());
        }

        bitacoraService.registrar(BitacoraService.SALIDA_ALMACEN, "SALIDAS",
                "Folio: " + folio
                        + (ordenTrabajo == null ? "" : " | OT: " + ordenTrabajo)
                        + " | Productos: " + detalles.size()
                        + " | Unidades: " + totalUnidades
                        + " | Motivo: " + motivoSalida
                        + " | Detalle: " + resumen,
                "Solicita: " + salida.getPersonaSolicita()
                        + " | Recibe: " + salida.getPersonaRecibe()
                        + (salida.getDepartamentoSolicita() == null
                            ? "" : " | Departamento: " + salida.getDepartamentoSolicita())
                        + (salida.getMotivoOtro() == null ? "" : " | Motivo otro: " + salida.getMotivoOtro()),
                usuarioRegistro);

        return guardada;
    }

    @Transactional(readOnly = true)
    public List<SalidaAlmacen> listarTodas() {
        return salidaAlmacenRepository.findAll();
    }

    @Transactional(readOnly = true)
    public SalidaAlmacen buscarPorFolio(String folio) {
        return salidaAlmacenRepository.findByFolio(folio).orElse(null);
    }

    private static String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
