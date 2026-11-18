package com.sapam.inventario.reportes;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba la conversión de los reportes a encabezados y filas, que es lo que
 * dibuja la aplicación de escritorio y lo que escribe en el CSV.
 *
 * Los casos con detalle anidado son los que más cuidado necesitan: si una fila
 * se desalinea respecto a los encabezados, el CSV sale corrupto.
 */
class ReportesTablaTest {

    /** Comprueba que todas las filas tienen exactamente tantas columnas. */
    private static void filasAlineadas(ReportesTabla.Tabla tabla, int columnas) {
        assertEquals(columnas, tabla.encabezados().size(),
                "los encabezados deben declarar " + columnas + " columnas");
        for (List<String> fila : tabla.filas()) {
            assertEquals(columnas, fila.size(), "la fila no cuadra con los encabezados: " + fila);
        }
    }

    private static String contenido(ReportesTabla.Tabla tabla) {
        return String.join("\n", tabla.filas().stream().map(String::valueOf).toList());
    }

    // ============================================================
    // Entradas y salidas: una fila por cada línea de producto
    // ============================================================

    @Test
    void cadaLineaDeUnaEntradaSeVuelveUnaFila() {
        ReporteEntradaProductoDTO linea1 = new ReporteEntradaProductoDTO();
        linea1.setNombreProducto("Tornillo");
        linea1.setCodigoBarras("T-001");
        linea1.setCantidadIngresada(100);
        linea1.setUnidadMedida("PZA");
        linea1.setCodigoInterno("INT-T-001");

        ReporteEntradaProductoDTO linea2 = new ReporteEntradaProductoDTO();
        linea2.setNombreProducto("Cable");
        linea2.setCodigoBarras("C-002");
        linea2.setCantidadIngresada(25);
        linea2.setUnidadMedida("M");
        linea2.setCodigoInterno("INT-C-002");

        ReporteEntradasPeriodoDTO entrada = new ReporteEntradasPeriodoDTO();
        entrada.setFolio("ENT-1");
        entrada.setFecha(LocalDate.of(2026, 10, 3));
        entrada.setHora(LocalTime.of(9, 15));
        entrada.setUsuarioRegistro("admin@sgia.com");
        entrada.setObservaciones("Recepción completa");
        entrada.setProductos(List.of(linea1, linea2));

        ReportesTabla.Tabla tabla = ReportesTabla.entradasPeriodo(List.of(entrada));

        assertEquals(2, tabla.filas().size(), "una entrada con dos líneas debe dar dos filas");
        filasAlineadas(tabla, 9);
        assertEquals("ENT-1", tabla.filas().get(0).get(0));
        assertEquals("Tornillo", tabla.filas().get(0).get(4));
        assertEquals("100", tabla.filas().get(0).get(6));
        assertEquals("Cable", tabla.filas().get(1).get(4));
        assertEquals("Recepción completa", tabla.filas().get(1).get(8));
    }

    @Test
    void unaSalidaConCamposVaciosNuncaEscribeNull() {
        ReporteSalidasPeriodoDTO salida = new ReporteSalidasPeriodoDTO();
        salida.setFolio("SAL-1");
        salida.setFecha(LocalDate.of(2026, 10, 3));
        salida.setPersonaSolicita("Carlos López");
        // puesto, departamento, motivo y autorizador quedan sin informar
        salida.setProductos(List.of());

        ReportesTabla.Tabla tabla = ReportesTabla.salidasPeriodo(List.of(salida));

        assertEquals(1, tabla.filas().size());
        filasAlineadas(tabla, 12);
        assertFalse(contenido(tabla).contains("null"), "un campo vacío debe ir en blanco: " + contenido(tabla));
        assertEquals("", tabla.filas().get(0).get(4), "el puesto sin informar sale vacío");
    }

    // ============================================================
    // Materiales entregados: cabecera repetida por cada material
    // ============================================================

    @Test
    void elReportePorTrabajadorRepiteLaCabeceraPorMaterial() {
        ReporteMaterialEntregadoDTO material1 = new ReporteMaterialEntregadoDTO();
        material1.setFolioSalida("SAL-1");
        material1.setNombreProducto("Tornillo");
        material1.setCantidadEntregada(10);

        ReporteMaterialEntregadoDTO material2 = new ReporteMaterialEntregadoDTO();
        material2.setFolioSalida("SAL-2");
        material2.setNombreProducto("Cable");
        material2.setCantidadEntregada(5);

        ReporteMaterialesTrabajadorDTO trabajador = new ReporteMaterialesTrabajadorDTO();
        trabajador.setNombreTrabajador("Carlos López");
        trabajador.setCargoTrabajador("Operario");
        trabajador.setDepartamentoTrabajador("Almacén");
        trabajador.setTotalMovimientos(2L);
        trabajador.setFechaUltimoMovimiento(LocalDate.of(2026, 10, 3));
        trabajador.setMaterialesEntregados(List.of(material1, material2));

        ReportesTabla.Tabla tabla = ReportesTabla.materialesTrabajador(List.of(trabajador));

        assertEquals(2, tabla.filas().size());
        filasAlineadas(tabla, 11);
        // la cabecera del trabajador se repite en las dos filas
        assertEquals("Carlos López", tabla.filas().get(0).get(0));
        assertEquals("Carlos López", tabla.filas().get(1).get(0));
        assertEquals("Tornillo", tabla.filas().get(0).get(7));
        assertEquals("Cable", tabla.filas().get(1).get(7));
    }

