package com.sapam.inventario.reportes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pasa los reportes a encabezados y filas para la tabla de la aplicación de
 * escritorio y para su exportación a CSV.
 *
 * Vive en este paquete porque varios DTO de detalle —las líneas de una entrada,
 * de una salida, del kardex, el resumen de movimientos de un usuario— son
 * internos al módulo de reportes y solo se pueden leer desde aquí. Desde el
 * paquete de controladores JavaFX no compilan.
 *
 * Los reportes con detalle anidado se aplanan a una fila por línea repitiendo
 * las columnas de cabecera, que es como se espera en un CSV.
 *
 * Ningún reporte repite el código que lleva impresa la etiqueta: la columna
 * "Código" ya es el código de barras que escribe el lector, así que ahí no
 * sobra una segunda columna con el identificador interno.
 */
public final class ReportesTabla {

    /** Encabezados y filas listas para dibujar en pantalla y exportar. */
    public record Tabla(List<String> encabezados, List<List<String>> filas) {}

    private ReportesTabla() {
    }

    // ============================================================
    // Inventario
    // ============================================================

    public static Tabla existenciasActuales(List<ReporteExistenciasActualesDTO> datos) {
        List<List<String>> filas = new ArrayList<>();
        for (ReporteExistenciasActualesDTO d : datos) {
            filas.add(List.of(
                    s(d.getCodigoBarras()), s(d.getNombre()), s(d.getSeccion()), s(d.getMarca()),
                    s(d.getModelo()), s(d.getUnidadMedida()), s(d.getExistenciaActual()),
                    s(d.getExistenciaMinima()), s(d.getUbicacionFisica()), s(d.getEstado())));
        }
        return new Tabla(List.of("Código", "Nombre", "Sección", "Marca", "Modelo", "Unidad",
                "Existencia", "Mínimo", "Ubicación", "Estado"), filas);
    }

    public static Tabla productosAgotados(List<ReporteProductosAgotadosDTO> datos) {
        List<List<String>> filas = new ArrayList<>();
        for (ReporteProductosAgotadosDTO d : datos) {
            filas.add(List.of(
                    s(d.getCodigoBarras()), s(d.getNombre()), s(d.getSeccion()), s(d.getMarca()),
                    s(d.getExistenciaActual())));
        }
        return new Tabla(List.of("Código", "Nombre", "Sección", "Marca", "Existencia"),
                filas);
    }

    public static Tabla productosStockMinimo(List<ReporteProductosStockMinimoDTO> datos) {
        List<List<String>> filas = new ArrayList<>();
        for (ReporteProductosStockMinimoDTO d : datos) {
            filas.add(List.of(
                    s(d.getCodigoBarras()), s(d.getNombre()), s(d.getSeccion()),
                    s(d.getExistenciaActual()), s(d.getExistenciaMinima()), s(d.getPorcentajeStock())));
        }
        return new Tabla(List.of("Código", "Nombre", "Sección", "Existencia", "Mínimo",
                "% stock"), filas);
    }

    // ============================================================
    // Entradas y salidas de almacén
    // ============================================================

    public static Tabla entradasPeriodo(List<ReporteEntradasPeriodoDTO> datos) {
        List<List<String>> filas = new ArrayList<>();
        for (ReporteEntradasPeriodoDTO e : datos) {
            if (e.getProductos() == null || e.getProductos().isEmpty()) {
                filas.add(List.of(s(e.getFolio()), s(e.getFecha()), s(e.getHora()),
                        s(e.getUsuarioRegistro()), "", "", "", "", s(e.getObservaciones())));
                continue;
            }
            for (ReporteEntradaProductoDTO p : e.getProductos()) {
                filas.add(List.of(
                        s(e.getFolio()), s(e.getFecha()), s(e.getHora()), s(e.getUsuarioRegistro()),
                        s(p.getNombreProducto()), s(p.getCodigoBarras()),
                        s(p.getCantidadIngresada()), s(p.getUnidadMedida()),
                        s(e.getObservaciones())));
            }
        }
        return new Tabla(List.of("Folio", "Fecha", "Hora", "Registró",
                "Producto", "Código", "Cantidad", "Unidad", "Observaciones"), filas);
    }

