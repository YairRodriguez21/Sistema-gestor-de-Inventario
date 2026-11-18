package com.sapam.inventario.javafx.controller;

import com.sapam.inventario.entity.Herramienta;
import com.sapam.inventario.entity.PrestamoHerramienta;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.javafx.JavaFxApplication;
import com.sapam.inventario.service.HerramientaService;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ventana de herramientas: se busca (o se escanea el código), se da de alta, se
 * presta y se devuelve. Editar y dar de baja están reservados a Administrador,
 * y el rol Consulta solo entra a mirar: ni alta, ni préstamo, ni devolución.
 *
 * Al prestar se abre el ticket con el folio {@code PRE-} en código de barras.
 * Ese mismo folio se puede escanear en la caja de búsqueda para iniciar la
 * devolución, además del botón manual sobre la fila.
 *
 * {@link #initialize()} no toca base de datos, para que el FXML se pueda cargar
 * fuera de Spring igual que el resto de formularios; los datos entran con
 * {@link #cargar(String)} a través de {@link #abrir(Usuario)}.
 *
 * Un préstamo pasado de fecha no se queda callado: su fila sale en rojo, la
 * columna Situación y el detalle dicen hace cuánto venció y el resumen de
 * arriba los cuenta aparte. Con eso y el reporte "Herramientas pendientes de
 * devolución" no hay manera de que una herramienta con alguien fuera de plazo
 * pase desapercibida.
 */
@Controller
public class HerramientasController {

    @Autowired
    private HerramientaService herramientaService;

    @FXML private TextField txtBuscar;
    @FXML private Label lblResumen;
    @FXML private Label lblDetalle;
    @FXML private Button btnNueva;
    @FXML private Button btnPrestar;
    @FXML private Button btnDevolver;
    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private TableView<Herramienta> tabla;

    @FXML private TableColumn<Herramienta, String> colCodigo;
    @FXML private TableColumn<Herramienta, String> colNombre;
    @FXML private TableColumn<Herramienta, String> colMarca;
    @FXML private TableColumn<Herramienta, String> colModelo;
    @FXML private TableColumn<Herramienta, String> colSerie;
    @FXML private TableColumn<Herramienta, String> colEstado;
    @FXML private TableColumn<Herramienta, String> colArea;
    @FXML private TableColumn<Herramienta, String> colSituacion;

    private Usuario usuarioActual;
    private List<Herramienta> herramientas = List.of();
    private Map<Long, PrestamoHerramienta> prestamosAbiertos = Map.of();

    /** True solo con sesión de Administrador: habilita Editar y Eliminar. */
    private boolean esAdmin;

    /** Administrador y operativo prestan y devuelven; consulta solo mira. */
    private boolean puedeDevolver;

    /** Lo que se rellena en el cuadro de préstamo. */
    private record PrestamoDatos(String quienRecibe, String area, String destino,
                                 LocalDate fechaEstimada, String observaciones) {}

    /** Lo que se rellena en el cuadro de devolución. */
    private record DevolucionDatos(LocalDate fecha, String estado, String observaciones) {}

    /** Color del resumen: el gris del FXML, y rojo en cuanto hay vencidas. */
    private static final String RESUMEN_AL_DIA = "-fx-text-fill: #6c757d;";
    private static final String RESUMEN_VENCIDO = "-fx-text-fill: #dc3545;";

    /** Folios antiguos: PRE- y trece dígitos del reloj. */
    private static final Pattern FOLIO_ANTIGUO = Pattern.compile("PRE-[0-9]{13}");

    /** Folios nuevos: PRE- y seis caracteres, el largo del ticket. */
    private static final Pattern FOLIO_NUEVO = Pattern.compile("PRE-[A-Z0-9]{6}");

    /** Cualquier folio completo, para no cerrar la puerta a formatos futuros. */
    private static final Pattern FOLIO_COMPLETO = Pattern.compile("PRE-[A-Z0-9-]{6,46}");

    /**
     * Espera breve después del último carácter antes de buscar un ticket.
     *
     * El lector que no manda Enter deja el código escrito y se queda ahí. Si
     * se busca en cada tecla, una persona escribiendo un nombre dispararía
     * búsquedas a medias; con esta pausa solo se busca cuando el texto ya
     * parece un folio completo. Si el lector sí manda Enter, {@link #buscar()}
     * cancela esta espera y no se ejecuta dos veces.
     */
    private static final Duration ESPERA_ESCANEO = Duration.millis(180);

    private final PauseTransition esperaEscaneo = new PauseTransition(ESPERA_ESCANEO);

    @FXML
    private void initialize() {
        colCodigo.setCellValueFactory(c -> texto(c.getValue().getCodigoBarras()));
        colNombre.setCellValueFactory(c -> texto(c.getValue().getNombre()));
        colMarca.setCellValueFactory(c -> texto(c.getValue().getMarca()));
        colModelo.setCellValueFactory(c -> texto(c.getValue().getModelo()));
        colSerie.setCellValueFactory(c -> texto(c.getValue().getNumeroSerie()));
        colEstado.setCellValueFactory(c -> texto(c.getValue().getEstadoFisico()));
        colArea.setCellValueFactory(c -> texto(c.getValue().getAreaResponsable()));
        colSituacion.setCellValueFactory(c -> texto(situacion(c.getValue())));

        // Sin selección no se puede prestar ni devolver nada, y sin sesión de
        // administrador no se puede editar ni dar de baja nada.
        tabla.getSelectionModel().selectedItemProperty().addListener(
                (obs, anterior, actual) -> {
                    actualizarBotones();
                    actualizarDetalle();
                });

        // La fila de una herramienta pasada de fecha se pinta en rojo: es la
        // señal de que un bien está con alguien fuera de plazo. La pintura va
        // en la fila porque así se ve de un vistazo en cualquier columna.
        tabla.setRowFactory(fila -> new TableRow<Herramienta>() {
            private void pintar() {
                setStyle(estiloDeFila(!isEmpty() && estaVencida(getItem()), isSelected()));
            }

            @Override
            protected void updateItem(Herramienta item, boolean vacia) {
                super.updateItem(item, vacia);
                pintar();
            }

            @Override
            public void updateSelected(boolean seleccionada) {
                super.updateSelected(seleccionada);
                pintar();
            }
        });

        // Algunos lectores escriben el código pero no mandan Enter. Cuando el
        // texto ya tiene forma de folio completo, se busca solo tras una pausa
        // corta; si el lector sí manda Enter, buscar() cancela esta espera.
        esperaEscaneo.setOnFinished(evento -> {
            if (herramientaService != null && folioDeTicket(txtBuscar.getText()) != null) {
                buscar();
            }
        });
        txtBuscar.textProperty().addListener((obs, anterior, actual) -> {
            esperaEscaneo.stop();
            if (herramientaService != null && folioDeTicket(actual) != null) {
                esperaEscaneo.playFromStart();
            }
        });

        actualizarBotones();
        actualizarDetalle();
    }

    /**
     * Entra con la sesión abierta y pinta la lista completa.
     *
     * Tres niveles, y ninguno igual al de la pantalla principal:
     *
     * <ul>
     *   <li><b>Administrador</b>: el catálogo entero —alta, edición y
     *       baja— y además presta y devuelve.</li>
     *   <li><b>Usuario operativo</b>: <b>solo presta y devuelve</b>. No da de
     *       alta herramientas ni las da de baja: meter piezas al catálogo y
     *       sacarlas es cosa de quien lo administra, no de quien se las lleva
     *       a usar. Con ver la ficha, prestarla y devolverla le alcanza.</li>
     *   <li><b>Consulta</b>: no escribe nada. Mira la lista, que es lo que
     *       hace antes de pedir un reporte.</li>
     * </ul>
     *
     * Lo que no le toca desaparece en vez de quedarse apagado: un botón que
     * ese rol nunca va a poder usar solo estorba y hace dudar de si está roto.
     */
    public void abrir(Usuario usuario) {
        this.usuarioActual = usuario;
        this.esAdmin = usuario != null && usuario.getRol() == Usuario.Rol.admin;
        boolean esOperativo = usuario != null && usuario.getRol() == Usuario.Rol.usuario;
        this.puedeDevolver = esAdmin || esOperativo;

        ocultarBoton(btnNueva, esAdmin);
        ocultarBoton(btnEditar, esAdmin);
        ocultarBoton(btnEliminar, esAdmin);
        ocultarBoton(btnPrestar, puedeDevolver);
        ocultarBoton(btnDevolver, puedeDevolver);

        cargar(null);

        // La caja del lector pide el foco en cuanto la escena está montada:
        // abrir Herramientas y escanear sin tener que hacer clic primero.
        txtBuscar.sceneProperty().addListener((obs, antes, ahora) -> {
            if (ahora != null) {
                txtBuscar.requestFocus();
            }
        });
    }

    /** Oculta el botón y lo saca del flujo: no debe quedar ni hueco ni apagado. */
    private static void ocultarBoton(Button boton, boolean visible) {
        boton.setVisible(visible);
        boton.setManaged(visible);
    }

    public void cargar(String texto) {
        // Fuera de Spring no hay servicio: la ventana se abre igual y solo no
        // pinta filas, igual que la pantalla principal, para poder comprobarla
        // desde FxmlTest sin base de datos.
        if (herramientaService == null) {
            return;
        }
        herramientas = herramientaService.buscar(texto);

        prestamosAbiertos = new HashMap<>();
        for (PrestamoHerramienta prestamo : herramientaService.prestamosActivos()) {
            prestamosAbiertos.put(prestamo.getHerramienta().getId(), prestamo);
        }

        tabla.setItems(FXCollections.observableArrayList(herramientas));
        long prestadas = herramientas.stream()
                .filter(h -> prestamosAbiertos.containsKey(h.getId())).count();
        long vencidas = herramientas.stream().filter(this::estaVencida).count();

        lblResumen.setText(herramientas.size() + " herramienta(s)  ·  "
                + prestadas + " prestada(s)  ·  "
                + (herramientas.size() - prestadas) + " disponible(s)"
                + (vencidas == 0 ? "" : "  ·  " + vencidas + " vencida(s)"));
        // El resumen se pone en rojo en cuanto hay algo pasado de fecha: es lo
        // primero que tiene que ver quien abre la ventana.
        lblResumen.setStyle(vencidas == 0 ? RESUMEN_AL_DIA : RESUMEN_VENCIDO);

        actualizarBotones();
        actualizarDetalle();
    }

    private String situacion(Herramienta herramienta) {
        PrestamoHerramienta prestamo = prestamosAbiertos.get(herramienta.getId());
        if (prestamo == null) {
            return "Disponible";
        }
        return (prestamo.getFolio() == null ? "" : prestamo.getFolio() + " · ")
                + "Con " + prestamo.getPersonaRecibe()
                + " · devolver antes del " + prestamo.getFechaEstimadaDevolucion()
                + retraso(prestamo);
    }

    /**
     * Aviso de retraso: "· vencida hace N día(s)" cuando la fecha estimada ya
     * pasó, y cadena vacía mientras el préstamo esté dentro de plazo —quien no
     * debe nada no tiene que leer ningún aviso—. La misma frase se usa en la
     * columna Situación y en el detalle, para que un vencido se lea igual en
     * los dos sitios.
     */
    private static String retraso(PrestamoHerramienta prestamo) {
        if (prestamo == null || prestamo.getFechaEstimadaDevolucion() == null) {
            return "";
        }
        long dias = ChronoUnit.DAYS.between(
                prestamo.getFechaEstimadaDevolucion(), LocalDate.now());
        if (dias <= 0) {
            return "";
        }
        return " · vencida hace " + dias + " día" + (dias == 1 ? "" : "s");
    }

    /** Una herramienta está vencida si su préstamo abierto pasó de fecha. */
    private boolean estaVencida(Herramienta herramienta) {
        return herramienta != null
                && !retraso(prestamosAbiertos.get(herramienta.getId())).isEmpty();
    }

    /**
     * Estilo de la fila: en rojo si está vencida, y normal en cuanto se marca
     * la fila, porque sobre el azul de la selección el texto rojo no se lee.
     */
    private static String estiloDeFila(boolean vencida, boolean seleccionada) {
        if (!vencida || seleccionada) {
            return "";
        }
        return "-fx-background-color: #FDECEA; -fx-text-fill: #B02A37;";
    }

    private void actualizarBotones() {
        Herramienta herramienta = tabla.getSelectionModel().getSelectedItem();
        boolean seleccionada = herramienta != null;
        boolean prestada = seleccionada && prestamosAbiertos.containsKey(herramienta.getId());

        btnPrestar.setDisable(!seleccionada || prestada);
        btnDevolver.setDisable(!seleccionada || !prestada);
        // Editar y dar de baja solo para administrador.
        btnEditar.setDisable(!esAdmin || !seleccionada);
        // Una herramienta con el préstamo abierto está con alguien: no se de baja.
        btnEliminar.setDisable(!esAdmin || !seleccionada || prestada);
    }

    private void actualizarDetalle() {
        Herramienta herramienta = tabla.getSelectionModel().getSelectedItem();
        if (herramienta == null) {
            lblDetalle.setText("Selecciona una herramienta para ver su situación");
            return;
        }

        PrestamoHerramienta prestamo = prestamosAbiertos.get(herramienta.getId());
        String registrada = herramienta.getUsuarioRegistro() == null
                ? "" : " · Registró: " + herramienta.getUsuarioRegistro().getNombreCompleto();

        if (prestamo == null) {
            lblDetalle.setText(herramienta.getNombre()
                    + ": sin préstamo, disponible en "
                    + (herramienta.getAreaResponsable() == null
                            ? "(sin área asignada)" : herramienta.getAreaResponsable())
                    + registrada);
            return;
        }

        lblDetalle.setText(herramienta.getNombre()
                + " · folio " + (prestamo.getFolio() == null ? "(sin folio)" : prestamo.getFolio())
                + ": con " + prestamo.getPersonaRecibe()
                + " desde el " + prestamo.getFechaEntrega().toLocalDate()
                + (prestamo.getDestino() == null ? "" : " · para dónde: " + prestamo.getDestino())
                + " · devolver antes del " + prestamo.getFechaEstimadaDevolucion()
                + retraso(prestamo)
                + " · la entregó " + (prestamo.getPersonaEntrega() == null
                        ? "(desconocido)" : prestamo.getPersonaEntrega())
                + " · estado al salir: " + (prestamo.getEstadoEntrega() == null
                        ? "(sin registro)" : prestamo.getEstadoEntrega())
                + registrada);
    }

    @FXML
    private void buscar() {
        esperaEscaneo.stop();
        String texto = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim();

        // El lector puede mandar el guion como apóstrofo si está configurado
        // con otra distribución de teclado. En herramientas todos los códigos
        // llevan guion (HER-... y PRE-...), así que se corrige antes de buscar.
        String textoNormalizado = normalizarCodigo(texto);
        String textoBusqueda = pareceCodigoDeHerramienta(textoNormalizado)
                ? textoNormalizado : texto;

        // El folio de un préstamo empieza por PRE-. Si lo que llega es un
        // ticket, no se busca una herramienta: se abre directamente la
        // devolución de ese préstamo. Así el papel que se entregó al prestar
        // sirve para devolver sin localizar antes la fila.
        //
        // folioDeTicket() tolera que el lector agregue un prefijo de
        // simbología, espacios o el salto de línea final, y devuelve el folio
        // en mayúsculas aunque el lector lo escriba en minúsculas.
        String folioTicket = folioDeTicket(texto);
        boolean pareceTicket = folioTicket != null && herramientaService != null;
        if (pareceTicket) {
            Optional<PrestamoHerramienta> porTicket =
                    herramientaService.prestamoPorFolio(folioTicket);
            if (porTicket.isPresent()) {
                devolverPorTicket(porTicket.get());
                return;
            }
        }

        cargar(textoBusqueda);

        // Escanear deja la herramienta elegida en la tabla: con la fila
        // seleccionada se puede prestar, devolver o editar sin volver a
        // buscarla con el ratón. Solo cuando el texto es el código completo
        // de una sola ficha —lo que escribe el lector—, para que una búsqueda
        // por nombre no deje seleccionada la primera coincidencia a la
        // deriva.
        if (!textoBusqueda.isEmpty() && herramientas.size() == 1
                && textoBusqueda.equalsIgnoreCase(String.valueOf(herramientas.get(0).getCodigoBarras()))) {
            tabla.getSelectionModel().select(herramientas.get(0));
            tabla.scrollTo(herramientas.get(0));
            // La caja se vacía y vuelve a recibir el foco: el siguiente
            // código se escanea sin tocar el teclado ni el ratón.
            txtBuscar.clear();
        } else if (pareceTicket && herramientas.isEmpty()) {
            aviso("Ticket no encontrado",
                    "No hay ningún préstamo con el folio " + folioTicket + ".");
            txtBuscar.clear();
        }
        txtBuscar.requestFocus();
    }

    /**
     * Extrae el folio de un ticket de lo que escribió el lector.
     *
     * Un lector puede mandar el texto pelado, con un prefijo de simbología
     * ({@code ]C1...}), con espacios o con el salto de línea del Enter. Se
     * limpia todo eso y se busca el folio conocido dentro del texto; así el
     * escaneo no depende de que el equipo esté configurado de una sola forma.
     * Devuelve {@code null} cuando el texto no contiene un folio de ticket.
     */
    private static String folioDeTicket(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = normalizarCodigo(texto)
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);

        // Los antiguos se comprueban primero: su serie de trece dígitos
        // también encajaría en el patrón corto, pero solo en los primeros seis.
        Matcher antiguo = FOLIO_ANTIGUO.matcher(limpio);
        if (antiguo.find()) {
            return antiguo.group();
        }
        Matcher nuevo = FOLIO_NUEVO.matcher(limpio);
        if (nuevo.find()) {
            return nuevo.group();
        }
        Matcher completo = FOLIO_COMPLETO.matcher(limpio);
        return completo.matches() ? completo.group() : null;
    }

    /**
     * Corrige las variantes que puede escribir un lector según su distribución
     * de teclado. El caso típico es un lector configurado en inglés sobre
     * Windows en español: el guion de {@code PRE-...} o {@code HER-...} llega
     * como apóstrofo ({@code PRE'...}).
     */
    private static String normalizarCodigo(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();

        // Algunos lectores anteponen el identificador de simbología, por
        // ejemplo ]C1. No forma parte del código que se guardó.
        if (limpio.length() > 3
                && limpio.charAt(0) == ']' && limpio.charAt(1) == 'C') {
            limpio = limpio.substring(3);
        }

        return limpio
                .replace('\'', '-')
                .replace('´', '-')
                .replace('`', '-')
                .replace('’', '-')
                .replace('‘', '-')
                .replace('′', '-')
                .replace('–', '-')
                .replace('—', '-')
                .replace('−', '-')
                .replace('_', '-');
    }

    private static boolean pareceCodigoDeHerramienta(String texto) {
        if (texto == null) {
            return false;
        }
        String mayusculas = texto.toUpperCase(Locale.ROOT);
        return mayusculas.startsWith("HER-") || mayusculas.startsWith("PRE-");
    }

    @FXML
    private void actualizar() {
        cargar(txtBuscar.getText());
    }

    /**
     * Alta reservada a Administrador, igual que la edición y la baja: el
     * operativo presta y devuelve, pero no decide qué entra ni qué sale del
     * catálogo.
     *
     * El botón ya está oculto para él y aun así se vuelve a comprobar aquí,
     * porque el permiso no vive en un botón.
     */
    @FXML
    private void nueva() {
        if (!esAdmin) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/herramienta-form.fxml"));
            loader.setControllerFactory(JavaFxApplication.getContext()::getBean);
            Parent raiz = loader.load();

            ((HerramientaFormController) loader.getController()).abrir(usuarioActual);

            Stage stage = new Stage();
            stage.setTitle("Nueva herramienta");
            stage.setScene(new Scene(raiz));
            stage.initOwner((Stage) tabla.getScene().getWindow());
            stage.showAndWait();

            cargar(txtBuscar.getText());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Edición reservada a Administrador: abre la ficha ya rellenada con su código. */
    @FXML
    private void editar() {
        Herramienta herramienta = seleccionada();
        if (herramienta == null || !esAdmin) {
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/herramienta-form.fxml"));
            loader.setControllerFactory(JavaFxApplication.getContext()::getBean);
            Parent raiz = loader.load();

            HerramientaFormController controlador = loader.getController();
            controlador.abrir(usuarioActual);
            controlador.setHerramienta(herramienta);

            Stage stage = new Stage();
            stage.setTitle("Editar herramienta");
            stage.setScene(new Scene(raiz));
            stage.initOwner((Stage) tabla.getScene().getWindow());
            stage.showAndWait();

            cargar(txtBuscar.getText());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Baja reservada a Administrador. Es una baja lógica: la fila no se borra
     * para conservar la trazabilidad de sus préstamos y de la bitácora.
     */
    @FXML
    private void eliminar() {
        Herramienta herramienta = seleccionada();
        if (herramienta == null || !esAdmin) {
            return;
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Dar de baja la herramienta");
        confirmacion.setHeaderText("Dar de baja: " + herramienta.getNombre());
        confirmacion.setContentText("El registro no se borra: queda en estado BAJAS para "
                + "conservar la trazabilidad de sus préstamos y de la bitácora.\n\n"
                + "¿Continuar?");
        Optional<ButtonType> respuesta = confirmacion.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() != ButtonType.OK) {
            return;
        }

        try {
            herramientaService.baja(herramienta.getId(), usuarioActual);
            cargar(txtBuscar.getText());
        } catch (IllegalArgumentException e) {
            aviso("No se pudo dar de baja", e.getMessage());
        }
    }

    @FXML
    private void prestar() {
        Herramienta herramienta = seleccionada();
        if (herramienta == null) {
            return;
        }

        Optional<PrestamoDatos> datos = pedirPrestamo(herramienta);
        if (datos.isEmpty()) {
            return;
        }

        try {
            PrestamoHerramienta prestamo = herramientaService.prestar(
                    herramienta.getId(),
                    datos.get().quienRecibe(),
                    datos.get().area(),
                    datos.get().destino(),
                    datos.get().fechaEstimada(),
                    datos.get().observaciones(),
                    usuarioActual);
            cargar(txtBuscar.getText());
            mostrarTicket(prestamo);
        } catch (IllegalArgumentException e) {
            aviso("No se pudo prestar la herramienta", e.getMessage());
        }
    }

    /**
     * Abre el ticket del préstamo recién registrado.
     *
     * El préstamo ya quedó guardado: si el FXML del ticket fallara, se avisa
     * pero no se pierde la operación. El controlador del ticket recibe la
     * entidad completa; la herramienta es una relación EAGER y ya viene
     * cargada desde el servicio.
     */
    private void mostrarTicket(PrestamoHerramienta prestamo) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/ticket-prestamo.fxml"));
            Parent raiz = loader.load();

            TicketPrestamoController controlador = loader.getController();
            controlador.cargar(prestamo);

            Stage stage = new Stage();
            stage.setTitle("Ticket de préstamo " + prestamo.getFolio());
            stage.setScene(new Scene(raiz));
            if (tabla.getScene() != null && tabla.getScene().getWindow() != null) {
                stage.initOwner(tabla.getScene().getWindow());
            }
            stage.showAndWait();
        } catch (IOException | RuntimeException e) {
            aviso("Préstamo registrado",
                    "El préstamo " + prestamo.getFolio() + " quedó registrado, "
                            + "pero no se pudo abrir el ticket.\n" + e.getMessage());
        }
    }

    @FXML
    private void devolver() {
        Herramienta herramienta = seleccionada();
        if (herramienta == null) {
            return;
        }

        PrestamoHerramienta prestamo = herramientaService.prestamoActivo(herramienta.getId())
                .orElse(null);
        if (prestamo == null) {
            aviso("Devolver herramienta", "Esta herramienta no está prestada.");
            return;
        }

        Optional<DevolucionDatos> datos = pedirDevolucion(herramienta, prestamo);
        if (datos.isEmpty()) {
            return;
        }

        try {
            herramientaService.devolver(prestamo.getId(), datos.get().fecha(),
                    datos.get().estado(), datos.get().observaciones(), usuarioActual);
            cargar(txtBuscar.getText());
        } catch (IllegalArgumentException e) {
            aviso("No se pudo registrar la devolución", e.getMessage());
        }
    }

    /**
     * Devolución iniciada escaneando el ticket de préstamo.
     *
     * Se filtra la tabla por la herramienta para que la fila quede a la vista
     * y se abre el mismo cuadro de devolución del botón manual: el escaneo
     * ahorra buscar el bien, pero no cambia las reglas de qué se registra.
     */
    private void devolverPorTicket(PrestamoHerramienta prestamo) {
        if (!puedeDevolver) {
            aviso("Devolver por ticket",
                    "Tu rol no puede registrar devoluciones.");
            limpiarBuscador();
            return;
        }

        if (prestamo.getFechaDevolucion() != null) {
            aviso("Préstamo ya devuelto",
                    "El ticket " + prestamo.getFolio() + " ya fue devuelto el "
                            + prestamo.getFechaDevolucion() + ".");
            limpiarBuscador();
            return;
        }

        Herramienta herramienta = prestamo.getHerramienta();
        if (herramienta == null) {
            aviso("Ticket sin herramienta",
                    "El préstamo " + prestamo.getFolio() + " no tiene una herramienta asociada.");
            limpiarBuscador();
            return;
        }

        // La tabla se reduce a esa herramienta y se marca su fila; el cuadro
        // de devolución se abre igual que si se hubiera pulsado el botón.
        cargar(herramienta.getCodigoBarras());
        seleccionarPorId(herramienta.getId());

        Optional<DevolucionDatos> datos = pedirDevolucion(herramienta, prestamo);
        if (datos.isEmpty()) {
            limpiarBuscador();
            cargar(null);
            return;
        }

        try {
            herramientaService.devolver(prestamo.getId(), datos.get().fecha(),
                    datos.get().estado(), datos.get().observaciones(), usuarioActual);
            limpiarBuscador();
            cargar(null);
            Alert confirmacion = new Alert(Alert.AlertType.INFORMATION);
            confirmacion.setTitle("Devolución registrada");
            confirmacion.setHeaderText("Devolución registrada");
            confirmacion.setContentText("La herramienta " + herramienta.getNombre()
                    + " volvió al almacén.");
            confirmacion.showAndWait();
        } catch (IllegalArgumentException e) {
            aviso("No se pudo registrar la devolución", e.getMessage());
            limpiarBuscador();
            cargar(null);
        }
    }

    private void seleccionarPorId(Long id) {
        if (id == null) {
            return;
        }
        for (int i = 0; i < herramientas.size(); i++) {
            if (id.equals(herramientas.get(i).getId())) {
                tabla.getSelectionModel().select(i);
                tabla.scrollTo(i);
                return;
            }
        }
    }

    private void limpiarBuscador() {
        txtBuscar.clear();
        txtBuscar.requestFocus();
    }

    @FXML
    private void cerrar() {
        Stage stage = (Stage) tabla.getScene().getWindow();
        stage.close();
    }

    private Herramienta seleccionada() {
        Herramienta herramienta = tabla.getSelectionModel().getSelectedItem();
        if (herramienta == null) {
            aviso("Selecciona una herramienta",
                    "Marca primero una fila de la tabla para trabajar sobre ella.");
        }
        return herramienta;
    }

    /**
     * Cuadro de préstamo. Quién se la lleva se escribe como texto libre, igual
     * que quien recibe una salida: esa persona casi nunca tiene cuenta en el
     * sistema. El botón Aceptar no se activa hasta que se anota un nombre.
     */
    private Optional<PrestamoDatos> pedirPrestamo(Herramienta herramienta) {
        TextField txtQuienRecibe = new TextField();
        txtQuienRecibe.setPrefWidth(340);
        txtQuienRecibe.setPromptText("Nombre de quien se la lleva");

        Label ayuda = new Label("Escribe el nombre tal como debe aparecer en el ticket, en el "
                + "reporte y en la bitácora. No necesita tener cuenta en el sistema. Quien "
                + "entrega es la cuenta con la que estás trabajando, y esa es la que responde "
                + "del bien.");
        ayuda.setWrapText(true);
        ayuda.setMaxWidth(340);
        ayuda.setStyle("-fx-text-fill: #6c757d;");

        TextField txtArea = new TextField();
        txtArea.setPrefWidth(340);
        txtArea.setText(herramienta.getAreaResponsable());

        TextField txtDestino = new TextField();
        txtDestino.setPrefWidth(340);
        txtDestino.setPromptText("Obra, laboratorio, planta o lugar donde se usará");

        DatePicker dpEstimada = new DatePicker(LocalDate.now().plusDays(7));

        TextArea taObs = new TextArea();
        taObs.setPrefRowCount(3);
        taObs.setPrefWidth(340);

        GridPane cuadricula = new GridPane();
        cuadricula.setHgap(10);
        cuadricula.setVgap(10);
        cuadricula.add(new Label("Quién se la lleva *:"), 0, 0);
        cuadricula.add(txtQuienRecibe, 1, 0);
        cuadricula.add(ayuda, 1, 1);
        cuadricula.add(new Label("Área responsable:"), 0, 2);
        cuadricula.add(txtArea, 1, 2);
        cuadricula.add(new Label("Para dónde *:"), 0, 3);
        cuadricula.add(txtDestino, 1, 3);
        cuadricula.add(new Label("Devolver antes del:"), 0, 4);
        cuadricula.add(dpEstimada, 1, 4);
        cuadricula.add(new Label("Observaciones:"), 0, 5);
        cuadricula.add(taObs, 1, 5);

        Dialog<PrestamoDatos> dialogo = new Dialog<>();
        dialogo.setTitle("Prestar herramienta");
        dialogo.setHeaderText(herramienta.getNombre() + "   ·   " + herramienta.getCodigoBarras());
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogo.getDialogPane().setContent(cuadricula);
        if (tabla.getScene() != null && tabla.getScene().getWindow() != null) {
            dialogo.initOwner(tabla.getScene().getWindow());
        }

        Node botonAceptar = dialogo.getDialogPane().lookupButton(ButtonType.OK);
        // El ticket tiene que llevar quién se la lleva y para dónde: sin esos
        // dos datos el papel no sirve para localizar la herramienta.
        botonAceptar.disableProperty().bind(Bindings.createBooleanBinding(
                () -> txtQuienRecibe.getText().isBlank() || txtDestino.getText().isBlank(),
                txtQuienRecibe.textProperty(), txtDestino.textProperty()));

        dialogo.setResultConverter(boton -> boton == ButtonType.OK
                ? new PrestamoDatos(txtQuienRecibe.getText(), txtArea.getText(),
                        txtDestino.getText(), dpEstimada.getValue(), taObs.getText())
                : null);
        return dialogo.showAndWait();
    }

    private Optional<DevolucionDatos> pedirDevolucion(Herramienta herramienta,
                                                      PrestamoHerramienta prestamo) {
        DatePicker dpFecha = new DatePicker(LocalDate.now());
        dpFecha.setPrefWidth(340);

        ComboBox<String> cbEstado = new ComboBox<>(
                FXCollections.observableArrayList(HerramientaService.ESTADOS_FISICOS));
        cbEstado.setPrefWidth(340);
        cbEstado.setValue(herramienta.getEstadoFisico());

        TextArea taObs = new TextArea();
        taObs.setPrefRowCount(3);
        taObs.setPrefWidth(340);

        GridPane cuadricula = new GridPane();
        cuadricula.setHgap(10);
        cuadricula.setVgap(10);
        cuadricula.add(new Label("Fecha de devolución:"), 0, 0);
        cuadricula.add(dpFecha, 1, 0);
        cuadricula.add(new Label("Estado al volver:"), 0, 1);
        cuadricula.add(cbEstado, 1, 1);
        cuadricula.add(new Label("Observaciones:"), 0, 2);
        cuadricula.add(taObs, 1, 2);
        // Estado con el que salió, para poder compararlo con el de arriba:
        // es el que quedó congelado en el préstamo al entregarla.
        cuadricula.add(new Label("Estado al salir:"), 0, 3);
        Label estadoSalida = new Label(prestamo.getEstadoEntrega() == null
                ? "(sin registro en el préstamo)" : prestamo.getEstadoEntrega());
        estadoSalida.setStyle("-fx-text-fill: #6c757d;");
        cuadricula.add(estadoSalida, 1, 3);

        Dialog<DevolucionDatos> dialogo = new Dialog<>();
        dialogo.setTitle("Devolver herramienta");
        dialogo.setHeaderText(herramienta.getNombre()
                + (prestamo.getFolio() == null ? "" : "   ·   " + prestamo.getFolio())
                + "   ·   lo tiene " + prestamo.getPersonaRecibe());
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogo.getDialogPane().setContent(cuadricula);
        if (tabla.getScene() != null && tabla.getScene().getWindow() != null) {
            dialogo.initOwner(tabla.getScene().getWindow());
        }

        dialogo.setResultConverter(boton -> boton == ButtonType.OK
                ? new DevolucionDatos(dpFecha.getValue(), cbEstado.getValue(), taObs.getText())
                : null);
        return dialogo.showAndWait();
    }

    private void aviso(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.WARNING, mensaje);
        alerta.setTitle(titulo);
        alerta.setHeaderText(titulo);
        alerta.showAndWait();
    }

    private static ObservableValue<String> texto(String valor) {
        return new SimpleStringProperty(valor == null ? "" : valor);
    }
}