    @Test
    void elReportePorDepartamentoTieneLaCabeceraCorta() {
        ReporteMaterialEntregadoDTO material = new ReporteMaterialEntregadoDTO();
        material.setFolioSalida("SAL-1");
        material.setNombreProducto("Tornillo");
        material.setCantidadEntregada(10);

        ReporteMaterialesDepartamentoDTO departamento = new ReporteMaterialesDepartamentoDTO();
        departamento.setNombreDepartamento("Almacén");
        departamento.setAreaResponsable("Operaciones");
        departamento.setTotalMovimientos(1L);
        departamento.setMaterialesEntregados(List.of(material));

        ReportesTabla.Tabla tabla = ReportesTabla.materialesDepartamento(List.of(departamento));

        assertEquals(1, tabla.filas().size());
        filasAlineadas(tabla, 10);
        assertEquals("Almacén", tabla.filas().get(0).get(0));
        assertEquals("Operaciones", tabla.filas().get(0).get(1));
        assertEquals("Tornillo", tabla.filas().get(0).get(6));
    }

    @Test
    void unTrabajadorSinMaterialesDejaLaFilaEnBlanco() {
        ReporteMaterialesTrabajadorDTO trabajador = new ReporteMaterialesTrabajadorDTO();
        trabajador.setNombreTrabajador("María Gómez");
        trabajador.setMaterialesEntregados(List.of());

        ReportesTabla.Tabla tabla = ReportesTabla.materialesTrabajador(List.of(trabajador));

        assertEquals(1, tabla.filas().size());
        filasAlineadas(tabla, 11);
        assertEquals("María Gómez", tabla.filas().get(0).get(0));
        assertEquals("", tabla.filas().get(0).get(5), "el folio vacío va en blanco");
    }

    // ============================================================
    // Kardex y movimientos de usuario
    // ============================================================

    @Test
    void elKardexAplanaElHistorialDelProducto() {
        ReporteMovimientoKardexDTO movimiento = new ReporteMovimientoKardexDTO();
        movimiento.setFecha(LocalDate.of(2026, 10, 3));
        movimiento.setTipoMovimiento("ENTRADA");
        movimiento.setFolio("ENT-1");
        movimiento.setCantidadMovida(50);
        movimiento.setExistenciaAnterior(0);
        movimiento.setExistenciaPosterior(50);
        movimiento.setNombreUsuario("admin@sgia.com");

        ReporteKardexProductoDTO kardex = new ReporteKardexProductoDTO();
        kardex.setNombreProducto("Tornillo");
        kardex.setCodigoBarras("T-001");
        kardex.setSeccion("Ferretería");
        kardex.setMovimientos(List.of(movimiento));

        ReportesTabla.Tabla tabla = ReportesTabla.kardexProducto(kardex);

        assertEquals(1, tabla.filas().size());
        filasAlineadas(tabla, 11);
        assertEquals("Tornillo", tabla.filas().get(0).get(0));
        assertEquals("ENTRADA", tabla.filas().get(0).get(4));
        assertEquals("50", tabla.filas().get(0).get(6));
        assertEquals("0", tabla.filas().get(0).get(7));
    }

    @Test
    void unProductoSinMovimientosSigueMostrandoLosEncabezados() {
        ReportesTabla.Tabla tabla = ReportesTabla.kardexProducto(null);

        assertTrue(tabla.filas().isEmpty());
        assertEquals(11, tabla.encabezados().size(), "con cero registros el usuario ve las columnas");
    }

    @Test
    void losMovimientosDeUnUsuarioSinResumenQuedanEnBlanco() {
        ReporteMovimientosUsuarioDTO usuario = new ReporteMovimientosUsuarioDTO();
        usuario.setNombreUsuario("Consulta");
        usuario.setEmailUsuario("consulta@sgia.com");
        usuario.setTotalMovimientos(0L);
        // sin resumen por tipo: nunca debe aparecer la palabra "null"

        ReportesTabla.Tabla tabla = ReportesTabla.movimientosPorUsuario(usuario);

        assertEquals(1, tabla.filas().size());
        filasAlineadas(tabla, 9);
        assertFalse(contenido(tabla).contains("null"));
        assertEquals("consulta@sgia.com", tabla.filas().get(0).get(1));
        assertEquals("", tabla.filas().get(0).get(5));
    }

