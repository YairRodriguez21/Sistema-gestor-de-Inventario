package com.sapam.inventario.javafx.controller;

import com.sapam.inventario.entity.Kardex;
import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.service.KardexService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

/**
 * Historial de kardex de un producto concreto.
 *
 * Es el kardex automático del producto que se está mirando: cada línea la
 * escribieron los servicios de entrada y de salida, nadie la rellena a mano,
 * y por eso la pantalla no ofrece edición de ningún tipo.
 */
@Controller
public class KardexProductoController {

    @Autowired
    private KardexService kardexService;

    @FXML private Label lblProducto;
    @FXML private Label lblStock;
    @FXML private TableView<Kardex> tablaKardex;

    @FXML private TableColumn<Kardex, String> colFecha;
    @FXML private TableColumn<Kardex, String> colHora;
    @FXML private TableColumn<Kardex, String> colTipo;
    @FXML private TableColumn<Kardex, String> colCantidad;
    @FXML private TableColumn<Kardex, String> colAntes;
    @FXML private TableColumn<Kardex, String> colDespues;
    @FXML private TableColumn<Kardex, String> colFolio;
    @FXML private TableColumn<Kardex, String> colResponsable;
    @FXML private TableColumn<Kardex, String> colObservaciones;

    @FXML
    private void initialize() {
        // Solo fábricas de celda: nada de base de datos, para que el FXML se
        // pueda cargar fuera de Spring igual que el resto de formularios.
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFecha() == null ? "" : c.getValue().getFecha().toString()));
        colHora.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getHora() == null ? ""
                        : String.format("%02d:%02d", c.getValue().getHora().getHour(),
                                          c.getValue().getHora().getMinute())));
        colTipo.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTipoMovimiento() == null ? "" : c.getValue().getTipoMovimiento()));
        colCantidad.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCantidadMovida() == null ? ""
                        : String.valueOf(c.getValue().getCantidadMovida())));
        colAntes.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getExistenciaAnterior() == null ? ""
                        : String.valueOf(c.getValue().getExistenciaAnterior())));
        colDespues.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getExistenciaPosterior() == null ? ""
                        : String.valueOf(c.getValue().getExistenciaPosterior())));
        colFolio.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFolio() == null ? "" : c.getValue().getFolio()));
        colResponsable.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getUsuarioResponsable() == null ? ""
                        : c.getValue().getUsuarioResponsable().getNombre()));
        colObservaciones.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getObservaciones() == null ? "" : c.getValue().getObservaciones()));
    }

    public void setProducto(Producto producto) {
        lblProducto.setText(producto.getNombre() + "  ·  " + producto.getCodigoBarras());
        lblStock.setText("Existencia actual: " + producto.getExistenciaActual()
                + (producto.getExistenciaMinima() == null ? ""
                        : "   ·   mínimo: " + producto.getExistenciaMinima()));

        tablaKardex.setItems(FXCollections.observableArrayList(
                kardexService.historialPorProducto(producto.getId())));
    }

    @FXML
    private void cerrar() {
        Stage stage = (Stage) tablaKardex.getScene().getWindow();
        stage.close();
    }
}
