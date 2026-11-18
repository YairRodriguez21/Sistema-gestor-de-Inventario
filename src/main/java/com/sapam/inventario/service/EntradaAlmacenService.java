package com.sapam.inventario.service;

import com.sapam.inventario.entity.EntradaAlmacen;
import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.entity.ProductoEntrada;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.EntradaAlmacenRepository;
import com.sapam.inventario.repository.ProductoRepository;
import com.sapam.inventario.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Entradas de almacén.
 *
 * Una entrada puede llevar varios productos; por cada uno se incrementa su
 * existencia y se escribe una línea en el kardex con la existencia anterior y
 * la posterior. Al final queda un evento en la bitácora con el folio completo.
 *
 * El folio es el identificador que codifica su código de barras (ver {@code CodigoBarrasController}),
 * así que un papel impreso con el folio se puede escanear para volver a la entrada.
 */
@Service
public class EntradaAlmacenService {

    /** Una línea de la entrada: qué producto entra y en qué cantidad. */
    public record Detalle(Integer productoId, Integer cantidad, String observaciones) {}

    @Autowired
    private EntradaAlmacenRepository entradaAlmacenRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private KardexService kardexService;

    @Autowired
    private BitacoraService bitacoraService;

    @Transactional
    public EntradaAlmacen registrar(Integer usuarioId,
                                    String observaciones,
                                    List<Detalle> detalles) {

        if (detalles == null || detalles.isEmpty()) {
            throw new IllegalArgumentException("La entrada debe incluir al menos un producto");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // ---- 1) Validar todo ANTES de tocar existencias --------------------
        // KardexService guarda en una transacción propia (REQUIRES_NEW): si algo
        // fallara a mitad ya no se podrían deshacer las líneas ya escritas.
        List<Producto> productos = new ArrayList<>();
        for (Detalle detalle : detalles) {
            if (detalle.cantidad() == null || detalle.cantidad() <= 0) {
                throw new IllegalArgumentException(
                        "La cantidad debe ser mayor a cero (producto " + detalle.productoId() + ")");
            }
            productos.add(productoRepository.findById(detalle.productoId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado: " + detalle.productoId())));
        }

        // ---- 2) Registrar -------------------------------------------------
        EntradaAlmacen entrada = new EntradaAlmacen();
        entrada.setFolio("ENT-" + System.currentTimeMillis());
        entrada.setFecha(LocalDate.now());
        entrada.setHora(LocalTime.now());
        entrada.setUsuarioRegistro(usuario);
        entrada.setObservaciones(observaciones);

        Set<ProductoEntrada> lineas = new HashSet<>();
        int totalUnidades = 0;

        for (int i = 0; i < detalles.size(); i++) {
            Detalle detalle = detalles.get(i);
            Producto producto = productos.get(i);

            int existenciaAnterior = producto.getExistenciaActual();
            int existenciaNueva = existenciaAnterior + detalle.cantidad();
            producto.setExistenciaActual(existenciaNueva);
            productoRepository.save(producto);

            ProductoEntrada linea = new ProductoEntrada();
            linea.setEntradaAlmacen(entrada);
            linea.setProducto(producto);
            linea.setCantidad(detalle.cantidad());
            linea.setObservaciones(detalle.observaciones());
            lineas.add(linea);

            kardexService.registrar(producto, "ENTRADA", entrada.getFolio(),
                    detalle.cantidad(), existenciaAnterior, existenciaNueva, usuario,
                    "Entrada de almacén" + (observaciones == null ? "" : ". " + observaciones));

            totalUnidades += detalle.cantidad();
        }

        entrada.setProductosIngresados(lineas);
        EntradaAlmacen guardada = entradaAlmacenRepository.save(entrada);

        StringBuilder resumen = new StringBuilder();
        for (int i = 0; i < detalles.size(); i++) {
            if (i > 0) {
                resumen.append(" | ");
            }
            resumen.append(productos.get(i).getNombre())
                    .append(": +").append(detalles.get(i).cantidad());
        }

        bitacoraService.registrar(
                BitacoraService.ENTRADA_ALMACEN,
                "ENTRADAS",
                "Folio: " + guardada.getFolio()
                        + " | Productos: " + detalles.size()
                        + " | Unidades: " + totalUnidades
                        + " | Detalle: " + resumen,
                observaciones == null ? "" : observaciones,
                usuario);

        return guardada;
    }

    @Transactional(readOnly = true)
    public List<EntradaAlmacen> listarTodas() {
        return entradaAlmacenRepository.findAll();
    }

    @Transactional(readOnly = true)
    public EntradaAlmacen buscarPorFolio(String folio) {
        return entradaAlmacenRepository.findByFolio(folio).orElse(null);
    }
}
