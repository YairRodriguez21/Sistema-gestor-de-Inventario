package com.sapam.inventario.javafx.controller;

import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.service.EntradaAlmacenService;
import com.sapam.inventario.service.EntradaAlmacenService.Detalle;
import com.sapam.inventario.service.ProductoService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Formulario de entrada de almacén.
 *
 * Acepta varios productos en una sola entrada: a cada uno se le suma la
 * existencia y se le escribe su línea de kardex con el folio de la entrada.
 */
/*
 * Cada ventana su controlador: la lista de renglones vive en la instancia, y
 * si se reutilizara, la segunda entrada empezaría con los renglones de la
 * primera encima.
 */
@Controller
@Scope("prototype")
public class EntradaFormController {

    @Autowired
    private EntradaAlmacenService entradaService;

    @Autowired
    private ProductoService productoService;

    @FXML
    private TextField txtObservaciones;

    @FXML
    private ComboBox<Producto> cmbProducto;

    /**
     * Caja para el lector de una dimensión: escribe el código y manda Enter,
     * que es lo mismo que pulsar Añadir. Cada lectura suma una unidad del
     * producto escaneado, así que leer la misma etiqueta tres veces deja tres
     * unidades en un solo renglón —sin esto, contar una caja de 50 tornillos
     * exigía escribir la cantidad a mano y equivocarse era fácil.
     */
    @FXML
    private TextField txtEscanear;

    @FXML
    private TextField txtCantidad;

    @FXML
    private TableView<Linea> tablaProductos;

    @FXML
    private TableColumn<Linea, String> colProducto;

    @FXML
    private TableColumn<Linea, Number> colCantidad;

    /**
     * Corrección en sitio: −, + y Quitar.
     *
     * Sin esto, una cantidad mal tecleada obligaba a cancelar la entrada
     * entera y volver a empezar; ahora se arregla el renglón donde está.
     */
    @FXML
    private TableColumn<Linea, Void> colAcciones;

    @FXML
    private Label lblResumen;

    private final ObservableList<Linea> lineas = FXCollections.observableArrayList();
    private Usuario usuarioActual;

    /** Solo prepara la tabla: no toca servicios, para poder cargar el FXML en los tests. */
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
        actualizarResumen();
    }

    private static Button boton(String texto, String fondo) {
        Button b = new Button(texto);
        b.setStyle("-fx-background-color: " + fondo + "; -fx-background-radius: 6;"
                + " -fx-cursor: hand; -fx-font-weight: bold;");
        return b;
    }

    // ------------------------------------------------------------------
    // Corrección de renglones: −, + y Quitar
    // ------------------------------------------------------------------

    /** Resta una unidad; al llegar a 0 el renglón se va, porque 0 unidades no entra. */
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

    /** Carga los datos de los combos. Lo llama MainController al abrir la ventana. */
    public void abrir(Usuario usuario) {
        this.usuarioActual = usuario;

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

        // La ventana entra con la caja del lector lista: en cuanto la escena
        // esté montada pide el foco, y así abrir la entrada deja la ventana
        // lista para escanear sin tener que hacer clic primero.
        txtEscanear.sceneProperty().addListener((obs, antes, ahora) -> {
            if (ahora != null) {
                txtEscanear.requestFocus();
            }
        });
    }

    /**
     * Cada lectura del lector suma una unidad del producto escaneado.
     *
     * El código se busca igual que en la pantalla principal (código de barras
     * y, si no, identificador interno). Un producto dado de baja no entra:
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

    @FXML
    private void registrarEntrada() {
        if (lineas.isEmpty()) {
            aviso(Alert.AlertType.WARNING, "Agrega al menos un producto a la entrada.");
            return;
        }
        if (usuarioActual == null) {
            aviso(Alert.AlertType.ERROR, "No hay usuario autenticado.");
            return;
        }

        try {
            List<Detalle> detalles = new ArrayList<>();
            for (Linea linea : lineas) {
                detalles.add(new Detalle(linea.getProducto().getId(),
                        linea.getCantidad(), null));
            }

            var entrada = entradaService.registrar(
                    usuarioActual.getId(),
                    vacioANull(txtObservaciones.getText()),
                    detalles);

            StringBuilder mensaje = new StringBuilder();
            mensaje.append("Entrada registrada.\n\nFolio: ").append(entrada.getFolio());
            mensaje.append("\nProductos: ").append(lineas.size());
            mensaje.append("\nUnidades: ").append(unidadesTotales());
            mensaje.append("\n\nEl folio tiene su código de barras para escanear.");

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Entrada registrada");
            alert.setHeaderText(null);
            alert.setContentText(mensaje.toString());
            alert.showAndWait();

            cerrar();

        } catch (Exception e) {
            aviso(Alert.AlertType.ERROR, e.getMessage());
        }
    }

    @FXML
    private void cancelar() {
        cerrar();
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

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

    private static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private void cerrar() {
        Stage stage = (Stage) tablaProductos.getScene().getWindow();
        stage.close();
    }

    private void aviso(Alert.AlertType tipo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    /** Una fila de la tabla: producto + cantidad que entra. */
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