    public static Tabla salidasPeriodo(List<ReporteSalidasPeriodoDTO> datos) {
        List<List<String>> filas = new ArrayList<>();
        for (ReporteSalidasPeriodoDTO salida : datos) {
            if (salida.getProductos() == null || salida.getProductos().isEmpty()) {
                filas.add(List.of(s(salida.getFolio()), s(salida.getFecha()), s(salida.getHora()),
                        s(salida.getPersonaSolicita()), s(salida.getPuestoSolicita()),
                        s(salida.getDepartamentoSolicita()), s(salida.getMotivoSalida()),
                        s(salida.getUsuarioResponsable()), "", "", "", ""));
                continue;
            }
            for (ReporteSalidaProductoDTO p : salida.getProductos()) {
                filas.add(List.of(
                        s(salida.getFolio()), s(salida.getFecha()), s(salida.getHora()),
                        s(salida.getPersonaSolicita()), s(salida.getPuestoSolicita()),
                        s(salida.getDepartamentoSolicita()), s(salida.getMotivoSalida()),
                        s(salida.getUsuarioResponsable()),
                        s(p.getNombreProducto()), s(p.getCodigoBarras()), s(p.getCantidadEntregada()),
                        s(p.getNombreReceptor())));
            }
        }
        return new Tabla(List.of("Folio", "Fecha", "Hora", "Solicita", "Puesto", "Departamento",
                "Motivo", "Responsable", "Producto", "Código", "Cantidad", "Recibe"), filas);
    }

    // ============================================================
    // Materiales entregados
    // ============================================================

    public static Tabla materialesTrabajador(List<ReporteMaterialesTrabajadorDTO> datos) {
        List<List<String>> filas = new ArrayList<>();
        for (ReporteMaterialesTrabajadorDTO t : datos) {
            filas.addAll(filasDeMateriales(t.getMaterialesEntregados(),
                    t.getNombreTrabajador(), t.getCargoTrabajador(),
                    t.getDepartamentoTrabajador(), t.getTotalMovimientos(),
                    t.getFechaUltimoMovimiento()));
        }
        return new Tabla(List.of("Trabajador", "Cargo", "Departamento", "Total movimientos",
                "Último movimiento", "Folio", "Fecha", "Producto", "Cantidad", "Motivo", "Código"),
                filas);
    }

    public static Tabla materialesDepartamento(List<ReporteMaterialesDepartamentoDTO> datos) {
        List<List<String>> filas = new ArrayList<>();
        for (ReporteMaterialesDepartamentoDTO d : datos) {
            filas.addAll(filasDeMateriales(d.getMaterialesEntregados(),
                    d.getNombreDepartamento(), d.getAreaResponsable(),
                    d.getTotalMovimientos(), d.getFechaUltimoMovimiento()));
        }
        return new Tabla(List.of("Departamento", "Área responsable", "Total movimientos",
                "Último movimiento", "Folio", "Fecha", "Producto", "Cantidad", "Motivo", "Código"),
                filas);
    }

    /**
     * Repite las columnas de cabecera por cada material entregado. Se reciben en
     * forma de varargs porque el orden de las columnas no puede separarse de su
     * documentación en los llamadores.
     */
    private static List<List<String>> filasDeMateriales(List<ReporteMaterialEntregadoDTO> materiales,
                                                        Object... cabecera) {
        List<List<String>> filas = new ArrayList<>();
        List<String> prefijo = Arrays.stream(cabecera).map(ReportesTabla::s).toList();

        // Sin materiales se emite una única fila con las columnas de detalle en
        // blanco, para que el reporte no se quede sin filas.
        List<ReporteMaterialEntregadoDTO> detalle =
                (materiales == null || materiales.isEmpty())
                        ? List.of(new ReporteMaterialEntregadoDTO())
                        : materiales;

        for (ReporteMaterialEntregadoDTO m : detalle) {
            List<String> fila = new ArrayList<>(prefijo);
            fila.add(s(m.getFolioSalida()));
            fila.add(s(m.getFechaSalida()));
            fila.add(s(m.getNombreProducto()));
            fila.add(s(m.getCantidadEntregada()));
            fila.add(s(m.getMotivoSalida()));
            fila.add(s(m.getCodigoBarras()));
            filas.add(fila);
        }
        return filas;
    }

    // ============================================================
    // Herramientas
    // ============================================================

    /**
     * Préstamos de herramientas abiertos hoy. Con cero préstamos no se emite
     * ninguna fila: el reporte de "herramientas prestadas" no tiene nada que
     * contar y el encabezado basta para que se vea que se ejecutó.
     */
    public static Tabla herramientasPrestadas(List<ReporteHerramientasPrestadasDTO> datos) {
        List<List<String>> filas = new ArrayList<>();
        for (ReporteHerramientasPrestadasDTO d : datos) {
            if (d.getPrestamosActivos() == null) {
                continue;
            }
            for (ReportePrestamoHerramientaDTO p : d.getPrestamosActivos()) {
                filas.add(List.of(
                        s(p.getFolio()),
                        s(p.getNombreHerramienta()), s(p.getNumeroSerie()),
                        s(p.getPersonaRecibe()), s(p.getFechaEntrega()),
                        s(p.getHoraEntrega()), s(p.getFechaEstimadaDevolucion()),
                        s(p.getFechaDevolucion()), s(p.getEstado()), s(p.getCodigoBarras())));
            }
        }
        return new Tabla(List.of("Folio", "Herramienta", "No. serie", "Recibe", "Entrega", "Hora",
                "Devolución estimada", "Devolución", "Estado", "Código"), filas);
    }

