package com.sapam.inventario.javafx.controller;

import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.entity.SalidaAlmacen;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.SalidaAlmacenRepository;
import com.sapam.inventario.service.ProductoService;
import com.sapam.inventario.service.SalidaAlmacenService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Formulario de salida de almacén.
 *
 * Surtir una lista era lo más pesado del día: cada producto exigía abrir la
 * ventana, teclear los mismos datos de siempre y guardar, con un folio por
 * producto y ningún papel que los juntara. Ahora se arma la lista igual que en
 * una entrada —renglón por renglón o escaneando— y se guarda una sola salida
 * con todos ellos.
 *
 * La orden de trabajo la teclea quien entrega el material y no se puede
 * omitir: es lo que amarra la salida con el documento que la autoriza. Lo
 * mismo pasa con "OTRO", que por sí solo no explica nada, así que al elegirlo
 * aparece una caja para decir de qué se trata.
 *
 * Quien responde de la salida no se puede elegir: es la cuenta con la que se
 * entró. El campo aparece como etiqueta de solo lectura, así que si algo falta
 * no hay manera de señalarse a otro nombre desde el formulario.
 *
 * Al terminar se levanta el ticket, que es el papel que se le entrega a quien
 * se lleva el material: productos, quién lo solicitó, fecha y hora, quién lo
 * dio y las observaciones.
 */
/*
 * Cada ventana su controlador: los renglones y los datos tecleados viven en la
 * instancia, y si se reutilizara, la segunda salida empezaría con la anterior
 * encima.
 */
@Controller
@Scope("prototype")
public class SalidaFormController {

    @Autowired
    private SalidaAlmacenService salidaAlmacenService;

    @Autowired
    private SalidaAlmacenRepository salidaAlmacenRepository;

    @Autowired
    private ProductoService productoService;

    @FXML private TextField txtEscanear;
    @FXML private ComboBox<Producto> cmbProducto;
    @FXML private TextField txtCantidad;
    @FXML private TableView<Linea> tablaProductos;
    @FXML private TableColumn<Linea, String> colProducto;
    @FXML private TableColumn<Linea, Number> colCantidad;
    @FXML private TableColumn<Linea, Void> colAcciones;
    @FXML private Label lblResumen;

    @FXML private TextField txtOrdenTrabajo;
    @FXML private ComboBox<String> cbMotivo;
    @FXML private Label lblMotivoOtro;
    @FXML private TextField txtMotivoOtro;
    @FXML private ComboBox<String> cbSolicita;
    @FXML private TextField txtPuesto;
    @FXML private TextField txtDepartamento;
    @FXML private ComboBox<String> cbRecibe;
    @FXML private Label lblResponsable;
    @FXML private TextArea txtObservaciones;

    private final ObservableList<Linea> lineas = FXCollections.observableArrayList();
    private Usuario usuarioActual;
    private boolean catalogosCargados = false;

