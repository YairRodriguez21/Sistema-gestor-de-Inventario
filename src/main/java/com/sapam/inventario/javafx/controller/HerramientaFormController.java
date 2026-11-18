package com.sapam.inventario.javafx.controller;

import com.sapam.inventario.entity.Herramienta;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.javafx.ImpresionEtiqueta;
import com.sapam.inventario.service.HerramientaService;
import com.sapam.inventario.service.CodigoBarrasService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.ByteArrayInputStream;
import java.util.Optional;

/**
 * Alta y edición de una herramienta.
 *
 * El código de la etiqueta no aparece en el formulario: lo genera el sistema al guardar,
 * igual que el código de barras de los productos, y aquí solo se muestra para
 * poder imprimir la etiqueta.
 *
 * Cada ventana su controlador y su ficha: el scope prototype más el reinicio
 * de {@link #initialize()} impiden que la ficha de la ventana anterior viaje a
 * la siguiente, cosa que hacía que "Nueva herramienta" guardara encima del
 * registro que ya se había dado de alta.
 */
@Controller
@Scope("prototype")
public class HerramientaFormController {

    @Autowired
    private HerramientaService herramientaService;

    @Autowired
    private CodigoBarrasService codigoBarrasService;

    @FXML private TextField txtNombre;
    @FXML private TextField txtMarca;
    @FXML private TextField txtModelo;
    @FXML private TextField txtSerie;
    @FXML private TextField txtArea;
    @FXML private ComboBox<String> cbEstado;
    @FXML private TextArea taObservaciones;
    @FXML private Label lblError;

    @FXML private ImageView imgCodigo;
    @FXML private Label lblCodigoBarras;
    @FXML private Button btnImprimirCodigo;

    /** La ficha en curso: nueva (sin id) o una existente al editar. */
    private Herramienta herramienta = new Herramienta();

    private Usuario usuarioActual;

    @FXML
    private void initialize() {
        // Solo catálogo en memoria: nada que ver con la base de datos.
        cbEstado.getItems().setAll(HerramientaService.ESTADOS_FISICOS);
        cbEstado.setValue("NUEVO");
        // La ficha arranca vacía en cada ventana. Si no, y el controlador se
        // reutiliza, la ventana de alta heredaría la herramienta ya guardada y
        // en lugar de dar de alta otra la sobrescribiría.
        this.herramienta = new Herramienta();
        mostrarCodigo();
    }

    public void abrir(Usuario usuario) {
        this.usuarioActual = usuario;
    }

    /** Rellena el formulario con una herramienta existente (modo edición). */
    public void setHerramienta(Herramienta herramienta) {
        this.herramienta = herramienta;
        txtNombre.setText(herramienta.getNombre());
        txtMarca.setText(herramienta.getMarca());
        txtModelo.setText(herramienta.getModelo());
        txtSerie.setText(herramienta.getNumeroSerie());
        cbEstado.setValue(herramienta.getEstadoFisico());
        txtArea.setText(herramienta.getAreaResponsable());
        taObservaciones.setText(herramienta.getObservaciones());
        mostrarCodigo();
    }

    @FXML
    private void guardar() {
        lblError.setStyle("-fx-text-fill: #dc3545;");
        lblError.setText("");

        herramienta.setNombre(txtNombre.getText());
        herramienta.setMarca(vacio(txtMarca.getText()));
        herramienta.setModelo(vacio(txtModelo.getText()));
        herramienta.setNumeroSerie(vacio(txtSerie.getText()));
        herramienta.setEstadoFisico(cbEstado.getValue());
        herramienta.setAreaResponsable(vacio(txtArea.getText()));
        herramienta.setObservaciones(vacio(taObservaciones.getText()));

        try {
            boolean esNueva = herramienta.getId() == null;
            herramienta = esNueva
                    ? herramientaService.alta(herramienta, usuarioActual)
                    : herramientaService.modificar(herramienta, usuarioActual);

            mostrarCodigo();
            lblError.setStyle("-fx-text-fill: #28A745;");
            lblError.setText("Guardada: " + herramienta.getNombre()
                    + "  ·  " + herramienta.getCodigoEscaneable());

            cerrarConEtiqueta(esNueva
                    ? "Herramienta guardada.\n\n"
                            + "Código de barras: " + herramienta.getCodigoEscaneable() + "\n\n"
                            + "Imprime la etiqueta ahora, o cierra y hazlo más tarde "
                            + "desde Editar."
                    : "Herramienta actualizada.\n\n"
                            + "Su código no cambia: sigue siendo "
                            + herramienta.getCodigoEscaneable() + "\n\n"
                            + "Imprime la etiqueta ahora, o cierra y hazlo más tarde "
                            + "desde Editar.");
        } catch (IllegalArgumentException e) {
            lblError.setStyle("-fx-text-fill: #dc3545;");
            lblError.setText(e.getMessage());
        }
    }

    /**
     * Aviso de guardado con dos salidas y ninguna de ellas dejando la ventana
     * abierta.
     *
     * Estar ahí esperando a que alguien le diera a Cancelar era lo que
     * permitía pulsar Guardar una y otra vez: cada pulsación volvía a guardar
     * y a anotar un evento más en la bitácora. Aquí se imprime la etiqueta y
     * se cierra, o se cierra directamente; para reimprimir, Editar.
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

    /** Dibuja el código de barras; sin código, deja el aviso de "al guardar". */
    private void mostrarCodigo() {
        String contenido = herramienta != null ? herramienta.getCodigoEscaneable() : null;
        if (contenido == null || contenido.isBlank()) {
            imgCodigo.setImage(null);
            lblCodigoBarras.setText("(se genera al guardar)");
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

    /** Imprime la etiqueta de 50 x 30 mm con las barras, el nombre y la serie. */
    @FXML
    private void imprimirEtiqueta() {
        if (imgCodigo.getImage() == null) {
            return;
        }
        ImpresionEtiqueta.imprimir(
                codigoBarrasService.generar(
                        herramienta.getCodigoEscaneable(), CodigoBarrasService.TAMANO_DEFECTO),
                herramienta.getNombre(),
                herramienta.getNumeroSerie(),
                herramienta.getCodigoEscaneable());
    }

    @FXML
    private void cancelar() {
        cerrar();
    }

    private void cerrar() {
        Stage stage = (Stage) txtNombre.getScene().getWindow();
        stage.close();
    }

    private void aviso(Alert.AlertType tipo, String mensaje) {
        Alert alerta = new Alert(tipo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }

    /** Un campo sin escribir se guarda como NULL y no como cadena vacía. */
    private static String vacio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
