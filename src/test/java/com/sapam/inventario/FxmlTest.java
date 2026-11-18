package com.sapam.inventario;

import com.sapam.inventario.entity.Herramienta;
import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.entity.PrestamoHerramienta;
import com.sapam.inventario.entity.SalidaAlmacen;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.javafx.ImpresionEtiqueta;
import com.sapam.inventario.javafx.JavaFxApplication;
import com.sapam.inventario.javafx.controller.BitacoraController;
import com.sapam.inventario.javafx.controller.EntradaFormController;
import com.sapam.inventario.javafx.controller.HerramientaFormController;
import com.sapam.inventario.javafx.controller.HerramientasController;
import com.sapam.inventario.javafx.controller.KardexProductoController;
import com.sapam.inventario.javafx.controller.LoginController;
import com.sapam.inventario.javafx.controller.MainController;
import com.sapam.inventario.javafx.controller.ProductoFormController;
import com.sapam.inventario.javafx.controller.SalidaFormController;
import com.sapam.inventario.javafx.controller.TicketPrestamoController;
import com.sapam.inventario.javafx.controller.TicketSalidaController;
import com.sapam.inventario.javafx.controller.UsuariosController;
import com.sapam.inventario.service.BitacoraService;
import com.sapam.inventario.service.HerramientaService;
import com.sapam.inventario.service.ProductoService;
import com.sapam.inventario.service.CodigoBarrasService;
import com.sapam.inventario.service.SalidaAlmacenService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.GenericApplicationContext;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Carga el FXML de verdad.
 *
 * Los errores de FXML (un fx:id sin campo, un handler que no existe, un
 * import que falta) no fallan la compilación: solo saltan cuando la ventana
 * se abre. Aquí se fuerza esa carga para que no dependa de probarlo a mano.
 *
 * FXMLLoader construye el controlador fuera de Spring, así que los servicios
 * quedan a null; para {@code producto-form} no importa porque {@code initialize()}
 * únicamente pinta el código de barras, y un producto nuevo aún no
 * tiene ninguno de los dos: deja los dos huecos vacíos, sin avisos.
 */
class FxmlTest {

    private static boolean toolkitArrancado = false;

    @BeforeAll
    static void arrancarToolkitJavaFx() throws Exception {
        try {
            CountDownLatch listo = new CountDownLatch(1);
            Platform.startup(listo::countDown);
            assertTrue(listo.await(20, TimeUnit.SECONDS), "JavaFX no terminó de arrancar");
            toolkitArrancado = true;
        } catch (IllegalStateException yaArrancado) {
            // Otro test ya dejó el toolkit encendido: seguir con él.
        }

        // Al cerrar la última ventana JavaFX apaga su propio hilo (salida
        // implícita, que viene activada), y una vez apagado ningún runLater
        // vuelve a ejecutarse: las pruebas siguientes se quedarían esperando
        // delante de una tarea que nadie coge.
        CountDownLatch ajustado = new CountDownLatch(1);
        Platform.runLater(() -> {
            Platform.setImplicitExit(false);
            ajustado.countDown();
        });
        assertTrue(ajustado.await(20, TimeUnit.SECONDS),
                "no se pudo desactivar la salida implícita de JavaFX");
    }

    @AfterAll
    static void apagarToolkitJavaFx() {
        if (toolkitArrancado) {
            Platform.exit();
        }
    }

    @Test
    void elFormularioDeProductoSeCargaYLosControlesDeCodigoSeInyectan() throws Exception {
        URL recurso = getClass().getResource("/view/producto-form.fxml");
        assertNotNull(recurso, "falta el recurso /view/producto-form.fxml");

        FXMLLoader cargador = new FXMLLoader(recurso);
        Parent raiz = cargador.load();

        assertNotNull(raiz, "la vista debe tener contenido");

        ProductoFormController controlador = cargador.getController();
        assertNotNull(controlador, "el FXML debe instanciar su controlador");

        // Si un fx:id no tuviera campo en el controlador, FXMLLoader lo ignora
        // en silencio: se comprueba explícitamente que se inyectó.
        ImageView imgCodigo = (ImageView) leer(controlador, "imgCodigo");
        Label lblCodigoBarras = (Label) leer(controlador, "lblCodigoBarras");
        Label lblCodigo = (Label) leer(controlador, "lblCodigo");
        Button btnImprimirCodigo = (Button) leer(controlador, "btnImprimirCodigo");

        assertNotNull(imgCodigo, "fx:id=\"imgCodigo\" no se inyectó");
        assertNotNull(lblCodigoBarras, "fx:id=\"lblCodigoBarras\" no se inyectó");
        assertNotNull(lblCodigo, "fx:id=\"lblCodigo\" no se inyectó");
        assertNotNull(btnImprimirCodigo, "fx:id=\"btnImprimirCodigo\" no se inyectó");

        // El código ya no se teclea: el campo pasó de TextField a Label de solo
        // lectura. Y un producto nuevo ya no avisa de nada: los dos huecos, el
        // del código y el del código dibujado, quedan vacíos hasta que el
        // sistema le asigna uno al guardar.
        assertEquals("", lblCodigo.getText(),
                "un producto nuevo no debe enseñar ningún aviso sobre su código");
        assertEquals("", lblCodigoBarras.getText(),
                "ni sobre el código dibujado tampoco: los dos huecos quedan vacíos");

        // Carga sin excepción ya valida que #imprimirEtiqueta, #guardarProducto
        // y #cancelar existen en el controlador.
        assertTrue(btnImprimirCodigo.isDisable(),
                "sin código (producto nuevo) el botón de imprimir debe venir deshabilitado");
        assertNull(imgCodigo.getImage(), "sin código no debe haber imagen");

        // La sección es el único criterio con el que se pide el orden del
        // inventario, así que el desplegable ofrece justo las cinco y un
        // producto nuevo empieza sin elegir, para que nadie guarde una
        // sección que no escogió.
        ComboBox<?> seccion = (ComboBox<?>) leer(controlador, "cbSeccion");
        assertNotNull(seccion, "fx:id=\"cbSeccion\" no se inyectó");
        assertEquals(ProductoService.SECCIONES, seccion.getItems(),
                "el desplegable debe ofrecer las secciones del inventario, ni una más");
        assertNull(seccion.getValue(), "un producto nuevo empieza sin sección elegida");
    }

    @Test
    void elFormularioDeEntradaSeCargaYPreparaLaTabla() throws Exception {
        URL recurso = getClass().getResource("/view/entrada-form.fxml");
        assertNotNull(recurso, "falta el recurso /view/entrada-form.fxml");

        FXMLLoader cargador = new FXMLLoader(recurso);
        Parent raiz = cargador.load();
        assertNotNull(raiz, "la vista debe tener contenido");

        EntradaFormController controlador = cargador.getController();
        assertNotNull(controlador, "el FXML debe instanciar su controlador");

        // initialize() no debe tocar servicios: fuera de Spring están a null.
        TableView<?> tabla = (TableView<?>) leer(controlador, "tablaProductos");
        assertNotNull(tabla, "fx:id=\"tablaProductos\" no se inyectó");
        assertNotNull(tabla.getColumns(), "la tabla debe tener sus columnas");
        assertEquals(3, tabla.getColumns().size(), "producto, cantidad y corrección");

        // La columna de corrección es la que evita cancelar la entrada entera
        // cuando un renglón queda mal; sin su fx:id no se inyecta y no hay
        // dónde colgar los botones −, + y Quitar.
        assertNotNull(leer(controlador, "colAcciones"),
                "fx:id=\"colAcciones\" no se inyectó");

        assertNotNull(leer(controlador, "cmbProducto"), "falta el combo de productos");
        assertNotNull(leer(controlador, "lblResumen"), "falta el resumen");

        // La caja del lector: cada lectura suma una unidad del producto
        // escaneado. Sin ella en el FXML el escaneo no tendría dónde caer.
        TextField escanear = (TextField) leer(controlador, "txtEscanear");
        assertNotNull(escanear, "fx:id=\"txtEscanear\" no se inyectó");
    }