    /** Solo prepara la tabla y los motivos: no toca servicios, para poder cargar el FXML en los tests. */
    @FXML
    public void initialize() {
        colProducto.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().getProducto().getNombre()));
        colCantidad.setCellValueFactory(
                c -> new SimpleIntegerProperty(c.getValue().getCantidad()));
        colAcciones.setCellFactory(columna -> new javafx.scene.control.TableCell<>() {
            private final Button menos = boton("−", "#f8d7da");
            private final Button mas = boton("+", "#d4edda");
            private final Button quitar = boton("Quitar", "#f8d7da");

            {
                menos.setOnAction(e -> restarUna(getTableRow().getItem()));
                mas.setOnAction(e -> sumarUna(getTableRow().getItem()));
                quitar.setOnAction(e -> quitarLinea(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(Void nada, boolean vacia) {
                super.updateItem(nada, vacia);
                setGraphic(vacia ? null : new javafx.scene.layout.HBox(4, menos, mas, quitar));
            }
        });
        tablaProductos.setItems(lineas);
        cbMotivo.getItems().setAll(SalidaAlmacenService.MOTIVOS);
        actualizarResumen();
    }

    private static Button boton(String texto, String fondo) {
        Button b = new Button(texto);
        b.setStyle("-fx-background-color: " + fondo + "; -fx-background-radius: 6;"
                + " -fx-cursor: hand; -fx-font-weight: bold;");
        return b;
    }

    // ------------------------------------------------------------------
    // Carga de la ventana
    // ------------------------------------------------------------------

    /** Camino del botón "+ Salida" de la barra: lista vacía. */
    public void abrir(Usuario usuario) {
        setUsuarioActual(usuario);
    }

    /**
     * Carga los productos y los catálogos y deja la ventana lista para escribir.
     *
     * Lo llama MainController al abrir la ventana, sea desde la barra o desde
     * el botón de la fila de un producto.
     */
    public void setUsuarioActual(Usuario usuario) {
        this.usuarioActual = usuario;
        if (usuario != null) {
            lblResponsable.setText(usuario.getNombreCompleto() + " (" + usuario.getEmail() + ")"
                    + " — es quien registra y responde de la salida; no se puede cambiar");
        }

        List<Producto> productos = new ArrayList<>(productoService.listarActivos());
        productos.sort(Comparator.comparing(Producto::getNombre));
        cmbProducto.getItems().setAll(productos);

        cmbProducto.setConverter(new StringConverter<>() {
            @Override
            public String toString(Producto p) {
                return p == null ? "Seleccionar producto"
                        : p.getNombre() + "  (existencia " + p.getExistenciaActual() + ")";
            }

            @Override
            public Producto fromString(String texto) {
                return null;
            }
        });

        cargarCatalogos();

        // La ventana entra con la caja del lector lista: en cuanto la escena
        // esté montada pide el foco, y así dejar la salida lista para escanear
        // sin tener que hacer clic primero.
        txtEscanear.sceneProperty().addListener((obs, antes, ahora) -> {
            if (ahora != null) {
                txtEscanear.requestFocus();
            }
        });
    }

    /**
     * Camino del botón "Salida" de la fila: ese producto entra listo en la
     * lista y lo que falte se le va agregando encima.
     */
    public void setProducto(Producto producto) {
        if (producto != null) {
            sumarUnidad(producto);
        }
    }

    /**
     * Nombres ya usados en salidas anteriores.
     *
     * No hay directorio: tanto "solicita" como "recibe" son texto libre y aquí
     * solo se ofrecen los valores previos como atajo para que se escriban igual
     * y luego se pueda agrupar por nombre en los reportes.
     *
     * El responsable no aparece aquí porque no se elige: sale de la sesión.
     */
    private void cargarCatalogos() {
        if (catalogosCargados) {
            return;
        }
        catalogosCargados = true;

        cbSolicita.getItems().setAll(salidaAlmacenRepository.personasQueSolicitaron());
        cbRecibe.getItems().setAll(salidaAlmacenRepository.personasQueRecibieron());
    }

    // ------------------------------------------------------------------
    // Armar la lista de productos
    // ------------------------------------------------------------------

    /**
     * Cada lectura del lector suma una unidad del producto escaneado.
     *
     * El código se busca igual que en la pantalla principal (código de barras
     * y, si no, identificador interno). Un producto dado de baja no sale:
     * se avisa y la caja queda limpia para el siguiente intento.
     */
    @FXML
    private void agregarPorEscaneo() {
        String codigo = txtEscanear.getText();
        txtEscanear.clear();
        if (codigo == null || codigo.trim().isEmpty()) {
            return;
        }

        Optional<Producto> producto = productoService.buscarPorCodigoBarrasOInterno(codigo.trim());
        if (producto.isEmpty() || "BAJAS".equals(producto.get().getEstado())) {
            aviso(Alert.AlertType.WARNING, "No hay producto activo con el código: " + codigo.trim());
            txtEscanear.requestFocus();
            return;
        }

        sumarUnidad(producto.get());
        txtEscanear.requestFocus();
    }

    /**
     * Suma una unidad al renglón del producto, o abre uno nuevo con esa
     * unidad. Es el mismo camino que "Añadir" con cantidad 1, así que el
     * escaneo y el botón dejan filas idénticas.
     */
    private void sumarUnidad(Producto producto) {
        for (Linea linea : lineas) {
            if (linea.getProducto().getId().equals(producto.getId())) {
                linea.setCantidad(linea.getCantidad() + 1);
                tablaProductos.refresh();
                actualizarResumen();
                return;
            }
        }
        lineas.add(new Linea(producto, 1));
        actualizarResumen();
    }

    @FXML
    private void agregarProducto() {
        Producto producto = cmbProducto.getValue();
        if (producto == null) {
            aviso(Alert.AlertType.WARNING, "Selecciona un producto.");
            return;
        }

        int cantidad;
        try {
            cantidad = Integer.parseInt(txtCantidad.getText().trim());
        } catch (NumberFormatException e) {
            aviso(Alert.AlertType.ERROR, "La cantidad debe ser un número.");
            return;
        }
        if (cantidad <= 0) {
            aviso(Alert.AlertType.ERROR, "La cantidad debe ser mayor a cero.");
            return;
        }
        if (cantidad > producto.getExistenciaActual()) {
            aviso(Alert.AlertType.ERROR, "Stock insuficiente. " + producto.getNombre()
                    + " tiene " + producto.getExistenciaActual() + " en existencia.");
            return;
        }

        // Si ya estaba en la lista se acumula en vez de duplicar la fila
        for (Linea linea : lineas) {
            if (linea.getProducto().getId().equals(producto.getId())) {
                linea.setCantidad(linea.getCantidad() + cantidad);
                tablaProductos.refresh();
                actualizarResumen();
                limpiarProductoSeleccionado();
                return;
            }
        }

        lineas.add(new Linea(producto, cantidad));
        actualizarResumen();
        limpiarProductoSeleccionado();
    }

    // ------------------------------------------------------------------
    // Corrección de renglones: −, + y Quitar
    // ------------------------------------------------------------------

    /** Resta una unidad; al llegar a 0 el renglón se va, porque 0 unidades no sale. */
    private void restarUna(Linea linea) {
        if (linea == null) {
            return;
        }
        if (linea.getCantidad() <= 1) {
            quitarLinea(linea);
            return;
        }
        linea.setCantidad(linea.getCantidad() - 1);
        tablaProductos.refresh();
        actualizarResumen();
    }

    /** Suma una unidad sin pasar del límite de un entero, para no desbordar. */
    private void sumarUna(Linea linea) {
        if (linea == null || linea.getCantidad() == Integer.MAX_VALUE) {
            return;
        }
        linea.setCantidad(linea.getCantidad() + 1);
        tablaProductos.refresh();
        actualizarResumen();
    }

    /** Borra el renglón entero, cuando el producto completo estaba mal. */
    private void quitarLinea(Linea linea) {
        if (linea == null) {
            return;
        }
        lineas.remove(linea);
        actualizarResumen();
    }

    // ------------------------------------------------------------------
    // Motivo
    // ------------------------------------------------------------------

    /**
     * "OTRO" es el único motivo que no dice nada por sí solo.
     *
     * Al elegirlo aparece la caja para escribir de qué se trata; con los otros
     * cinco se vuelve a ocultar, porque repetir ahí "REPARACION" no aporta.
     */
    @FXML
    private void motivoCambiado() {
        boolean esOtro = SalidaAlmacenService.MOTIVO_OTRO.equals(cbMotivo.getValue());
        lblMotivoOtro.setVisible(esOtro);
        lblMotivoOtro.setManaged(esOtro);
        txtMotivoOtro.setVisible(esOtro);
        txtMotivoOtro.setManaged(esOtro);
        if (!esOtro) {
            txtMotivoOtro.clear();
        }
    }

    // ------------------------------------------------------------------
    // Guardar
    // ------------------------------------------------------------------

    @FXML
    private void guardarSalida() {
        try {
            if (lineas.isEmpty()) {
                throw new Exception("Agrega al menos un producto a la salida.");
            }
            if (usuarioActual == null) {
                throw new Exception("Error: Usuario no autenticado");
            }

            String ordenTrabajo = txtOrdenTrabajo.getText().trim();
            if (ordenTrabajo.isEmpty()) {
                throw new Exception("Debe indicar el número de orden de trabajo");
            }

            String motivo = cbMotivo.getValue();
            if (motivo == null || motivo.isBlank()) {
                throw new Exception("Debe seleccionar el motivo de la salida");
            }

            String motivoOtro = null;
            if (SalidaAlmacenService.MOTIVO_OTRO.equals(motivo)) {
                motivoOtro = txtMotivoOtro.getText().trim();
                if (motivoOtro.isEmpty()) {
                    throw new Exception("El motivo es OTRO: escriba en qué consiste la salida");
                }
            }

            String solicita = textoDe(cbSolicita);
            if (solicita.isEmpty()) {
                throw new Exception("Debe indicar quién solicita la salida");
            }

            String departamento = txtDepartamento.getText().trim();
            if (departamento.isEmpty()) {
                throw new Exception("Debe indicar el área o departamento que solicita");
            }

            String recibe = textoDe(cbRecibe);
            if (recibe.isEmpty()) {
                throw new Exception("Debe indicar quién recibe el material");
            }

            List<SalidaAlmacenService.Detalle> detalles = new ArrayList<>();
            for (Linea linea : lineas) {
                detalles.add(new SalidaAlmacenService.Detalle(
                        linea.getProducto().getId(), linea.getCantidad()));
            }

            var salida = salidaAlmacenService.registrar(
                    usuarioActual.getId(),
                    ordenTrabajo,
                    solicita,
                    txtPuesto.getText(),
                    departamento,
                    recibe,
                    motivo,
                    motivoOtro,
                    txtObservaciones.getText(),
                    detalles);

            Stage escenario = (Stage) tablaProductos.getScene().getWindow();
            escenario.close();

            mostrarTicket(salida);

        } catch (Exception e) {
            aviso(Alert.AlertType.ERROR, e.getMessage());
        }
    }

    /**
     * Levanta el ticket recién impreso.
     *
     * Los productos se pasan desde la lista que todavía vive en este
     * controlador: en la salida guardada su relación con el producto es
     * perezosa y leerla aquí, fuera de la transacción, petaría.
     *
     * Se cierra antes la ventana de la salida para que en pantalla solo quede
     * el papel, igual que cuando se entrega en mano.
     */
    private void mostrarTicket(SalidaAlmacen salida) {
        try {
            List<TicketSalidaController.ProductoDelTicket> productos = new ArrayList<>();
            for (Linea linea : lineas) {
                productos.add(new TicketSalidaController.ProductoDelTicket(
                        linea.getProducto().getNombre(), linea.getCantidad()));
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ticket-salida.fxml"));
            Parent root = loader.load();

            TicketSalidaController controlador = loader.getController();
            controlador.cargar(salida, productos);

            Stage stage = new Stage();
            stage.setTitle("Ticket de salida " + salida.getFolio());
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            // El ticket no puede ser motivo de perder la salida: ya quedó
            // guardada con sus cuatro trazas, así que se avisa y ya.
            aviso(Alert.AlertType.WARNING,
                    "La salida " + salida.getFolio() + " quedó registrada, "
                            + "pero no se pudo abrir el ticket.\n" + e.getMessage());
        }
    }

    /**
     * Lee el texto de un ComboBox editable: mientras el foco siga en el campo,
     * {@code getValue()} devuelve el valor anterior, así que hay que mirar el editor.
     */
    private String textoDe(ComboBox<String> combo) {
        if (combo.isEditable() && combo.getEditor() != null) {
            String escrito = combo.getEditor().getText();
            if (escrito != null && !escrito.isBlank()) {
                return escrito.trim();
            }
        }
        return combo.getValue() == null ? "" : combo.getValue().trim();
    }

    @FXML
    private void cancelar() {
        cerrar();
    }

    private void cerrar() {
        Stage stage = (Stage) tablaProductos.getScene().getWindow();
        stage.close();
    }

    private void actualizarResumen() {
        lblResumen.setText(lineas.size() + " producto(s), " + unidadesTotales() + " unidad(es)");
    }

    private int unidadesTotales() {
        int total = 0;
        for (Linea linea : lineas) {
            total += linea.getCantidad();
        }
        return total;
    }

    private void limpiarProductoSeleccionado() {
        cmbProducto.setValue(null);
        txtCantidad.clear();
    }

    private void aviso(Alert.AlertType tipo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    /** Una fila de la tabla: producto + cantidad que sale. */
    public static class Linea {
        private final Producto producto;
        private int cantidad;

        public Linea(Producto producto, int cantidad) {
            this.producto = producto;
            this.cantidad = cantidad;
        }

        public Producto getProducto() {
            return producto;
        }

        public int getCantidad() {
            return cantidad;
        }

        public void setCantidad(int cantidad) {
            this.cantidad = cantidad;
        }
    }
}
