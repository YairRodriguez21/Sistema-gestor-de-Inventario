package com.sapam.inventario.javafx.controller;

import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.service.UsuarioService;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Ventana de cuentas: alta, edición, baja y reactivación. Es de Administrador
 * y solo se llega desde el botón que la ventana principal le oculta a los
 * demás roles; aun así {@link #abrir(Usuario)} vuelve a comprobarlo, porque el
 * permiso no debe vivir en un botón.
 *
 * {@link #initialize()} no toca base de datos, como el resto de ventanas, para
 * que el FXML se pueda cargar fuera de Spring en las pruebas: la lista entra
 * con {@link #cargar(String)} desde {@link #abrir(Usuario)}.
 *
 * Aquí no se borra nada. La baja es lógica —la cuenta deja de entrar— porque
 * un usuario que ya registró entradas, salidas o eventos de bitácora es la
 * mitad de la trazabilidad del sistema: borrarlo dejaría esos movimientos sin
 * responsable. Es la misma razón por la que el repositorio no tiene {@code
 * delete}. La reactivación existe para deshacer una baja sin tener que crear
 * la cuenta de nuevo, que sería una fila distinta y perdería su historial.
 */
@Controller
public class UsuariosController {

    @Autowired
    private UsuarioService usuarioService;

    @FXML private TextField txtBuscar;
    @FXML private Label lblResumen;
    @FXML private Label lblDetalle;
    @FXML private Button btnNueva;
    @FXML private Button btnEditar;
    @FXML private Button btnBaja;
    @FXML private Button btnReactivar;
    @FXML private TableView<Usuario> tabla;

    @FXML private TableColumn<Usuario, String> colNombre;
    @FXML private TableColumn<Usuario, String> colEmail;
    @FXML private TableColumn<Usuario, String> colRol;
    @FXML private TableColumn<Usuario, String> colEstado;
    @FXML private TableColumn<Usuario, String> colRegistro;

    /** Quien abrió la ventana: es el responsable que queda en la bitácora. */
    private Usuario usuarioActual;

    private List<Usuario> cuentas = List.of();

    private static final DateTimeFormatter ALTA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Lo que se anota en el cuadro de alta y de edición. */
    private record CuentaDatos(String nombre, String email, String password,
                               Usuario.Rol rol, boolean activo) {}

    @FXML
    private void initialize() {
        colNombre.setCellValueFactory(c -> texto(c.getValue().getNombre()));
        colEmail.setCellValueFactory(c -> texto(c.getValue().getEmail()));
        colRol.setCellValueFactory(c -> texto(c.getValue().getRol() == null
                ? null : c.getValue().getRol().descripcion()));
        colEstado.setCellValueFactory(c -> texto(estaActiva(c.getValue())
                ? "Activa" : "Inactiva"));
        colRegistro.setCellValueFactory(c -> texto(c.getValue().getFechaRegistro() == null
                ? "" : c.getValue().getFechaRegistro().format(ALTA)));

        // Sin selección no se puede editar ni dar de baja nada.
        tabla.getSelectionModel().selectedItemProperty().addListener(
                (obs, anterior, actual) -> {
                    actualizarBotones();
                    actualizarDetalle();
                });

        // Una cuenta inactiva se apaga en la tabla: es lo que distingue de un
        // vistazo una cuenta dada de baja de una con la que se puede entrar.
        tabla.setRowFactory(fila -> new TableRow<Usuario>() {
            private void pintar() {
                setStyle(estiloDeFila(!isEmpty() && !estaActiva(getItem())));
            }

            @Override
            protected void updateItem(Usuario item, boolean vacia) {
                super.updateItem(item, vacia);
                pintar();
            }

            @Override
            public void updateSelected(boolean seleccionada) {
                super.updateSelected(seleccionada);
                pintar();
            }
        });

        actualizarBotones();
        actualizarDetalle();
    }

    /**
     * Entra con la sesión abierta y pinta la lista.
     *
     * @return {@code false} si quien llamó no es Administrador: en ese caso no
     *         se carga nada y no se debe mostrar la ventana.
     */
    public boolean abrir(Usuario usuario) {
        if (usuario == null || usuario.getRol() != Usuario.Rol.admin) {
            return false;
        }
        this.usuarioActual = usuario;
        cargar(null);
        return true;
    }

    /** Recarga la lista aplicando el texto de la caja de búsqueda. */
    public void cargar(String texto) {
        String filtro = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);

        cuentas = usuarioService.listarTodos().stream()
                .filter(u -> filtro.isEmpty()
                        || (u.getNombre() != null
                                && u.getNombre().toLowerCase(Locale.ROOT).contains(filtro))
                        || (u.getEmail() != null
                                && u.getEmail().toLowerCase(Locale.ROOT).contains(filtro)))
                // Las activas primero: son con las que se puede trabajar.
                .sorted((a, b) -> {
                    int porEstado = Boolean.compare(estaActiva(b), estaActiva(a));
                    if (porEstado != 0) {
                        return porEstado;
                    }
                    return nombreDe(a).compareToIgnoreCase(nombreDe(b));
                })
                .toList();

        tabla.setItems(FXCollections.observableArrayList(cuentas));
        if (!cuentas.isEmpty()) {
            tabla.getSelectionModel().select(0);
            tabla.scrollTo(0);
        }

        long activas = cuentas.stream().filter(UsuariosController::estaActiva).count();
        lblResumen.setText(cuentas.size() + (cuentas.size() == 1 ? " cuenta" : " cuentas")
                + "  ·  " + activas + " activa" + (activas == 1 ? "" : "s")
                + "  ·  " + (cuentas.size() - activas) + " de baja");

        actualizarBotones();
        actualizarDetalle();
    }

    @FXML
    private void buscar() {
        cargar(txtBuscar.getText());
        txtBuscar.requestFocus();
    }

    @FXML
    private void actualizar() {
        cargar(txtBuscar.getText());
    }

    @FXML
    private void nueva() {
        Optional<CuentaDatos> datos = pedirCuenta(null);
        datos.ifPresent(d -> guardar(new Usuario(), d));
    }

    @FXML
    private void editar() {
        Usuario cuenta = seleccionada();
        if (cuenta == null) {
            return;
        }
        pedirCuenta(cuenta).ifPresent(d -> guardar(cuenta, d));
    }

    /**
     * Edita o da de alta pasando por el servicio, que es el que valida y el
     * que escribe el evento en la bitácora. Aquí solo se traduce el rechazo a
     * un aviso legible: la validación no se duplica en la ventana.
     */
    private void guardar(Usuario cuenta, CuentaDatos datos) {
        cuenta.setNombre(datos.nombre());
        cuenta.setEmail(datos.email());
        cuenta.setPassword(datos.password());
        cuenta.setRol(datos.rol());
        cuenta.setActivo(datos.activo());

        try {
            usuarioService.guardar(cuenta, usuarioActual);
            cargar(txtBuscar.getText());
        } catch (IllegalArgumentException e) {
            aviso("No se pudo guardar la cuenta", e.getMessage());
        }
    }

    /**
     * Baja lógica: la cuenta deja de entrar y sus registros siguen en pie.
     *
     * El servicio se niega además a desactivar la cuenta con la que se está
     * trabajando, que dejaría al propio administrador fuera sin poder
     * deshacerlo.
     */
    @FXML
    private void baja() {
        Usuario cuenta = seleccionada();
        if (cuenta == null) {
            return;
        }
        if (!estaActiva(cuenta)) {
            aviso("La cuenta ya está dada de baja", cuenta.getEmail());
            return;
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION,
                "La cuenta " + cuenta.getEmail() + " dejará de poder entrar.\n\n"
                        + "No se borra nada: todo lo que ya registró se conserva a "
                        + "su nombre y podrá reactivarse desde esta misma ventana.",
                ButtonType.OK, ButtonType.CANCEL);
        confirmacion.setTitle("Dar de baja la cuenta");
        confirmacion.setHeaderText(cuenta.getNombre() + "   ·   " + cuenta.getEmail());

        Optional<ButtonType> respuesta = confirmacion.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() != ButtonType.OK) {
            return;
        }

        try {
            usuarioService.baja(cuenta.getId(), usuarioActual);
            cargar(txtBuscar.getText());
        } catch (IllegalArgumentException e) {
            aviso("No se pudo dar de baja", e.getMessage());
        }
    }

    /** Deshace una baja sin crear una cuenta nueva, que perdería el historial. */
    @FXML
    private void reactivar() {
        Usuario cuenta = seleccionada();
        if (cuenta == null) {
            return;
        }
        if (estaActiva(cuenta)) {
            aviso("La cuenta ya está activa", cuenta.getEmail());
            return;
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION,
                "La cuenta " + cuenta.getEmail() + " volverá a poder entrar con su "
                        + "rol de " + (cuenta.getRol() == null
                                ? "(sin rol)" : cuenta.getRol().descripcion()) + ".",
                ButtonType.OK, ButtonType.CANCEL);
        confirmacion.setTitle("Reactivar la cuenta");
        confirmacion.setHeaderText(cuenta.getNombre() + "   ·   " + cuenta.getEmail());

        Optional<ButtonType> respuesta = confirmacion.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() != ButtonType.OK) {
            return;
        }

        try {
            usuarioService.reactivar(cuenta.getId(), usuarioActual);
            cargar(txtBuscar.getText());
        } catch (IllegalArgumentException e) {
            aviso("No se pudo reactivar", e.getMessage());
        }
    }

    @FXML
    private void cerrar() {
        Stage stage = (Stage) tabla.getScene().getWindow();
        stage.close();
    }

    private Usuario seleccionada() {
        Usuario cuenta = tabla.getSelectionModel().getSelectedItem();
        if (cuenta == null) {
            aviso("Selecciona una cuenta",
                    "Marca primero una fila de la tabla para trabajar sobre ella.");
        }
        return cuenta;
    }

    /**
     * Cuadro de alta y de edición; con {@code existente} es edición.
     *
     * La contraseña se pide en claro como todo lo demás en este proyecto. Al
     * editar se puede dejar vacía, en cuyo caso se conserva la que ya tenía:
     * borrarla dejaría la cuenta sin forma de entrar.
     */
    private Optional<CuentaDatos> pedirCuenta(Usuario existente) {
        boolean esEdicion = existente != null;

        TextField txtNombre = new TextField(esEdicion ? existente.getNombre() : "");
        txtNombre.setPrefWidth(340);

        TextField txtEmail = new TextField(esEdicion ? existente.getEmail() : "");
        txtEmail.setPrefWidth(340);

        PasswordField pwdPassword = new PasswordField();
        pwdPassword.setPrefWidth(340);
        pwdPassword.setPromptText(esEdicion
                ? "Déjala vacía para conservar la actual" : "Contraseña con la que entrará");

        ComboBox<Usuario.Rol> cbRol = new ComboBox<>(FXCollections.observableArrayList(
                Usuario.Rol.values()));
        cbRol.setValue(esEdicion && existente.getRol() != null
                ? existente.getRol() : Usuario.Rol.usuario);

        CheckBox chkActivo = new CheckBox("La cuenta puede entrar al sistema");
        chkActivo.setSelected(!esEdicion || estaActiva(existente));

        Label ayuda = new Label(esEdicion
                ? "Si dejas la contraseña vacía se conserva la actual. Cambiar el rol "
                        + "cambia lo que esta cuenta puede ver y hacer en cuanto entre."
                : "El rol decide qué ve: Administrador ve todo, Usuario operativo mueve "
                        + "almacén y Consulta solo lee.");
        ayuda.setWrapText(true);
        ayuda.setMaxWidth(340);
        ayuda.setStyle("-fx-text-fill: #6c757d;");

        GridPane cuadricula = new GridPane();
        cuadricula.setHgap(10);
        cuadricula.setVgap(10);
        cuadricula.add(new Label("Nombre *:"), 0, 0);
        cuadricula.add(txtNombre, 1, 0);
        cuadricula.add(new Label("Correo *:"), 0, 1);
        cuadricula.add(txtEmail, 1, 1);
        cuadricula.add(new Label(esEdicion ? "Contraseña:" : "Contraseña *:"), 0, 2);
        cuadricula.add(pwdPassword, 1, 2);
        cuadricula.add(new Label("Rol *:"), 0, 3);
        cuadricula.add(cbRol, 1, 3);
        cuadricula.add(chkActivo, 1, 4);
        cuadricula.add(ayuda, 1, 5);

        Dialog<CuentaDatos> dialogo = new Dialog<>();
        dialogo.setTitle(esEdicion ? "Editar cuenta" : "Nueva cuenta");
        dialogo.setHeaderText(esEdicion
                ? existente.getNombre() + "   ·   " + existente.getEmail()
                : "Alta de una cuenta del sistema");
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogo.getDialogPane().setContent(cuadricula);

        // Aceptar no se activa hasta que nombre y correo estén escritos, y en
        // el alta hasta que además haya contraseña: el servicio lo rechazaría,
        // pero avisar cuando se escribe es mejor que avisar al darle a Aceptar.
        javafx.scene.Node aceptar = dialogo.getDialogPane().lookupButton(ButtonType.OK);
        Runnable revisar = () -> aceptar.setDisable(txtNombre.getText().trim().isEmpty()
                || txtEmail.getText().trim().isEmpty()
                || (!esEdicion && pwdPassword.getText().isEmpty()));
        txtNombre.textProperty().addListener((o, a, b) -> revisar.run());
        txtEmail.textProperty().addListener((o, a, b) -> revisar.run());
        pwdPassword.textProperty().addListener((o, a, b) -> revisar.run());
        revisar.run();

        dialogo.setResultConverter(boton -> boton == ButtonType.OK
                ? new CuentaDatos(txtNombre.getText().trim(),
                        txtEmail.getText().trim(),
                        pwdPassword.getText(),
                        cbRol.getValue(),
                        chkActivo.isSelected())
                : null);

        return dialogo.showAndWait();
    }

    private void actualizarBotones() {
        Usuario cuenta = tabla.getSelectionModel().getSelectedItem();
        boolean seleccionada = cuenta != null;
        boolean activa = seleccionada && estaActiva(cuenta);

        // Nueva cuenta siempre; editar, dar de baja y reactivar solo con fila.
        btnEditar.setDisable(!seleccionada);
        btnBaja.setDisable(!seleccionada || !activa);
        btnReactivar.setDisable(!seleccionada || activa);
    }

    private void actualizarDetalle() {
        Usuario cuenta = tabla.getSelectionModel().getSelectedItem();
        if (cuenta == null) {
            lblDetalle.setText("Selecciona una cuenta para ver su situación");
            return;
        }

        lblDetalle.setText(cuenta.getNombre()
                + "  ·  " + cuenta.getEmail()
                + "  ·  " + (cuenta.getRol() == null
                        ? "(sin rol)" : cuenta.getRol().descripcion())
                + "  ·  " + (estaActiva(cuenta)
                        ? "activa, puede entrar" : "dada de baja, no puede entrar")
                + (cuenta.getFechaRegistro() == null
                        ? "" : "  ·  alta el " + cuenta.getFechaRegistro().format(ALTA)));
    }

    private static boolean estaActiva(Usuario cuenta) {
        return cuenta != null && Boolean.TRUE.equals(cuenta.getActivo());
    }

    private static String nombreDe(Usuario cuenta) {
        return cuenta.getNombre() == null ? "" : cuenta.getNombre();
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

    /** Color de fila de una cuenta a la que ya no se puede entrar. */
    private static String estiloDeFila(boolean inactiva) {
        if (!inactiva) {
            return "";
        }
        return "-fx-background-color: #F2F3F4; -fx-text-fill: #6c757d;";
    }
}