    @Test
    void elResumenDeMovimientosDeUnUsuarioAgrupaPorTipo() {
        ReporteMovimientoUsuarioResumenDTO resumen = new ReporteMovimientoUsuarioResumenDTO();
        resumen.setTipoMovimiento("SALIDA");
        resumen.setCantidad(3L);
        resumen.setFecha(LocalDate.of(2026, 10, 3));
        resumen.setModulo("Salidas de almacén");

        ReporteMovimientosUsuarioDTO usuario = new ReporteMovimientosUsuarioDTO();
        usuario.setNombreUsuario("Consulta");
        usuario.setTotalMovimientos(3L);
        usuario.setMovimientos(List.of(resumen));

        ReportesTabla.Tabla tabla = ReportesTabla.movimientosPorUsuario(usuario);

        assertEquals(1, tabla.filas().size());
        filasAlineadas(tabla, 9);
        assertEquals("SALIDA", tabla.filas().get(0).get(5));
        assertEquals("Salidas de almacén", tabla.filas().get(0).get(8));
    }

    // ============================================================
    // Herramientas
    // ============================================================

    @Test
    void elReporteDeHerramientasPrestadasAplanaLosPrestamos() {
        ReportePrestamoHerramientaDTO prestamo = new ReportePrestamoHerramientaDTO();
        prestamo.setFolio("PRE-1001");
        prestamo.setNombreHerramienta("Martillo percutor");
        prestamo.setNumeroSerie("SN-77");
        prestamo.setPersonaRecibe("Carlos López");
        prestamo.setFechaEntrega(LocalDate.of(2026, 10, 1));
        prestamo.setHoraEntrega("09:15");
        prestamo.setFechaEstimadaDevolucion(LocalDate.of(2026, 10, 8));
        prestamo.setEstado("PENDIENTE");
        prestamo.setCodigoBarras("HER-1");

        ReporteHerramientasPrestadasDTO reporte = new ReporteHerramientasPrestadasDTO();
        reporte.setPrestamosActivos(List.of(prestamo));

        ReportesTabla.Tabla tabla = ReportesTabla.herramientasPrestadas(List.of(reporte));

        assertEquals(1, tabla.filas().size());
        filasAlineadas(tabla, 10);
        assertEquals("PRE-1001", tabla.filas().get(0).get(0),
                "el folio del préstamo encabeza la fila");
        assertEquals("Martillo percutor", tabla.filas().get(0).get(1));
        assertEquals("Carlos López", tabla.filas().get(0).get(3));
        assertFalse(contenido(tabla).contains("null"));
    }

    @Test
    void elReporteDeHerramientasVencidasMuestraLosDiasDeRetraso() {
        ReportePrestamoPendienteDTO pendiente = new ReportePrestamoPendienteDTO();
        pendiente.setFolio("PRE-1002");
        pendiente.setNombreHerramienta("Escalera");
        pendiente.setPersonaRecibe("María Gómez");
        pendiente.setFechaEntrega(LocalDate.of(2026, 9, 25));
        pendiente.setFechaVencimiento(LocalDate.of(2026, 9, 30));
        pendiente.setDiasVencido(3L);
        pendiente.setCodigoBarras("HER-2");

        ReporteHerramientasPendientesDevolucionDTO reporte =
                new ReporteHerramientasPendientesDevolucionDTO();
        reporte.setPrestamosPendientes(List.of(pendiente));

        ReportesTabla.Tabla tabla = ReportesTabla.herramientasPendientesDevolucion(List.of(reporte));

        assertEquals(1, tabla.filas().size());
        filasAlineadas(tabla, 8);
        assertEquals("PRE-1002", tabla.filas().get(0).get(0),
                "el folio del préstamo encabeza la fila");
        assertEquals("Escalera", tabla.filas().get(0).get(1));
        assertEquals("3", tabla.filas().get(0).get(6),
                "los días de retraso van calculados en la fila");
        assertEquals("HER-2", tabla.filas().get(0).get(7));
    }

    @Test
    void losReportesDeHerramientasSinRegistrosConservanLosEncabezados() {
        ReportesTabla.Tabla prestadas = ReportesTabla.herramientasPrestadas(List.of());
        ReportesTabla.Tabla vencidas =
                ReportesTabla.herramientasPendientesDevolucion(List.of());

        assertTrue(prestadas.filas().isEmpty());
        assertEquals(10, prestadas.encabezados().size());
        assertTrue(vencidas.filas().isEmpty());
        assertEquals(8, vencidas.encabezados().size());
    }
}