    /**
     * Préstamos que ya vencieron. Aquí sí interesa saber cuántos días lleva
     * retrasada la herramienta, así que la columna va calculada en el servicio.
     */
    public static Tabla herramientasPendientesDevolucion(
            List<ReporteHerramientasPendientesDevolucionDTO> datos) {
        List<List<String>> filas = new ArrayList<>();
        for (ReporteHerramientasPendientesDevolucionDTO d : datos) {
            if (d.getPrestamosPendientes() == null) {
                continue;
            }
            for (ReportePrestamoPendienteDTO p : d.getPrestamosPendientes()) {
                filas.add(List.of(
                        s(p.getFolio()),
                        s(p.getNombreHerramienta()), s(p.getNumeroSerie()),
                        s(p.getPersonaRecibe()), s(p.getFechaEntrega()),
                        s(p.getFechaVencimiento()), s(p.getDiasVencido()), s(p.getCodigoBarras())));
            }
        }
        return new Tabla(List.of("Folio", "Herramienta", "No. serie", "Recibe", "Entrega",
                "Vencimiento", "Días vencido", "Código"), filas);
    }

    // ============================================================
    // Kardex y movimientos
    // ============================================================

    public static Tabla historialMovimientos(List<ReporteHistorialMovimientosDTO> datos) {
        List<List<String>> filas = new ArrayList<>();
        for (ReporteHistorialMovimientosDTO h : datos) {
            filas.add(List.of(
                    s(h.getFechaMovimiento()), s(h.getTipoMovimiento()), s(h.getFolio()),
                    s(h.getNombreProducto()), s(h.getCodigoBarras()), s(h.getCantidadMovida()),
                    s(h.getExistenciaAnterior()), s(h.getExistenciaPosterior()),
                    s(h.getNombreUsuario()), s(h.getObservaciones())));
        }
        return new Tabla(List.of("Fecha", "Tipo", "Folio", "Producto", "Código", "Cantidad",
                "Anterior", "Posterior", "Usuario", "Observaciones"), filas);
    }

    public static Tabla kardexProducto(ReporteKardexProductoDTO datos) {
        List<List<String>> filas = new ArrayList<>();
        if (datos != null && datos.getMovimientos() != null) {
            for (ReporteMovimientoKardexDTO m : datos.getMovimientos()) {
                filas.add(List.of(
                        s(datos.getNombreProducto()), s(datos.getCodigoBarras()),
                        s(datos.getSeccion()), s(m.getFecha()), s(m.getTipoMovimiento()),
                        s(m.getFolio()), s(m.getCantidadMovida()), s(m.getExistenciaAnterior()),
                        s(m.getExistenciaPosterior()), s(m.getNombreUsuario()),
                        s(m.getObservaciones())));
            }
        }
        return new Tabla(List.of("Producto", "Código", "Sección", "Fecha", "Tipo", "Folio",
                "Cantidad", "Anterior", "Posterior", "Usuario", "Observaciones"), filas);
    }

    public static Tabla movimientosPorUsuario(ReporteMovimientosUsuarioDTO datos) {
        List<List<String>> filas = new ArrayList<>();
        if (datos != null) {
            if (datos.getMovimientos() == null || datos.getMovimientos().isEmpty()) {
                filas.add(List.of(s(datos.getNombreUsuario()), s(datos.getEmailUsuario()),
                        s(datos.getTotalMovimientos()), s(datos.getFechaPrimerMovimiento()),
                        s(datos.getFechaUltimoMovimiento()), "", "", "", ""));
            } else {
                for (ReporteMovimientoUsuarioResumenDTO m : datos.getMovimientos()) {
                    filas.add(List.of(
                            s(datos.getNombreUsuario()), s(datos.getEmailUsuario()),
                            s(datos.getTotalMovimientos()), s(datos.getFechaPrimerMovimiento()),
                            s(datos.getFechaUltimoMovimiento()),
                            s(m.getTipoMovimiento()), s(m.getCantidad()), s(m.getFecha()),
                            s(m.getModulo())));
                }
            }
        }
        return new Tabla(List.of("Usuario", "Email", "Total movimientos", "Primer movimiento",
                "Último movimiento", "Tipo", "Cantidad", "Fecha", "Módulo"), filas);
    }

    /** Un valor ausente se exporta vacío, nunca como "null". */
    private static String s(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }
}
