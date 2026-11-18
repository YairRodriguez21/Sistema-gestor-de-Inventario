package com.sapam.inventario.javafx.controller;

import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.javafx.ImpresionEtiqueta;
import com.sapam.inventario.service.ProductoService;
import com.sapam.inventario.service.CodigoBarrasService;
import javafx.fxml.FXML;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * Alta y edición de un producto.
 *
 * El código de barras lo asigna el sistema al guardar: aquí solo se lee y se
 * imprime. La etiqueta lleva ese mismo código en barras, que es lo que puede
 * leer el lector de una dimensión.
 *
 * Cada ventana su controlador y su ficha: el scope prototype más el reinicio
 * de {@link #initialize()} impiden que la ficha de la ventana anterior viaje a
 * la siguiente, cosa que haría que "Nuevo producto" guardara encima del que ya
 * se había dado de alta.
 */
@Controller
@Scope("prototype")
public class ProductoFormController {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private CodigoBarrasService codigoBarrasService;

    /** Solo lectura: el código lo asigna el sistema, aquí solo se muestra. */
    @FXML
    private Label lblCodigo;

    @FXML
    private TextField txtNombre;

    @FXML
    private TextField txtDescripcion;

    /**
     * Sección a la que pertenece el producto: las cinco de
     * {@code ProductoService.SECCIONES}, sin opción vacía que se pueda guardar
     * por descuido. El desplegable es lo único que decide qué botón de la
     * pantalla principal va a filtrarlo.
     */
    @FXML
    private ComboBox<String> cbSeccion;

    @FXML
    private TextField txtStock;

    @FXML
    private TextField txtMinimo;

    @FXML
    private ImageView imgCodigo;

    @FXML
    private Label lblCodigoBarras;

    @FXML
    private Button btnImprimirCodigo;

    private Producto producto;

    @FXML
    public void initialize() {
        this.producto = new Producto();
        cbSeccion.setItems(FXCollections.observableArrayList(ProductoService.SECCIONES));
        cbSeccion.setValue(null);
        mostrarCodigo();
        dibujarCodigo();
    }

    public void setProducto(Producto producto) {
        this.producto = producto;
        mostrarCodigo();
        dibujarCodigo();
        txtNombre.setText(producto.getNombre());
        txtDescripcion.setText(producto.getDescripcion());
        // Un producto de antes de las secciones viene sin ella: el desplegable
        // queda vacío y hay que elegir, que es lo que hace falta para que el
        // botón de su sección lo muestre.
        cbSeccion.setValue(producto.getSeccion());
        txtStock.setText(String.valueOf(producto.getExistenciaActual()));
        txtMinimo.setText(String.valueOf(producto.getExistenciaMinima()));
        dibujarCodigo();
    }

    /** Muestra el código asignado; si aún no hay, deja el hueco vacío. */
    private void mostrarCodigo() {
        String codigo = producto != null ? producto.getCodigoBarras() : null;
        lblCodigo.setText(codigo == null || codigo.isBlank() ? "" : codigo);
    }

    /**
     * Dibuja el código de barras; si el producto aún no tiene, deja el hueco
     * vacío, igual que el de arriba: un producto nuevo no avisa de nada.
     */
    private void dibujarCodigo() {
        String contenido = producto != null ? producto.getCodigoEscaneable() : null;
        if (contenido == null || contenido.isBlank()) {
            imgCodigo.setImage(null);
            lblCodigoBarras.setText("");
            btnImprimirCodigo.setDisable(true);
            return;
        }
        try {
            byte[] png = codigoBarrasService.png(contenido, CodigoBarrasService.TAMANO_DEFECTO);
            imgCodigo.setImage(new Image(new ByteArrayInputStream(png)));
            lblCodigoBarras.setText(contenido);
            btnImprimirCodigo.setDisable(false);
        } catch (Exception e) {
            imgCodigo.setImage(null);
            lblCodigoBarras.setText("No se pudo generar el código de barras: " + e.getMessage());
            btnImprimirCodigo.setDisable(true);
        }
    }

    /** Imprime la etiqueta de 50 x 30 mm con las barras, el nombre y el código. */
    @FXML
    private void imprimirEtiqueta() {
        if (imgCodigo.getImage() == null) {
            return;
        }
        ImpresionEtiqueta.imprimir(
                codigoBarrasService.generar(
                        producto.getCodigoEscaneable(), CodigoBarrasService.TAMANO_DEFECTO),
                producto.getNombre(),
                null,
                producto.getCodigoEscaneable());
    }

    private void aviso(Alert.AlertType tipo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    @FXML
    private void guardarProducto() {
        try {
            // La sección se pide antes que nada: un producto sin ella no
            // sale en ningún botón de sección y el orden del que va a
            // vivir el inventario quedaría a medias desde el primer alta.
            String seccion = cbSeccion.getValue();
            if (seccion == null || seccion.isBlank()) {
                aviso(Alert.AlertType.WARNING,
                        "Elige la sección del producto.\n\n"
                        + "Las del inventario son: " + String.join(", ", ProductoService.SECCIONES) + ".");
                return;
            }
            producto.setSeccion(seccion);

            producto.setNombre(txtNombre.getText());
            producto.setDescripcion(txtDescripcion.getText());

            producto.setExistenciaActual(Integer.parseInt(txtStock.getText()));
            producto.setExistenciaMinima(Integer.parseInt(txtMinimo.getText()));

            // codigoInterno es NOT NULL en la tabla: se genera si no existe todavía.
            // La etiqueta ya no lo lleva, va con el código de barras.
            if (producto.getCodigoInterno() == null || producto.getCodigoInterno().isBlank()) {
                producto.setCodigoInterno("INT-" + UUID.randomUUID());
            }

            boolean eraNuevo = producto.getId() == null;
            producto = productoService.guardar(producto);
            mostrarCodigo();
            dibujarCodigo();

            // La ventana se cierra sola en los dos casos: estando abierta
            // esperando a que alguien le diera a Cancelar, se podía volver a
            // pulsar Guardar y cada pulsación volvía a guardar y a anotar un
            // evento en la bitácora. Para reimprimir, Editar.
            cerrarConEtiqueta(eraNuevo
                    ? "Producto guardado.\n\n"
                            + "Código de barras: " + producto.getCodigoBarras() + "\n\n"
                            + "Imprime la etiqueta ahora, o cierra y hazlo más tarde "
                            + "desde Editar."
                    : "Producto guardado.\n\n"
                            + "Su código de barras no cambia: sigue siendo "
                            + producto.getCodigoBarras() + "\n\n"
                            + "Imprime la etiqueta ahora, o cierra y hazlo más tarde "
                            + "desde Editar.");

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("Error al guardar: " + e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Aviso de guardado con dos salidas y ninguna de ellas dejando la ventana
     * abierta: se imprime la etiqueta y se cierra, o se cierra directamente.
     */
    private void cerrarConEtiqueta(String mensaje) {
        ButtonType botonImprimir = new ButtonType("Imprimir etiqueta", ButtonData.YES);
        ButtonType botonCerrar = new ButtonType("Cerrar", ButtonData.CANCEL_CLOSE);
        Alert aviso = new Alert(Alert.AlertType.INFORMATION, mensaje, botonImprimir, botonCerrar);
        aviso.setHeaderText(null);
        Optional<ButtonType> opcion = aviso.showAndWait();
        if (opcion.isPresent() && opcion.get() == botonImprimir) {
            imprimirEtiqueta();
        }
        cerrar();
    }

    private void cerrar() {
        Stage stage = (Stage) txtNombre.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void cancelar() {
        cerrar();
    }
}