    /**
     * El lector repite el mismo código mientras la caja llena: leer la
     * etiqueta tres veces tiene que dejar tres unidades en un solo renglón,
     * no tres renglones ni una sola unidad. Es lo que permite contar una
     * caja entera escaneando, que es para lo que se compró el lector.
     */
    @Test
    void escanearElMismoCodigoTresVecesAcumulaTresUnidades() throws Exception {
        URL recurso = getClass().getResource("/view/entrada-form.fxml");
        assertNotNull(recurso, "falta el recurso /view/entrada-form.fxml");

        FXMLLoader cargador = new FXMLLoader(recurso);
        cargador.load();
        EntradaFormController controlador = cargador.getController();

        Producto producto = new Producto();
        producto.setId(1);
        producto.setNombre("Tornillo 1 pulgada");
        producto.setCodigoBarras("10007");

        ProductoService productoService = Mockito.mock(ProductoService.class);
        Mockito.when(productoService.buscarPorCodigoBarrasOInterno("10007"))
                .thenReturn(Optional.of(producto));
        inyectar(controlador, "productoService", productoService);

        TextField escanear = (TextField) leer(controlador, "txtEscanear");
        // agregarPorEscaneo no recibe nada: el lector escribe el código y el
        // Enter del propio lector es lo que dispara el handler del FXML.
        Method escanearMetodo = EntradaFormController.class
                .getDeclaredMethod("agregarPorEscaneo");
        escanearMetodo.setAccessible(true);

        // Los controles y los avisos solo se tocan en el hilo de JavaFX, así
        // que la parte que se queda esperando se despacha ahí.
        AtomicReference<Throwable> fallo = new AtomicReference<>();
        CountDownLatch terminado = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                for (int i = 0; i < 3; i++) {
                    escanear.setText("10007");
                    escanearMetodo.invoke(controlador);
                }
            } catch (Throwable t) {
                fallo.set(t);
            } finally {
                terminado.countDown();
            }
        });
        assertTrue(terminado.await(20, TimeUnit.SECONDS),
                "JavaFX no terminó de procesar los escaneos");
        if (fallo.get() != null) {
            throw new AssertionError("falló dentro del hilo de JavaFX", fallo.get());
        }

        @SuppressWarnings("unchecked")
        var lineas = (List<EntradaFormController.Linea>) leer(controlador, "lineas");
        assertEquals(1, lineas.size(),
                "el mismo código debe quedar en un solo renglón, no en tres");
        assertEquals(3, lineas.get(0).getCantidad(),
                "tres lecturas del mismo código deben sumar tres unidades");
        assertEquals("Tornillo 1 pulgada", lineas.get(0).getProducto().getNombre());

        // Y la caja queda vacía, lista para el siguiente escaneo.
        assertEquals("", escanear.getText(),
                "la caja del lector debe quedar limpia tras cada lectura");

        // Un producto dado de baja no entra por escaneo: se avisa y no se
        // agrega. El aviso lo cierra un hilo aparte, porque la prueba no tiene
        // a nadie que le dé al botón.
        Producto baja = new Producto();
        baja.setId(2);
        baja.setNombre("Dado de baja");
        baja.setCodigoBarras("10008");
        baja.setEstado("BAJAS");
        Mockito.when(productoService.buscarPorCodigoBarrasOInterno("10008"))
                .thenReturn(Optional.of(baja));

        AtomicBoolean seguirCerrando = new AtomicBoolean(true);
        Thread cerrador = new Thread(() -> {
            while (seguirCerrando.get()) {
                Platform.runLater(() -> new ArrayList<>(Window.getWindows()).forEach(ventana -> {
                    if (ventana.isShowing()
                            && ventana.getScene() != null
                            && ventana.getScene().getRoot() instanceof DialogPane) {
                        ventana.hide();
                    }
                }));
                try {
                    Thread.sleep(40);
                } catch (InterruptedException parado) {
                    return;
                }
            }
        });
        cerrador.setDaemon(true);
        cerrador.start();

        AtomicReference<Throwable> falloBaja = new AtomicReference<>();
        CountDownLatch terminadoBaja = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                escanear.setText("10008");
                escanearMetodo.invoke(controlador);
            } catch (Throwable t) {
                falloBaja.set(t);
            } finally {
                terminadoBaja.countDown();
            }
        });
        assertTrue(terminadoBaja.await(20, TimeUnit.SECONDS),
                "JavaFX no terminó de procesar el escaneo de baja");
        seguirCerrando.set(false);
        cerrador.interrupt();

        if (falloBaja.get() != null) {
            throw new AssertionError("falló dentro del hilo de JavaFX", falloBaja.get());
        }
        assertEquals(1, lineas.size(),
                "un producto en estado BAJAS no debe agregarse por escaneo");
    }

    /**
     * La etiqueta que se manda a la impresora tiene que conservar el código.
     *
     * El código de una herramienta es más largo que el de un producto y, si
     * la etiqueta lo reduce al maquetarla, el lector deja de verlo. Esta
     * prueba compone el bitmap final y lo decodifica, que es exactamente lo
     * que hace el escáner sobre el papel.
     */
    @Test
    void laEtiquetaDeHerramientaSePuedeEscanearDespuesDeComponerla() throws Exception {
        CodigoBarrasService codigos = new CodigoBarrasService();
        BufferedImage codigo = codigos.generar(
                "HER-ABC123", CodigoBarrasService.TAMANO_DEFECTO);

        AtomicReference<BufferedImage> etiqueta = new AtomicReference<>();
        AtomicReference<Throwable> fallo = new AtomicReference<>();
        CountDownLatch hecha = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                etiqueta.set(ImpresionEtiqueta.componer(
                        codigo, "Taladro percutor", "SN-123", "HER-ABC123"));
            } catch (Throwable t) {
                fallo.set(t);
            } finally {
                hecha.countDown();
            }
        });

        assertTrue(hecha.await(20, TimeUnit.SECONDS),
                "JavaFX no terminó de componer la etiqueta");
        if (fallo.get() != null) {
            throw new AssertionError("falló al componer la etiqueta", fallo.get());
        }
        assertNotNull(etiqueta.get(), "la etiqueta compuesta no puede ser nula");
        assertEquals(Optional.of("HER-ABC123"), codigos.decodificar(etiqueta.get()),
                "el código de la herramienta tiene que seguir leyéndose en la etiqueta");
    }

    /**
     * Un renglón mal hecho se arregla en el sitio.
     *
     * Antes, si la cantidad salía mal la única salida era Cancelar la entrada
     * entera y volver a empezar. Con − se resta de una en una, y al llegar a
     * 1 el siguiente − se lleva el renglón, porque 0 unidades no entra. Con +
     * se suma, y Quitar borra el producto completo del renglón.
     */
    @Test
    void unRenglonSeCorrigeEnElSitioSinCancelarLaEntrada() throws Exception {
        URL recurso = getClass().getResource("/view/entrada-form.fxml");
        assertNotNull(recurso, "falta el recurso /view/entrada-form.fxml");

        FXMLLoader cargador = new FXMLLoader(recurso);
        cargador.load();
        EntradaFormController controlador = cargador.getController();

        Producto producto = new Producto();
        producto.setId(1);
        producto.setNombre("Tornillo 1 pulgada");

        @SuppressWarnings("unchecked")
        var lineas = (List<EntradaFormController.Linea>) leer(controlador, "lineas");
        EntradaFormController.Linea linea = new EntradaFormController.Linea(producto, 3);
        lineas.add(linea);

        Method restar = EntradaFormController.class.getDeclaredMethod("restarUna",
                EntradaFormController.Linea.class);
        Method sumar = EntradaFormController.class.getDeclaredMethod("sumarUna",
                EntradaFormController.Linea.class);
        Method quitar = EntradaFormController.class.getDeclaredMethod("quitarLinea",
                EntradaFormController.Linea.class);
        restar.setAccessible(true);
        sumar.setAccessible(true);
        quitar.setAccessible(true);

        AtomicReference<Throwable> fallo = new AtomicReference<>();
        CountDownLatch terminado = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                // − dos veces: de 3 a 1, sin pasar de largo ni romper el resumen.
                restar.invoke(controlador, linea);
                restar.invoke(controlador, linea);
            } catch (Throwable t) {
                fallo.set(t);
            } finally {
                terminado.countDown();
            }
        });
        assertTrue(terminado.await(20, TimeUnit.SECONDS),
                "JavaFX no terminó de procesar el −");
        if (fallo.get() != null) {
            throw new AssertionError("falló dentro del hilo de JavaFX", fallo.get());
        }

        assertEquals(1, linea.getCantidad(), "− debe restar una unidad por pulsación");
        assertEquals(1, lineas.size(), "restar no debe borrar el renglón mientras quede 1");

        // El siguiente − se lleva el renglón: 0 unidades no entra.
        CountDownLatch terminadoCero = new CountDownLatch(1);
        AtomicReference<Throwable> falloCero = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                restar.invoke(controlador, linea);
            } catch (Throwable t) {
                falloCero.set(t);
            } finally {
                terminadoCero.countDown();
            }
        });
        assertTrue(terminadoCero.await(20, TimeUnit.SECONDS),
                "JavaFX no terminó de procesar el − final");
        if (falloCero.get() != null) {
            throw new AssertionError("falló dentro del hilo de JavaFX", falloCero.get());
        }
        assertTrue(lineas.isEmpty(), "al llegar a 0 el renglón debe irse solo");

        // Y el + tampoco hace falta cancelar para subir una unidad.
        EntradaFormController.Linea otra = new EntradaFormController.Linea(producto, 2);
        lineas.add(otra);
        CountDownLatch terminadoMas = new CountDownLatch(1);
        AtomicReference<Throwable> falloMas = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                sumar.invoke(controlador, otra);
            } catch (Throwable t) {
                falloMas.set(t);
            } finally {
                terminadoMas.countDown();
            }
        });
        assertTrue(terminadoMas.await(20, TimeUnit.SECONDS),
                "JavaFX no terminó de procesar el +");
        if (falloMas.get() != null) {
            throw new AssertionError("falló dentro del hilo de JavaFX", falloMas.get());
        }
        assertEquals(3, otra.getCantidad(), "+ debe sumar una unidad por pulsación");

        // Quitar borra el renglón entero, cuando el producto era el equivocado.
        CountDownLatch terminadoQuitar = new CountDownLatch(1);
        AtomicReference<Throwable> falloQuitar = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                quitar.invoke(controlador, otra);
            } catch (Throwable t) {
                falloQuitar.set(t);
            } finally {
                terminadoQuitar.countDown();
            }
        });
        assertTrue(terminadoQuitar.await(20, TimeUnit.SECONDS),
                "JavaFX no terminó de procesar Quitar");
        if (falloQuitar.get() != null) {
            throw new AssertionError("falló dentro del hilo de JavaFX", falloQuitar.get());
        }
        assertTrue(lineas.isEmpty(), "Quitar debe llevarse el renglón completo");
    }

    @Test
    void elFormularioDeSalidaSeCargaYPreparaLosMotivos() throws Exception {
        URL recurso = getClass().getResource("/view/salida-form.fxml");
        assertNotNull(recurso, "falta el recurso /view/salida-form.fxml");

        FXMLLoader cargador = new FXMLLoader(recurso);
        Parent raiz = cargador.load();
        assertNotNull(raiz, "la vista debe tener contenido");

        SalidaFormController controlador = cargador.getController();
        assertNotNull(controlador, "el FXML debe instanciar su controlador");

        assertNotNull(leer(controlador, "txtCantidad"), "falta el campo de cantidad");
        assertNotNull(leer(controlador, "txtObservaciones"), "falta el campo de observaciones");
        assertNotNull(leer(controlador, "cbSolicita"), "falta el combo de quien solicita");
        assertNotNull(leer(controlador, "cbRecibe"), "falta el combo de quien recibe");

        // La orden de trabajo es lo que autoriza la salida y la teclea quien la
        // da: sin ella no hay formulario que la deje pasar.
        assertNotNull(leer(controlador, "txtOrdenTrabajo"),
                "falta el campo de número de orden de trabajo");

        // Al salir con el motivo en OTRO aparece la caja para escribir de qué se
        // trata, y mientras el motivo sea otro de los cinco no estorba.
        javafx.scene.control.TextField motivoOtro =
                (javafx.scene.control.TextField) leer(controlador, "txtMotivoOtro");
        assertNotNull(motivoOtro, "falta la caja del motivo OTRO");
        assertFalse(motivoOtro.isVisible(), "la caja del motivo OTRO debe empezar oculta");
        assertFalse(motivoOtro.isManaged(), "y sin ocupar espacio mientras esté oculta");

        // El puesto y el área de quien recibe se quitaron del formulario: ya no
        // se le piden esos datos a quien retira el material.
        for (String quitado : new String[]{"txtPuestoRecibe", "txtDepartamentoRecibe"}) {
            assertFalse(java.util.Arrays.stream(SalidaFormController.class.getDeclaredFields())
                            .anyMatch(campo -> campo.getName().equals(quitado)),
                    quitado + " se quitó del formulario y ya no debe existir");
        }

        // El responsable no se elige: es la cuenta con la que se entró. Por eso
        // es una etiqueta y no un combo, y no hay manera de firmar por otra cuenta.
        Label responsable = (Label) leer(controlador, "lblResponsable");
        assertNotNull(responsable, "falta la etiqueta de quien responde por la salida");
        assertFalse(responsable.getText().isEmpty(),
                "debe explicar que el responsable sale de la sesión y no se cambia");

        // Los catálogos (quién ha solicitado y los usuarios que reciben) sí
        // tocan base, así que se cargan al recibir el usuario; los motivos no y
        // deben estar ya aquí.
        ComboBox<?> motivos = (ComboBox<?>) leer(controlador, "cbMotivo");
        assertNotNull(motivos, "falta el combo de motivos");
        assertEquals(SalidaAlmacenService.MOTIVOS, motivos.getItems(),
                "initialize() debe poblar los motivos sin depender de la base");
    }

    /**
     * El papel que se entrega con la salida.
     *
     * Ninguna otra prueba carga este FXML, así que un tecleo mal puesto aquí no
     * se notaría compilando ni corriendo las pruebas: se notaría el día que
     * alguien entregue material y el ticket salga en blanco.
     */
    @Test
    void elTicketDeSalidaSeCargaYTraeTodoLoQueHayQueEntregar() throws Exception {
        URL recurso = getClass().getResource("/view/ticket-salida.fxml");
        assertNotNull(recurso, "falta el recurso /view/ticket-salida.fxml");

        FXMLLoader cargador = new FXMLLoader(recurso);
        Parent raiz = cargador.load();
        assertNotNull(raiz, "la vista debe tener contenido");

        TicketSalidaController controlador = cargador.getController();
        assertNotNull(controlador, "el FXML debe instanciar su controlador");

        SalidaAlmacen salida = new SalidaAlmacen();
        salida.setFolio("SAL-123");
        salida.setFecha(LocalDate.of(2026, 10, 19));
        salida.setHora(LocalTime.of(14, 30, 5));
        salida.setOrdenTrabajo("OT-4521");
        salida.setPersonaSolicita("Carlos López");
        salida.setPuestoSolicita("Jefe de turno");
        salida.setDepartamentoSolicita("Almacén");
        salida.setPersonaRecibe("Ana Torres");
        salida.setMotivoSalida("REPARACION");
        salida.setObservaciones("Cambio de boquilla");

        controlador.cargar(salida, List.of(
                new TicketSalidaController.ProductoDelTicket("Taladro", 2),
                new TicketSalidaController.ProductoDelTicket("Cinta métrica", 1)));

        assertEquals("Folio: SAL-123", texto(controlador, "lblFolio"));
        assertEquals("19/10/2026  14:30:05", texto(controlador, "lblFechaHora"),
                "el ticket debe llevar la fecha y la hora de quién lo dio");
        assertEquals("OT-4521", texto(controlador, "lblOrdenTrabajo"));
        assertEquals("Almacén", texto(controlador, "lblArea"));
        assertEquals("Ana Torres", texto(controlador, "lblRecibe"));
        assertEquals("REPARACION", texto(controlador, "lblMotivo"));
        assertEquals("Cambio de boquilla", texto(controlador, "lblObservaciones"),
                "el texto de observaciones es lo que pide el papel");

        javafx.scene.layout.VBox productos =
                (javafx.scene.layout.VBox) leer(controlador, "vbProductos");
        assertEquals(2, productos.getChildren().size(),
                "el ticket debe listar todos los productos de la salida");

        // Ancho del ticket para la POS-80. La prueba de impresión confirmó
        // que el driver entrega unos 272 px imprimibles y que el recuadro de
        // 272 px sale completo; el ticket se queda en 192 px para dejar margen
        // y no pegar el texto al borde. El límite superior evita volver a los
        // anchos que se recortaban y el inferior evita un ticket demasiado
        // estrecho.
        javafx.scene.layout.VBox papel =
                (javafx.scene.layout.VBox) leer(controlador, "vbTicket");
        assertTrue(papel.getPrefWidth() <= 196,
                "el ticket mide " + papel.getPrefWidth()
                        + " px; el máximo seguro es 196 para dejar margen");
        assertTrue(papel.getPrefWidth() >= 160,
                "el ticket mide solo " + papel.getPrefWidth() + " px: estrecho, "
                        + "sale con un montón de rollo en blanco");

        // Los huecos se dicen en voz alta: un guion avisa más que un blanco que
        // parece error de impresión.
        SalidaAlmacen sinMas = new SalidaAlmacen();
        sinMas.setFolio("SAL-456");
        sinMas.setMotivoSalida("INSTALACION");
        controlador.cargar(sinMas, List.of());

        assertEquals("(sin observaciones)", texto(controlador, "lblObservaciones"));
        assertEquals("—", texto(controlador, "lblOrdenTrabajo"),
                "sin orden el papel debe decir que no hay");
        assertEquals("—", texto(controlador, "lblArea"));
        assertEquals(1, ((javafx.scene.layout.VBox) leer(controlador, "vbProductos"))
                        .getChildren().size(),
                "sin productos el papel lo dice en vez de dejar el hueco en blanco");
    }

    /**
     * El ticket de préstamo lleva quién se lleva la herramienta, el área y el
     * destino, y su código de barras tiene que seguir leyéndose después del
     * pegado a 203 dpi. Es el papel con el que después se devuelve.
     */
    @Test
    void elTicketDePrestamoSeCargaYElCodigoDelFolioSeLee() throws Exception {
        URL recurso = getClass().getResource("/view/ticket-prestamo.fxml");
        assertNotNull(recurso, "falta el recurso /view/ticket-prestamo.fxml");

        FXMLLoader cargador = new FXMLLoader(recurso);
        Parent raiz = cargador.load();
        assertNotNull(raiz, "la vista debe tener contenido");

        TicketPrestamoController controlador = cargador.getController();
        assertNotNull(controlador, "el FXML debe instanciar su controlador");

        Herramienta herramienta = new Herramienta();
        herramienta.setNombre("Taladro Bosch");
        herramienta.setCodigoBarras("HER-ABC123");
        herramienta.setNumeroSerie("SN-77");

        PrestamoHerramienta prestamo = new PrestamoHerramienta();
        prestamo.setFolio("PRE-ABC123");
        prestamo.setHerramienta(herramienta);
        prestamo.setPersonaRecibe("Ana Torres");
        prestamo.setAreaResponsable("Mantenimiento");
        prestamo.setDestino("Obra norte");
        prestamo.setFechaEntrega(LocalDateTime.of(2026, 11, 7, 9, 15));
        prestamo.setFechaEstimadaDevolucion(LocalDate.of(2026, 11, 14));
        prestamo.setPersonaEntrega("Carlos López");
        prestamo.setObservaciones("Taladro con broca");

        controlador.cargar(prestamo);

        assertEquals("Folio: PRE-ABC123", texto(controlador, "lblFolio"));
        assertEquals("Código: PRE-ABC123", texto(controlador, "lblCodigoTexto"));
        assertEquals("07/11/2026 09:15", texto(controlador, "lblFechaHora"));
        assertEquals("Ana Torres", texto(controlador, "lblRecibe"));
        assertEquals("Mantenimiento", texto(controlador, "lblArea"));
        assertEquals("Obra norte", texto(controlador, "lblDestino"),
                "el ticket debe decir para dónde va la herramienta");
        assertEquals("Taladro Bosch", texto(controlador, "lblHerramienta"));
        assertEquals("HER-ABC123", texto(controlador, "lblCodigoHerramienta"));
        assertEquals("SN-77", texto(controlador, "lblSerie"));
        assertEquals("14/11/2026", texto(controlador, "lblDevolverAntes"));
        assertEquals("Carlos López", texto(controlador, "lblEntrego"));
        assertEquals("Taladro con broca", texto(controlador, "lblObservaciones"));

        ImageView codigo = (ImageView) leer(controlador, "imgCodigo");
        assertNotNull(codigo.getImage(), "el ticket debe enseñar su código de barras");

        // El contenido no puede pasar del área imprimible de la POS-80: el
        // ticket de salida ya usa 192 px y aquí el código necesita algo más de
        // ancho, pero 260 px deja margen dentro de los 272 px medidos.
        javafx.scene.layout.VBox papel =
                (javafx.scene.layout.VBox) leer(controlador, "vbTicket");
        assertTrue(papel.getPrefWidth() <= 264,
                "el ticket mide " + papel.getPrefWidth()
                        + " px; el máximo seguro es 264 para dejar margen");
        assertTrue(papel.getPrefWidth() >= 240,
                "el ticket mide solo " + papel.getPrefWidth()
                        + " px: el código de barras quedaría demasiado apretado");

        // La composición final se hace en el hilo de JavaFX, igual que al
        // imprimir. El bitmap resultante tiene que seguir decodificando el
        // folio que se entregó en papel.
        AtomicReference<BufferedImage> imagen = new AtomicReference<>();
        AtomicReference<Throwable> fallo = new AtomicReference<>();
        CountDownLatch listo = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                imagen.set(controlador.componer());
            } catch (Throwable t) {
                fallo.set(t);
            } finally {
                listo.countDown();
            }
        });
        assertTrue(listo.await(30, TimeUnit.SECONDS),
                "JavaFX no terminó de componer el ticket de préstamo");
        if (fallo.get() != null) {
            throw new AssertionError("falló dentro del hilo de JavaFX", fallo.get());
        }

        assertEquals(Optional.of("PRE-ABC123"),
                new CodigoBarrasService().decodificar(imagen.get()),
                "el código del ticket tiene que seguir leyéndose en el papel");
    }

    /**
     * El buscador no debe depender de que el lector mande exactamente el
     * folio pelado: hay equipos que agregan un prefijo de simbología o el
     * salto de línea. Lo importante es sacar el PRE-... y buscar ese folio.
     */
    @Test
    void elBuscadorReconoceElFolioAunqueElLectorAgreguePrefijoOSaltoDeLinea() throws Exception {
        URL recurso = getClass().getResource("/view/herramientas.fxml");
        assertNotNull(recurso, "falta el recurso /view/herramientas.fxml");

        FXMLLoader cargador = new FXMLLoader(recurso);
        cargador.load();
        HerramientasController controlador = cargador.getController();
        assertNotNull(controlador, "el FXML debe instanciar su controlador");

        assertEquals("PRE-30A8AA",
                invocar(controlador, "folioDeTicket", String.class, "]C1pre-30a8aa\r\n"),
                "el prefijo del lector y el Enter no deben romper la búsqueda");
        assertEquals("PRE-594A82",
                invocar(controlador, "folioDeTicket", String.class, "PRE'594A82"),
                "un lector con distribución inglesa manda el guion como apóstrofo");
        assertEquals("PRE-594A82",
                invocar(controlador, "folioDeTicket", String.class, "PRE_594A82"),
                "otras distribuciones pueden mandar el guion como guion bajo");
        assertEquals("HER-EE9650",
                invocar(controlador, "normalizarCodigo", String.class, "]C1HER'EE9650"),
                "el código de una herramienta también se tiene que corregir");
        assertEquals("PRE-1794033257861",
                invocar(controlador, "folioDeTicket", String.class, "PRE-1794033257861"),
                "los tickets viejos también se tienen que poder devolver");
        assertNull(invocar(controlador, "folioDeTicket", String.class, "Desarmador"),
                "un nombre de herramienta no es un folio de ticket");
        assertNull(invocar(controlador, "folioDeTicket", String.class, "PRE-30"),
                "un folio incompleto no debe disparar una búsqueda");
    }

    /** El texto que deja un Label inyectado por el FXML. */
    private String texto(Object controlador, String campo) throws Exception {
        return ((Label) leer(controlador, campo)).getText();
    }

    /**
     * "OTRO" es el único motivo que no dice nada por sí solo, así que al
     * elegirlo aparece la caja para escribir de qué se trata —y solo cuando
     * corresponde: con los otros cinco no debe estorbar ni arrastrar lo que se
     * hubiera tecleado antes.
     */
    @Test
    void laCajaDelMotivoOtroSoloSeMuestraCuandoElMotivoEsOtro() throws Exception {
        URL recurso = getClass().getResource("/view/salida-form.fxml");
        assertNotNull(recurso, "falta el recurso /view/salida-form.fxml");

        FXMLLoader cargador = new FXMLLoader(recurso);
        cargador.load();
        SalidaFormController controlador = cargador.getController();

        @SuppressWarnings("unchecked")
        ComboBox<String> motivo = (ComboBox<String>) leer(controlador, "cbMotivo");
        TextField caja = (TextField) leer(controlador, "txtMotivoOtro");
        Label etiqueta = (Label) leer(controlador, "lblMotivoOtro");

        Method cambio = SalidaFormController.class.getDeclaredMethod("motivoCambiado");
        cambio.setAccessible(true);

        motivo.setValue("REPARACION");
        cambio.invoke(controlador);
        assertFalse(caja.isVisible(), "con un motivo de los cinco no hay caja que mostrar");
        assertFalse(etiqueta.isVisible(), "ni su etiqueta");
        assertFalse(caja.isManaged(), "y no debe dejar un hueco en blanco en el formulario");

        motivo.setValue("OTRO");
        cambio.invoke(controlador);
        assertTrue(caja.isVisible(), "con OTRO hay que poder escribir de qué se trata");
        assertTrue(etiqueta.isVisible(), "con su etiqueta, para que se entienda qué se pide");

        caja.setText("Retiro de material para la mudanza");
        motivo.setValue("INSTALACION");
        cambio.invoke(controlador);
        assertFalse(caja.isVisible(), "al volver a otro motivo la caja se va");
        assertEquals("", caja.getText(),
                "lo que se hubiera escrito para OTRO no debe quedarse pegado a otro motivo");
    }

    @Test
    void laVentanaDeBitacoraSeCargaSinOpcionDeBorrado() throws Exception {
        URL recurso = getClass().getResource("/view/bitacora.fxml");
        assertNotNull(recurso, "falta el recurso /view/bitacora.fxml");

        FXMLLoader cargador = new FXMLLoader(recurso);
        Parent raiz = cargador.load();
        assertNotNull(raiz, "la vista debe tener contenido");

        BitacoraController controlador = cargador.getController();
        assertNotNull(controlador, "el FXML debe instanciar su controlador");

        // initialize() solo monta las columnas: fuera de Spring el servicio está
        // a null y la ventana igualmente debe abrirse.
        TableView<?> tabla = (TableView<?>) leer(controlador, "tablaEventos");
        assertNotNull(tabla, "fx:id=\"tablaEventos\" no se inyectó");
        assertEquals(8, tabla.getColumns().size(),
                "fecha, hora, usuario, acción, módulo, qué cambió, observaciones y código");

        assertNotNull(leer(controlador, "cbAccion"), "falta el filtro de acción");
        assertNotNull(leer(controlador, "cbUsuario"), "falta el filtro de usuario");
        assertNotNull(leer(controlador, "lblResumen"), "falta el resumen");

        // La paginación: la bitácora no se carga entera, se recorre de 200 en
        // 200, así que hace falta de dónde decir en dónde se está.
        assertNotNull(leer(controlador, "lblPagina"), "falta el rótulo de página");
        assertNotNull(leer(controlador, "btnAnterior"), "falta el botón de página anterior");
        assertNotNull(leer(controlador, "btnSiguiente"), "falta el botón de página siguiente");

        // Requisito de bitácora ineliminable: no debe existir ni en el FXML ni
        // en el controlador nada que borre o edite un evento.
        for (Method metodo : BitacoraController.class.getDeclaredMethods()) {
            String nombre = metodo.getName().toLowerCase(java.util.Locale.ROOT);
            assertFalse(nombre.contains("eliminar") || nombre.contains("borrar")
                            || nombre.contains("quitar") || nombre.contains("editar"),
                    "la bitácora no debe tener método de borrado o edición: " + metodo.getName());
        }
    }

    @Test
    void laVentanaDeKardexSeCargaYPreparaElHistorial() throws Exception {
        URL recurso = getClass().getResource("/view/kardex-producto.fxml");
        assertNotNull(recurso, "falta el recurso /view/kardex-producto.fxml");

        FXMLLoader cargador = new FXMLLoader(recurso);
        Parent raiz = cargador.load();
        assertNotNull(raiz, "la vista debe tener contenido");

        KardexProductoController controlador = cargador.getController();
        assertNotNull(controlador, "el FXML debe instanciar su controlador");

        TableView<?> tabla = (TableView<?>) leer(controlador, "tablaKardex");
        assertNotNull(tabla, "fx:id=\"tablaKardex\" no se inyectó");
        assertEquals(9, tabla.getColumns().size(),
                "fecha, hora, tipo, cantidad, antes, después, folio, responsable y observaciones");

        // La cabecera se rellena al abrir la ventana con el producto elegido,
        // así que aquí solo se comprueba que el control existe.
        assertNotNull(leer(controlador, "lblProducto"), "falta la cabecera del producto");
        assertNotNull(leer(controlador, "lblStock"), "falta la existencia actual");
    }

    @Test
    void laVentanaDeHerramientasSeCargaSinTocarLaBase() throws Exception {
        URL recurso = getClass().getResource("/view/herramientas.fxml");
        assertNotNull(recurso, "falta el recurso /view/herramientas.fxml");

        FXMLLoader cargador = new FXMLLoader(recurso);
        Parent raiz = cargador.load();
        assertNotNull(raiz, "la vista debe tener contenido");

        HerramientasController controlador = cargador.getController();
        assertNotNull(controlador, "el FXML debe instanciar su controlador");

        // initialize() solo monta columnas y los botones vienen bloqueados:
        // fuera de Spring el servicio está a null y la ventana igual debe abrirse.
        TableView<?> tabla = (TableView<?>) leer(controlador, "tabla");
        assertNotNull(tabla, "fx:id=\"tabla\" no se inyectó");
        assertEquals(8, tabla.getColumns().size(),
                "código, nombre, marca, modelo, serie, estado, área y situación");

        Button prestar = (Button) leer(controlador, "btnPrestar");
        Button devolver = (Button) leer(controlador, "btnDevolver");
        assertNotNull(prestar, "falta el botón de préstamo");
        assertNotNull(devolver, "falta el botón de devolución");
        assertTrue(prestar.isDisable(), "sin selección no se puede prestar");
        assertTrue(devolver.isDisable(), "sin selección no se puede devolver");

        // Editar y dar de baja existen pero vienen bloqueados: sin sesión de
        // administrador (abrir() no se llamó aquí) no se habilitan.
        Button editar = (Button) leer(controlador, "btnEditar");
        Button eliminar = (Button) leer(controlador, "btnEliminar");
        assertNotNull(editar, "falta el botón de edición");
        assertNotNull(eliminar, "falta el botón de baja");
        assertTrue(editar.isDisable(), "sin sesión de administrador no se puede editar");
        assertTrue(eliminar.isDisable(), "sin sesión de administrador no se puede dar de baja");
    }

    /**
     * La ventana de cuentas se carga sin tocar base de datos, como las demás:
     * fuera de Spring el servicio está a null y la ventana igual debe abrirse.
     * Los datos entran con {@code abrir()}.
     */
    @Test
    void laVentanaDeUsuariosSeCargaSinTocarLaBase() throws Exception {
        URL recurso = getClass().getResource("/view/usuarios.fxml");
        assertNotNull(recurso, "falta el recurso /view/usuarios.fxml");

        FXMLLoader cargador = new FXMLLoader(recurso);
        Parent raiz = cargador.load();
        assertNotNull(raiz, "la vista debe tener contenido");

        UsuariosController controlador = cargador.getController();
        assertNotNull(controlador, "el FXML debe instanciar su controlador");

        TableView<?> tabla = (TableView<?>) leer(controlador, "tabla");
        assertNotNull(tabla, "fx:id=\"tabla\" no se inyectó");
        assertEquals(5, tabla.getColumns().size(),
                "nombre, correo, rol, estado y alta");

        // Sin selección no se puede editar ni tocar el estado de nada.
        assertTrue(((Button) leer(controlador, "btnEditar")).isDisable(),
                "sin selección no se puede editar");
        assertTrue(((Button) leer(controlador, "btnBaja")).isDisable(),
                "sin selección no se puede dar de baja");
        assertTrue(((Button) leer(controlador, "btnReactivar")).isDisable(),
                "sin selección no se puede reactivar");

        // Aquí sí se borra una cuenta, pero solo si no registró nada: la baja
        // de quien ya firmó entradas o bitácora es lógica. Lo que no debe
        // existir es método alguno de borrado físico.
        for (Method metodo : UsuariosController.class.getDeclaredMethods()) {
            String nombre = metodo.getName().toLowerCase(java.util.Locale.ROOT);
            assertFalse(nombre.contains("eliminar") || nombre.contains("borrar")
                            || nombre.contains("quitar"),
                    "la cuenta no se borra: se da de baja; sobra " + metodo.getName());
        }
    }

    /**
     * El permiso no vive en el botón que la ventana principal le oculta a los
     * demás roles: la propia ventana vuelve a comprobarlo al abrirse.
     *
     * Se comprueba con los roles que no deben entrar, porque esos salen antes
     * de tocar base de datos —el servicio es null aquí—, que es justo lo que
     * demuestra que el control va primero.
     */
    @Test
    void laVentanaDeUsuariosSoloSeAbreParaAdministrador() throws Exception {
        URL recurso = getClass().getResource("/view/usuarios.fxml");
        FXMLLoader cargador = new FXMLLoader(recurso);
        cargador.load();

        UsuariosController controlador = cargador.getController();

        assertFalse(controlador.abrir(null), "sin sesión abierta no se abre");

        Usuario operativo = new Usuario();
        operativo.setNombre("Operativo");
        operativo.setRol(Usuario.Rol.usuario);
        assertFalse(controlador.abrir(operativo), "el operativo no administra cuentas");

        Usuario consulta = new Usuario();
        consulta.setNombre("Consulta");
        consulta.setRol(Usuario.Rol.consulta);
        assertFalse(controlador.abrir(consulta), "consulta no administra cuentas");
    }

    @Test
    void elFormularioDeHerramientaNoPideElCodigoPorqueLoAsignaElSistema() throws Exception {
        URL recurso = getClass().getResource("/view/herramienta-form.fxml");
        assertNotNull(recurso, "falta el recurso /view/herramienta-form.fxml");

        FXMLLoader cargador = new FXMLLoader(recurso);
        Parent raiz = cargador.load();
        assertNotNull(raiz, "la vista debe tener contenido");
        assertNotNull(cargador.getController(), "el FXML debe instanciar su controlador");

        // Ni fx:id ni onAction deben mencionar un campo de código tecleable.
        String xml = new String(recurso.openStream().readAllBytes(), StandardCharsets.UTF_8);
        assertFalse(xml.contains("fx:id=\"txtCodigo\""),
                "el código no se teclea: el sistema lo genera al guardar");

        ComboBox<?> estados = (ComboBox<?>) leer(cargador.getController(), "cbEstado");
        assertEquals(HerramientaService.ESTADOS_FISICOS, estados.getItems(),
                "initialize() debe poblar los estados sin depender de la base");

        // La etiqueta es la misma que la de productos: imagen, código de
        // solo lectura y botón de imprimir, todos preparados en initialize().
        ImageView imgCodigo = (ImageView) leer(cargador.getController(), "imgCodigo");
        Label lblCodigoBarras = (Label) leer(cargador.getController(), "lblCodigoBarras");
        Button imprimir = (Button) leer(cargador.getController(), "btnImprimirCodigo");
        assertNotNull(imgCodigo, "falta la imagen del código");
        assertNotNull(lblCodigoBarras, "falta la etiqueta del código");
        assertNotNull(imprimir, "falta el botón de imprimir etiqueta");
        assertEquals("(se genera al guardar)", lblCodigoBarras.getText(),
                "sin código aún debe verse el aviso, no un valor vacío");
        assertNull(imgCodigo.getImage(), "sin código no debe haber imagen");
        assertTrue(imprimir.isDisable(), "sin código no se puede imprimir la etiqueta");
    }

    /**
     * {@code main.fxml} no se puede abrir fuera de la aplicación porque
     * {@link MainController#initialize()} pide el contexto de Spring, así que se
     * comprueba lo que sí puede fallar en silencio: que cada botón llame a un
     * método que exista.
     *
     * Si un {@code onAction} apunta a un nombre equivocado, la ventana principal
     * no llega a abrirse y solo se ve al ejecutarla a mano.
     */
    @Test
    void todosLosBotonesDeLaPantallaPrincipalLlamanAMetodosQueExisten() throws Exception {
        URL recurso = getClass().getResource("/view/main.fxml");
        assertNotNull(recurso, "falta el recurso /view/main.fxml");

        String xml = new String(recurso.openStream().readAllBytes(), StandardCharsets.UTF_8);
        Matcher manejadores = Pattern.compile("onAction=\"#(\\w+)\"").matcher(xml);
        Set<String> llamados = new LinkedHashSet<>();
        while (manejadores.find()) {
            llamados.add(manejadores.group(1));
        }
        assertFalse(llamados.isEmpty(), "main.fxml debe declarar botones con onAction");

        Set<String> metodos = new LinkedHashSet<>();
        for (Method metodo : MainController.class.getDeclaredMethods()) {
            metodos.add(metodo.getName());
        }

        for (String nombre : llamados) {
            assertTrue(metodos.contains(nombre),
                    "main.fxml llama a #" + nombre + " pero MainController no tiene ese método");
        }
    }

    /**
     * Los destinos de la ventana principal están en los botones, que es lo que
     * se le pone al cliente. No debe volver una barra de menú que los repite:
     * las dos vías se acaban desincronizando y el usuario no sabe cuál manda.
     */
    @Test
    void laPantallaPrincipalNavegaPorBotonesYSinBarraDeMenu() throws Exception {
        URL recurso = getClass().getResource("/view/main.fxml");
        assertNotNull(recurso, "falta el recurso /view/main.fxml");
        String xml = new String(recurso.openStream().readAllBytes(), StandardCharsets.UTF_8);

        assertFalse(xml.contains("<MenuBar"), "la barra de menú duplica los botones de abajo");
        assertFalse(xml.contains("<MenuItem"), "lo mismo para los elementos sueltos de menú");

        for (String destino : List.of("#buscarProducto", "#abrirNuevoProducto", "#abrirEntrada",
                "#abrirKardex", "#abrirBitacora", "#generarReporte", "#cerrarSesion",
                "#abrirHerramientas", "#filtrarSeccion", "#abrirUsuarios")) {
            assertTrue(xml.contains(destino), "falta el botón que llama a " + destino);
        }
        assertTrue(xml.contains("fx:id=\"lblSesion\""),
                "la cabecera debe decir quién tiene la sesión abierta y con qué rol");

        // Las cinco secciones, que es lo que sustituyó a los comodatos en
        // esta fila. El texto del botón es la sección: si cambia uno de
        // estos, cambia también lo que se filtra.
        for (String seccion : ProductoService.SECCIONES) {
            assertTrue(xml.contains("text=\"" + seccion + "\""),
                    "falta el botón de la sección " + seccion);
        }
        assertTrue(xml.contains("text=\"Todo\""),
                "sin el botón de Todo no hay forma de volver al inventario completo");
    }

    /**
     * El filtro de secciones es lo que hay detrás de los botones de la fila:
     * se queda con los productos de la sección elegida, devuelve todo cuando
     * no hay sección y no inventa nada para la que no tiene productos.
     */
    @Test
    void losBotonesDeSeccionFiltranElInventario() {
        Producto papeleria = productoDeSeccion("Papelería");
        Producto construccion = productoDeSeccion("Construcción");
        Producto sinSeccion = productoDeSeccion(null);
        List<Producto> inventario = List.of(papeleria, construccion, sinSeccion);

        assertEquals(3, ProductoService.deSeccion(inventario, "").size(),
                "sin sección se ve el inventario entero");
        assertEquals(3, ProductoService.deSeccion(inventario, null).size());
        assertEquals(1, ProductoService.deSeccion(inventario, "Papelería").size(),
                "una sección se queda con lo suyo");
        assertEquals(0, ProductoService.deSeccion(inventario, "Uniformes").size(),
                "una sección vacía sale con cero filas, nunca con las ajenas");
    }

    private Producto productoDeSeccion(String seccion) {
        Producto producto = new Producto();
        producto.setNombre("Producto de " + (seccion == null ? "ninguna sección" : seccion));
        producto.setSeccion(seccion);
        return producto;
    }

    /**
     * La leyenda del pie de la tabla general tiene que ser cierta: dice verde
     * para el que alcanza, amarillo para el que está por debajo del mínimo y
     * rojo para el que no tiene nada.
     *
     * Se comprueban sobre todo los dos lindes, que son los únicos puntos
     * donde cambia de color y donde un error de un solo carácter se vería
     * como una fila pintada al revés sin que nada más estuviera roto.
     */
    @Test
    void laLeyendaDeStockDiceLoQueLaTablaPinta() {
        assertEquals("#F8D7DA", MainController.colorDeFila(productoConStock(0, 5)),
                "sin existencias: rojo");
        assertEquals("#F8D7DA", MainController.colorDeFila(productoConStock(-3, 5)),
                "negativo también es no tener");
        assertEquals("#FFF3CD", MainController.colorDeFila(productoConStock(4, 5)),
                "debajo del mínimo: amarillo");
        assertEquals("#D4EDDA", MainController.colorDeFila(productoConStock(5, 5)),
                "en el mínimo ya alcanza: verde");
        assertEquals("#D4EDDA", MainController.colorDeFila(productoConStock(90, 5)),
                "por encima del mínimo: verde");
        assertEquals("", MainController.colorDeFila(null),
                "las filas de relleno de abajo no se pintan");
        assertEquals("#D4EDDA", MainController.colorDeFila(productoConStock(3, null)),
                "un mínimo sin fijar no es un mínimo bajo: no se marca amarilla");
    }

    private Producto productoConStock(int actual, Integer minimo) {
        Producto producto = new Producto();
        producto.setNombre("Producto con stock de prueba");
        producto.setExistenciaActual(actual);
        producto.setExistenciaMinima(minimo);
        return producto;
    }

    /**
     * La ficha bajo la tabla es donde se lee lo que no cabe en ella, y sobre
     * todo la descripción: es lo único que se teclea al dar de alta y que no
     * aparece en ninguna columna. Se pierde ahí si no se cuenta.
     */
    @Test
    void laFichaCuentaLoQueLaTablaNoCuenta() {
        assertEquals("", MainController.fichaDe(null),
                "sin producto marcado no se cuenta nada");

        Producto conTodo = productoConStock(3, 5);
        conTodo.setCodigoBarras("HER-1234");
        conTodo.setCodigoInterno("INT-5678");
        conTodo.setSeccion("Ferretería");
        conTodo.setDescripcion("  Bulto de 50 kg  ");

        String ficha = MainController.fichaDe(conTodo);
        assertTrue(ficha.contains("Sección: Ferretería"), "la sección sí se cuenta");
        assertTrue(ficha.contains("Cód. barras: HER-1234"), "el código de barras sí se cuenta");
        assertTrue(ficha.contains("Cód. interno: INT-5678"),
                "el código interno, que el sistema genera solo, hay que contarlo aquí");
        assertTrue(ficha.contains("Stock: 3"), "el stock va en la ficha");
        assertTrue(ficha.contains("mínimo 5"), "y el mínimo con él");
        assertTrue(ficha.contains("Descripción: Bulto de 50 kg"),
                "la descripción no se pierde ni con los espacios de más");

        Producto vacio = productoConStock(0, 5);
        assertFalse(MainController.fichaDe(vacio).contains("Descripción"),
                "sin descripción no se deja un renglón de pega");
        assertTrue(MainController.fichaDe(vacio).contains("(sin dato)"),
                "lo que no tiene se dice en pantalla y no se deja en blanco");
    }

    /**
     * Un {@code fx:id} sin campo no falla la compilación: la ventana se abre
     * igual y revienta con un {@code NullPointerException} en cuanto algo lo
     * toque, o no se inyecta y se queda a null hasta ese momento.
     *
     * Se comprueba por texto porque main.fxml no se puede abrir fuera de la
     * aplicación, y se cruzan los ids del XML con los campos del controlador.
     */
    @Test
    void todosLosIdDelXmlDeLaPantallaPrincipalTienenSuCampo() throws Exception {
        URL recurso = getClass().getResource("/view/main.fxml");
        assertNotNull(recurso, "falta el recurso /view/main.fxml");

        String xml = new String(recurso.openStream().readAllBytes(), StandardCharsets.UTF_8);
        Matcher ids = Pattern.compile("fx:id=\"(\\w+)\"").matcher(xml);
        Set<String> declarados = new LinkedHashSet<>();
        while (ids.find()) {
            declarados.add(ids.group(1));
        }
        assertFalse(declarados.isEmpty(), "main.fxml debe declarar fx:id");

        Set<String> campos = new LinkedHashSet<>();
        for (Field campo : MainController.class.getDeclaredFields()) {
            campos.add(campo.getName());
        }

        for (String id : declarados) {
            assertTrue(campos.contains(id),
                    "fx:id=\"" + id + "\" no tiene campo en MainController");
        }
    }

    /**
     * Ver los colores en una lista de cadenas no basta: hay que verlos en la
     * tabla, y sobre todo comprobar que el color de la fila no tape la
     * seleccionada.
     *
     * El kardex y la salida se abren sobre la fila marcada, y si el fondo de
     * la fila ganara no se vería cuál es la que se está usando. Por eso la
     * tabla se monta de verdad dentro del hilo de JavaFX: el pintado lo hace
     * la piel al recorrer las celdas, y sobre un objeto suelto, fuera de un
     * escenario, no pasaría nada.
     */
    @Test
    void laTablaPintaLasFallasYLaSeleccionNoTapaNada() throws Exception {
        List<String> antes = new ArrayList<>();
        List<String> despues = new ArrayList<>();

        CountDownLatch listo = new CountDownLatch(1);
        Platform.runLater(() -> {
            TableView<Producto> tabla = new TableView<>();
            tabla.setItems(FXCollections.observableArrayList(
                    productoConStock(0, 5), productoConStock(4, 5), productoConStock(90, 5)));
            tabla.setRowFactory(t -> MainController.filaDeProductos());
            TableColumn<Producto, String> columna = new TableColumn<>("Stock");
            columna.setCellValueFactory(new PropertyValueFactory<>("existenciaActual"));
            tabla.getColumns().add(columna);

            Stage escenario = new Stage();
            escenario.setScene(new Scene(tabla, 320, 200));
            escenario.show();

            tabla.applyCss();
            tabla.layout();
            pintarFilas(tabla, antes);

            tabla.getSelectionModel().select(1);
            tabla.applyCss();
            tabla.layout();
            pintarFilas(tabla, despues);

            escenario.close();
            listo.countDown();
        });

        assertTrue(listo.await(20, TimeUnit.SECONDS), "no se pudo montar la tabla de prueba");

        assertEquals("-fx-background-color: #F8D7DA;", colorDeLaFila(antes, 0),
                "la fila sin existencias sale roja");
        assertEquals("-fx-background-color: #FFF3CD;", colorDeLaFila(antes, 1),
                "la de debajo del mínimo sale amarilla");
        assertEquals("-fx-background-color: #D4EDDA;", colorDeLaFila(antes, 2),
                "la que llega al mínimo sale verde");
        assertEquals("", colorDeLaFila(antes, 3), "la fila de relleno no se pinta");

        assertEquals("-fx-background-color: #BBDEFB;", colorDeLaFila(despues, 1),
                "la seleccionada se marca a ella");
        assertEquals("-fx-background-color: #F8D7DA;", colorDeLaFila(despues, 0),
                "las demás siguen con su color: la selección no tapa nada");
    }

    /** El estilo de cada fila, con su índice delante para poder buscarla. */
    private static void pintarFilas(TableView<Producto> tabla, List<String> acu) {
        for (Node nodo : tabla.lookupAll(".table-row-cell")) {
            if (nodo instanceof TableRow<?> fila) {
                acu.add(fila.getIndex() + ":" + fila.getStyle());
            }
        }
    }

    private static String colorDeLaFila(List<String> filas, int indice) {
        return filas.stream()
                .filter(fila -> fila.startsWith(indice + ":"))
                .map(fila -> fila.substring(fila.indexOf(':') + 1))
                .findFirst()
                .orElse("");
    }

    /**
     * Los permisos de la interfaz no se ven en el FXML: se deciden en
     * {@code configurarRol()}. Aquí se comprueba la tabla entera, porque un
     * cambio de rol equivocado solo se nota ejecutando la aplicación y con una
     * cuenta que no es la tuya.
     */
    @Test
    void configurarRolDaATCadaRolLoQueLeCorresponde() throws Exception {
        MainController controlador = new MainController();

        inyectar(controlador, "tablaProductos", new TableView<>());
        Button nuevo = new Button();
        Button entrada = new Button();
        Button salida = new Button();
        Button reporte = new Button();
        Button bitacora = new Button();
        Button usuarios = new Button();
        inyectar(controlador, "btnNuevoProducto", nuevo);
        inyectar(controlador, "btnEntrada", entrada);
        inyectar(controlador, "btnSalida", salida);
        inyectar(controlador, "btnReporte", reporte);
        inyectar(controlador, "btnBitacora", bitacora);
        inyectar(controlador, "btnUsuarios", usuarios);

        Label sesion = new Label();
        inyectar(controlador, "lblSesion", sesion);

        Usuario ana = new Usuario();
        ana.setNombre("Ana");
        ana.setRol(Usuario.Rol.consulta);
        inyectar(controlador, "usuarioActual", ana);

        // Consulta: solo lectura. Ve inventario, los reportes y la bitácora,
        // pero no escribe nada ni toca las cuentas.
        controlador.configurarRol("consulta");
        assertFalse(nuevo.isVisible(), "consulta no da de alta productos");
        assertFalse(entrada.isVisible(), "consulta no registra entradas");
        assertFalse(salida.isVisible(), "consulta no saca material del almacén");
        assertTrue(reporte.isVisible(), "consulta sí ve reportes");
        assertTrue(bitacora.isVisible(), "consulta lee la bitácora: es solo lectura");
        assertFalse(usuarios.isVisible(), "consulta no administra cuentas");
        assertTrue(sesion.getText().contains("Ana"), "la cabecera dice quién está");
        assertTrue(sesion.getText().contains("Consulta"), "la cabecera dice el rol");

        // Operativo: mueve almacén y consulta —entradas, salidas, kardex,
        // reportes y herramientas—, pero no toca el catálogo ni la bitácora
        // ni las cuentas.
        controlador.configurarRol("usuario");
        assertFalse(nuevo.isVisible(), "operativo no crea productos");
        assertTrue(entrada.isVisible(), "operativo sí registra entradas");
        assertTrue(salida.isVisible(), "operativo sí saca material del almacén");
        assertTrue(reporte.isVisible(), "operativo sí genera reportes");
        assertFalse(bitacora.isVisible(), "operativo no revisa la bitácora");
        assertFalse(usuarios.isVisible(), "operativo no administra cuentas");

        // Administrador: todo.
        controlador.configurarRol("admin");
        assertTrue(nuevo.isVisible(), "administrador crea productos");
        assertTrue(entrada.isVisible(), "administrador registra entradas");
        assertTrue(salida.isVisible(), "administrador saca material del almacén");
        assertTrue(reporte.isVisible(), "administrador genera reportes");
        assertTrue(bitacora.isVisible(), "administrador lee la bitácora");
        assertTrue(usuarios.isVisible(), "administrador administra cuentas");

        // Rol desconocido o nulo: lo más restrictivo, que era la rama final de
        // antes y no debe perderse.
        controlador.configurarRol("nadie");
        assertFalse(nuevo.isVisible(), "rol sin definir no escribe");
        assertFalse(entrada.isVisible(), "rol sin definir no escribe");
        assertFalse(salida.isVisible(), "rol sin definir no escribe");
        assertFalse(reporte.isVisible(), "rol sin definir no ve reportes");
        assertFalse(bitacora.isVisible(), "rol sin definir no ve la bitácora");
        assertFalse(usuarios.isVisible(), "rol sin definir no toca cuentas");
    }

    /**
     * El rol Consulta entra en herramientas a mirar: ahí no hay
     * columna oculta que lo frene, así que tienen que desaparecerle los botones
     * que escriben —alta, préstamo y devolución— igual que los de edición.
     */
    @Test
    void elRolConsultaNoEscribeNadaEnHerramientas() throws Exception {
        URL herramientas = getClass().getResource("/view/herramientas.fxml");
        assertNotNull(herramientas, "falta el recurso /view/herramientas.fxml");
        FXMLLoader cargador = new FXMLLoader(herramientas);
        cargador.load();
        HerramientasController herramientasControl = cargador.getController();

        Usuario consulta = usuario(Usuario.Rol.consulta);
        herramientasControl.abrir(consulta);
        assertFalse(((Button) leer(herramientasControl, "btnNueva")).isVisible(),
                "consulta no da de alta herramientas");
        assertFalse(((Button) leer(herramientasControl, "btnPrestar")).isVisible(),
                "consulta no presta herramientas");
        assertFalse(((Button) leer(herramientasControl, "btnDevolver")).isVisible(),
                "consulta no registra devoluciones");
        assertFalse(((Button) leer(herramientasControl, "btnEditar")).isVisible(),
                "consulta no edita herramientas");
        assertFalse(((Button) leer(herramientasControl, "btnEliminar")).isVisible(),
                "consulta no da de baja herramientas");

        herramientasControl.abrir(usuario(Usuario.Rol.usuario));
        assertFalse(((Button) leer(herramientasControl, "btnNueva")).isVisible(),
                "el operativo no da de alta herramientas: eso es del catálogo");
        assertTrue(((Button) leer(herramientasControl, "btnPrestar")).isVisible(),
                "el operativo sí presta");
        assertTrue(((Button) leer(herramientasControl, "btnDevolver")).isVisible(),
                "el operativo sí devuelve");
        assertFalse(((Button) leer(herramientasControl, "btnEditar")).isVisible(),
                "editar sigue siendo cosa de administrador");
        assertFalse(((Button) leer(herramientasControl, "btnEliminar")).isVisible(),
                "tampoco da de baja");
    }

    /**
     * Cerrar sesión es cambiar de usuario, no apagar el equipo: la ventana
     * sigue abierta y dentro queda la pantalla de acceso para quien venga a
     * continuación, con su usuario y su rol.
     */
    @Test
    void cerrarSesionVuelveALaPantallaDeAccesoSinCerrarLaVentana() throws Exception {
        // Cargar login.fxml pide el controlador a Spring, y en este test no hay
        // aplicación de escritorio arrancada: se deja un contexto mínimo que
        // se lo devuelva tal cual se lo pida.
        GenericApplicationContext contexto = new GenericApplicationContext();
        contexto.registerBean(LoginController.class, LoginController::new);
        contexto.refresh();

        Field campoContexto = JavaFxApplication.class.getDeclaredField("context");
        campoContexto.setAccessible(true);
        ConfigurableApplicationContext previo =
                (ConfigurableApplicationContext) campoContexto.get(null);
        campoContexto.set(null, contexto);

        // Un Stage solo se toca en el hilo de JavaFX, así que la prueba entera
        // se despacha ahí y aquí se espera a que termine.
        AtomicReference<Throwable> fallo = new AtomicReference<>();
        CountDownLatch terminado = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage escenario = new Stage();
            try {
                TableView<Producto> tabla = new TableView<>();
                escenario.setScene(new Scene(tabla));
                escenario.show();

                MainController controlador = new MainController();
                inyectar(controlador, "tablaProductos", tabla);
                BitacoraService bitacora = Mockito.mock(BitacoraService.class);
                inyectar(controlador, "bitacoraService", bitacora);

                Method cerrar = MainController.class.getDeclaredMethod("cerrarSesion");
                cerrar.setAccessible(true);
                cerrar.invoke(controlador);

                assertTrue(escenario.isShowing(),
                        "cerrar sesión no debe apagar la aplicación");
                assertEquals("SGIA", escenario.getTitle(),
                        "vuelve el título de la pantalla de acceso");

                Parent raiz = escenario.getScene().getRoot();
                assertNotNull(raiz.lookup("#usernameField"),
                        "la ventana debe quedar en la pantalla de acceso");
                assertNotNull(raiz.lookup("#passwordField"),
                        "con la contraseña lista para el siguiente usuario");

                // El cierre también deja su rastro, que es lo que pide la trazabilidad.
                Mockito.verify(bitacora).registrar(
                        Mockito.eq(BitacoraService.LOGOUT), Mockito.eq("SEGURIDAD"),
                        Mockito.anyString(), Mockito.anyString(), Mockito.isNull());
            } catch (Throwable t) {
                fallo.set(t);
            } finally {
                escenario.close();
                terminado.countDown();
            }
        });

        assertTrue(terminado.await(20, TimeUnit.SECONDS),
                "JavaFX no terminó de cerrar la sesión");
        campoContexto.set(null, previo);
        contexto.close();

        if (fallo.get() != null) {
            throw new AssertionError("falló dentro del hilo de JavaFX", fallo.get());
        }
    }

    /**
     * En la aplicación el controlador del formulario lo pide Spring con
     * {@code getBean}, y Spring devuelve siempre el mismo si nadie pide otra
     * cosa. Con la ficha reutilizada, la ventana de "Nueva herramienta"
     * guardaba encima de la que se acababa de crear: el alta se convertía en
     * una edición y el registro anterior desaparecía.
     *
     * Aquí se reproduce la cadena real: mismo controlador, dos ventanas.
     */
    @Test
    void unaVentanaDeAltaDeHerramientaNoHeredaLaFichaDeLaAnterior() throws Exception {
        URL recurso = getClass().getResource("/view/herramienta-form.fxml");
        assertNotNull(recurso, "falta el recurso /view/herramienta-form.fxml");

        HerramientaFormController controlador = new HerramientaFormController();

        FXMLLoader primera = new FXMLLoader(recurso);
        primera.setControllerFactory(t -> controlador);
        primera.load();

        // Así queda la ficha justo después de que la base la guarde.
        Herramienta guardada = (Herramienta) leer(controlador, "herramienta");
        guardada.setNombre("Taladro");
        guardada.setId(7L);

        FXMLLoader segunda = new FXMLLoader(recurso);
        segunda.setControllerFactory(t -> controlador);
        segunda.load();

        Herramienta siguiente = (Herramienta) leer(controlador, "herramienta");
        assertNotNull(siguiente, "la ventana de alta necesita su propia ficha");
        assertNull(siguiente.getId(),
                "la ficha anterior siguió colgada: en lugar de dar de alta otra "
                        + "herramienta se habría sobrescrito la que ya existía");
        assertNull(siguiente.getNombre(), "y con sus datos, que saldrían como propios");
    }

    /**
     * Guardar no deja la ventana esperando a que alguien le dé a Cancelar: se
     * cierra sola. Mientras esté abierta se le puede volver a dar a Guardar, y
     * cada pulsación vuelve a guardar y anota un evento más en la bitácora,
     * que es la "basura" que aparecía en la base.
     *
     * El aviso que sale al guardar no lo puede cerrar una prueba, así que aquí
     * se cierra en cuanto aparece y se comprueba que la ventana ya no está.
     */
    @Test
    void guardarUnaHerramientaCierraLaVentanaDeUnaVez() throws Exception {
        HerramientaFormController controlador = new HerramientaFormController();

        HerramientaService herramientas = Mockito.mock(HerramientaService.class);
        Mockito.when(herramientas.alta(Mockito.any(Herramienta.class), Mockito.any()))
                .thenAnswer(llamada -> {
                    Herramienta guardada = llamada.getArgument(0);
                    guardada.setId(42L);
                    guardada.setCodigoBarras("HER-de-prueba");
                    return guardada;
                });
        inyectar(controlador, "herramientaService", herramientas);
        inyectar(controlador, "codigoBarrasService", Mockito.mock(CodigoBarrasService.class));

        URL recurso = getClass().getResource("/view/herramienta-form.fxml");
        assertNotNull(recurso, "falta el recurso /view/herramienta-form.fxml");
        FXMLLoader cargador = new FXMLLoader(recurso);
        cargador.setControllerFactory(t -> controlador);
        Parent raiz = cargador.load();
        ((TextField) leer(controlador, "txtNombre")).setText("Martillo");

        AtomicReference<Throwable> fallo = new AtomicReference<>();
        AtomicBoolean seguirCerrando = new AtomicBoolean(true);
        CountDownLatch terminado = new CountDownLatch(1);

        Platform.runLater(() -> {
            Stage escenario = new Stage();
            try {
                escenario.setScene(new Scene(raiz));
                escenario.show();

                // El aviso lo cierra este hilo: la prueba no tiene a nadie que
                // le dé al botón, pero sí tiene que comprobar qué queda después.
                Thread cerrador = new Thread(() -> {
                    while (seguirCerrando.get()) {
                        // Se recorre una copia: al ocultar el aviso la lista de
                        // ventanas cambia y, si se recorre la original, el
                        // forEach acaba en un IndexOutOfBoundsException.
                        Platform.runLater(() -> new ArrayList<>(Window.getWindows()).forEach(ventana -> {
                            // Un aviso es una ventana cuya raíz es un DialogPane; la
                            // ventana del formulario no lo es y no se toca.
                            if (ventana.isShowing()
                                    && ventana.getScene() != null
                                    && ventana.getScene().getRoot() instanceof DialogPane) {
                                ventana.hide();
                            }
                        }));
                        try {
                            Thread.sleep(40);
                        } catch (InterruptedException parado) {
                            return;
                        }
                    }
                });
                cerrador.setDaemon(true);
                cerrador.start();

                Method guardar = HerramientaFormController.class.getDeclaredMethod("guardar");
                guardar.setAccessible(true);
                guardar.invoke(controlador);

                assertFalse(escenario.isShowing(),
                        "tras guardar la ventana debe cerrarse sola: si se queda "
                                + "abierta se le puede volver a dar a Guardar y cada "
                                + "pulsación deja un evento más en la bitácora");
            } catch (Throwable t) {
                fallo.set(t);
            } finally {
                seguirCerrando.set(false);
                escenario.close();
                terminado.countDown();
            }
        });

        assertTrue(terminado.await(30, TimeUnit.SECONDS),
                "JavaFX no terminó de guardar la herramienta");
        if (fallo.get() != null) {
            throw new AssertionError("falló dentro del hilo de JavaFX", fallo.get());
        }
    }

    /**
     * La otra mitad del arreglo: el scope prototype hace que cada ventana pida
     * su propio controlador. Sin él, Spring devuelve el mismo para toda la
     * sesión y cualquier cosa que viva en la instancia —la ficha de una
     * herramienta, los renglones de una entrada— viaja de ventana en ventana.
     */
    @Test
    void losFormulariosQueSeAbrenVariasVecesNoSonSingleton() {
        for (Class<?> formulario : List.of(ProductoFormController.class,
                HerramientaFormController.class,
                EntradaFormController.class, SalidaFormController.class)) {
            Scope scope = formulario.getAnnotation(Scope.class);
            assertNotNull(scope, formulario.getSimpleName()
                    + " se abre varias veces por sesión: tiene que declarar @Scope");
            assertEquals("prototype", scope.value(),
                    formulario.getSimpleName() + " debe ser prototype, no singleton");
        }
    }

    /**
     * Un préstamo pasado de fecha no puede quedarse callado: la columna
     * Situación debe decir hace cuánto venció, la herramienta debe contar
     * como vencida y su fila debe pintarse en rojo.
     */
    @Test
    void unaHerramientaPasadaDeFechaSeMarcaEnLaVentana() throws Exception {
        URL recurso = getClass().getResource("/view/herramientas.fxml");
        assertNotNull(recurso, "falta el recurso /view/herramientas.fxml");
        FXMLLoader cargador = new FXMLLoader(recurso);
        cargador.load();
        HerramientasController controlador = cargador.getController();

        Herramienta herramienta = new Herramienta();
        herramienta.setId(7L);
        herramienta.setNombre("Taladro");

        PrestamoHerramienta prestamo = new PrestamoHerramienta();
        prestamo.setHerramienta(herramienta);
        prestamo.setFolio("PRE-1");
        prestamo.setPersonaRecibe("Juan");
        prestamo.setFechaEntrega(LocalDateTime.now().minusDays(10));
        prestamo.setFechaEstimadaDevolucion(LocalDate.now().minusDays(3));

        Map<Long, PrestamoHerramienta> abiertos = new HashMap<>();
        abiertos.put(herramienta.getId(), prestamo);
        inyectar(controlador, "prestamosAbiertos", abiertos);

        String situacion = (String) invocar(controlador, "situacion", Herramienta.class, herramienta);
        assertTrue(situacion.contains("vencida hace 3 días"),
                "la columna Situación debe decir cuánto lleva vencida, decía: " + situacion);

        assertEquals(" · vencida hace 3 días",
                invocar(controlador, "retraso", PrestamoHerramienta.class, prestamo),
                "el aviso de retraso lleva los días exactos");

        assertTrue((Boolean) invocar(controlador, "estaVencida", Herramienta.class, herramienta),
                "una herramienta con la fecha ya pasada está vencida");

        // Estilo de la fila: en rojo, salvo mientras esté seleccionada.
        Method estilo = HerramientasController.class.getDeclaredMethod(
                "estiloDeFila", boolean.class, boolean.class);
        estilo.setAccessible(true);
        assertTrue(((String) estilo.invoke(null, true, false)).contains("#FDECEA"),
                "la fila de una vencida se pinta en rojo");
        assertEquals("", estilo.invoke(null, true, true),
                "sobre la selección el texto rojo no se lee: se deja el color normal");
        assertEquals("", estilo.invoke(null, false, false),
                "una herramienta al corriente no se pinta de nada");

        // Quien no debe nada no tiene que leer ningún aviso.
        prestamo.setFechaEstimadaDevolucion(LocalDate.now());
        assertEquals("", invocar(controlador, "retraso", PrestamoHerramienta.class, prestamo),
                "con la fecha de hoy todavía no hay aviso");
        assertFalse((Boolean) invocar(controlador, "estaVencida", Herramienta.class, herramienta),
                "una herramienta a tiempo no está vencida");
    }

    /**
     * El resumen de arriba es lo primero que se ve al abrir la ventana: cuenta
     * las vencidas aparte y se pone en rojo mientras las haya.
     */
    @Test
    void elResumenCuentaLasVencidasYLasPintaEnRojo() throws Exception {
        URL recurso = getClass().getResource("/view/herramientas.fxml");
        assertNotNull(recurso, "falta el recurso /view/herramientas.fxml");
        FXMLLoader cargador = new FXMLLoader(recurso);
        cargador.load();
        HerramientasController controlador = cargador.getController();

        Herramienta herramienta = new Herramienta();
        herramienta.setId(11L);
        herramienta.setNombre("Makita");

        PrestamoHerramienta prestamo = new PrestamoHerramienta();
        prestamo.setHerramienta(herramienta);
        prestamo.setPersonaRecibe("Juan");
        prestamo.setFechaEntrega(LocalDateTime.now().minusDays(20));
        prestamo.setFechaEstimadaDevolucion(LocalDate.now().minusDays(12));

        HerramientaService servicios = Mockito.mock(HerramientaService.class);
        Mockito.when(servicios.buscar(Mockito.any())).thenReturn(List.of(herramienta));
        Mockito.when(servicios.prestamosActivos()).thenReturn(List.of(prestamo));
        inyectar(controlador, "herramientaService", servicios);

        controlador.cargar(null);

        Label resumen = (Label) leer(controlador, "lblResumen");
        assertTrue(resumen.getText().contains("1 vencida(s)"),
                "el resumen debe contar las vencidas, decía: " + resumen.getText());
        assertTrue(resumen.getStyle().contains("#dc3545"),
                "el resumen se pone en rojo con vencidas, estilo: " + resumen.getStyle());

        // Devuelta a tiempo, deja de contarla y vuelve a su color.
        prestamo.setFechaEstimadaDevolucion(LocalDate.now());
        controlador.cargar(null);
        assertFalse(resumen.getText().contains("vencida(s)"),
                "sin vencidas el resumen no debe hablar de vencidas");
        assertTrue(resumen.getStyle().contains("#6c757d"),
                "sin vencidas el resumen vuelve al gris normal");
    }

    private static Usuario usuario(Usuario.Rol rol) {
        Usuario u = new Usuario();
        u.setNombre("De Prueba");
        u.setRol(rol);
        return u;
    }

    /** Llama a un método privado del controlador y devuelve lo que devuelve. */
    private static Object invocar(Object objeto, String nombre, Class<?> tipo,
                                  Object... argumentos) throws Exception {
        Method metodo = objeto.getClass().getDeclaredMethod(nombre, tipo);
        metodo.setAccessible(true);
        return metodo.invoke(objeto, argumentos);
    }

    private static void inyectar(Object objeto, String nombreCampo, Object valor) throws Exception {
        Field campo = objeto.getClass().getDeclaredField(nombreCampo);
        campo.setAccessible(true);
        campo.set(objeto, valor);
    }

    private static Object leer(Object objeto, String nombreCampo) throws Exception {
        Field campo = objeto.getClass().getDeclaredField(nombreCampo);
        campo.setAccessible(true);
        return campo.get(objeto);
    }
}
