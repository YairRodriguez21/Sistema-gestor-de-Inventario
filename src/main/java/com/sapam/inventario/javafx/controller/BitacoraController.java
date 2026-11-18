package com.sapam.inventario.javafx.controller;

import com.sapam.inventario.entity.AuditoriaSistema;
import com.sapam.inventario.service.BitacoraService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bitácora del sistema: una página de eventos por vez, con filtros.
 *
 * Es solo consulta. No hay botón de borrar ni de editar porque la bitácora es
 * ineliminable: la pantalla tampoco lo pone.
 *
 * <b>La ventana no carga la bitácora entera.</b> Trae de 200 en 200 y deja
 * avanzar con Anterior/Siguiente: el corte lo hace la base de datos, no la
 * aplicación, así que da igual cuántos eventos se acumulen. Con esto se va
 * atrás en el tiempo sin que cada apertura tenga que ordenar toda la tabla.
 *
 * Los filtros también se resuelven allá abajo y no sobre lo cargado: si se
 * filtraran los 200 visibles, filtrar en la página 3 no daría lo mismo que
 * filtrar en la 1, y no habría manera de encontrar algo viejo.
 *
 * {@link #initialize()} no toca la base de datos, para que el FXML pueda
 * cargarse fuera de Spring igual que los demás formularios; los datos entran
 * con {@link #cargar()}, que llama quien abre la ventana.
 */
@Controller
public class BitacoraController {

    @Autowired
    private BitacoraService bitacoraService;

    @FXML private Label lblResumen;
    @FXML private Label lblPagina;
    @FXML private ComboBox<String> cbAccion;
    @FXML private ComboBox<String> cbUsuario;
    @FXML private Button btnAnterior;
    @FXML private Button btnSiguiente;
    @FXML private TableView<AuditoriaSistema> tablaEventos;

    @FXML private TableColumn<AuditoriaSistema, String> colFecha;
    @FXML private TableColumn<AuditoriaSistema, String> colHora;
    @FXML private TableColumn<AuditoriaSistema, String> colUsuario;
    @FXML private TableColumn<AuditoriaSistema, String> colAccion;
    @FXML private TableColumn<AuditoriaSistema, String> colModulo;
    @FXML private TableColumn<AuditoriaSistema, String> colDetalle;
    @FXML private TableColumn<AuditoriaSistema, String> colObservaciones;
    @FXML private TableColumn<AuditoriaSistema, String> colCodigo;

    private static final String TODAS = "Todas las acciones";
    private static final String TODOS = "Todos los usuarios";

    /** Página que se está mirando; 0 es la más reciente. */
    private int paginaActual = 0;

    /** Qué había en los selectores la última vez, para no perder la página
     *  cuando se rellenan de nuevo con lo mismo. */
    private String accionVisible;
    private String usuarioVisible;

    @FXML
    private void initialize() {
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFecha() == null ? "" : c.getValue().getFecha().toString()));
        colHora.setCellValueFactory(c -> new SimpleStringProperty(horaDe(c.getValue().getHora())));
        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(usuarioDe(c.getValue())));
        colAccion.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getAccion() == null ? "" : c.getValue().getAccion()));
        colModulo.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getModuloAfectado() == null ? "" : c.getValue().getModuloAfectado()));
        colDetalle.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getInformacionModificada() == null ? ""
                        : c.getValue().getInformacionModificada()));
        colObservaciones.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getObservaciones() == null ? "" : c.getValue().getObservaciones()));
        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCodigoVerificacion() == null ? "" : c.getValue().getCodigoVerificacion()));
    }

    /**
     * Rellena los dos selectores con lo que existe en la bitácora y pinta la
     * página en la que se está.
     *
     * Las opciones salen de la historia entera y no de lo que se está mirando:
     * si salieran de la página visible, cambiar de página cambiaría lo que se
     * puede filtrar.
     *
     * No regresa a la página 1, para que "Actualizar" refresque lo que se está
     * viendo sin arrancar de nuevo a quien va buscando algo viejo.
     */
    public void cargar() {
        String accionAnterior = cbAccion.getValue();
        String usuarioAnterior = cbUsuario.getValue();

        List<String> opcionesAccion = new ArrayList<>();
        opcionesAccion.add(TODAS);
        bitacoraService.acciones().forEach(opcionesAccion::add);
        cbAccion.getItems().setAll(opcionesAccion);
        cbAccion.setValue(opcionesAccion.contains(accionAnterior) ? accionAnterior : TODAS);

        List<String> opcionesUsuario = new ArrayList<>();
        opcionesUsuario.add(TODOS);
        bitacoraService.usuarios().forEach(opcionesUsuario::add);
        cbUsuario.getItems().setAll(opcionesUsuario);
        cbUsuario.setValue(opcionesUsuario.contains(usuarioAnterior) ? usuarioAnterior : TODOS);

        pintar();
    }

    /**
     * Cambia de filtro. Empieza de la página 1: filtrar es una búsqueda nueva,
     * y seguir donde estaba no tendría sentido con otro criterio encima.
     */
    @FXML
    private void aplicarFiltro() {
        boolean cambio = !Objects.equals(accionVisible, cbAccion.getValue())
                || !Objects.equals(usuarioVisible, cbUsuario.getValue());
        accionVisible = cbAccion.getValue();
        usuarioVisible = cbUsuario.getValue();

        if (cambio) {
            paginaActual = 0;
        }
        pintar();
    }

    @FXML
    private void paginaAnterior() {
        if (paginaActual > 0) {
            paginaActual--;
            pintar();
        }
    }

    @FXML
    private void paginaSiguiente() {
        paginaActual++;
        pintar();
    }

    /**
     * Pide la página al servicio y la pinta, con su rótulo y los botones en el
     * estado que les toca: los extremos se apagan para que no se pida una
     * página que no existe.
     */
    private void pintar() {
        BitacoraService.Pagina pagina = bitacoraService.eventos(
                accionFiltrada(), usuarioFiltrado(), paginaActual);

        paginaActual = pagina.pagina();
        tablaEventos.setItems(FXCollections.observableArrayList(pagina.eventos()));

        long total = pagina.total();
        lblResumen.setText(total + (total == 1 ? " evento" : " eventos")
                + " con este filtro  ·  los más recientes primero");
        lblPagina.setText("Página " + (pagina.pagina() + 1) + " de " + pagina.paginas());

        btnAnterior.setDisable(pagina.pagina() <= 0);
        btnSiguiente.setDisable(pagina.pagina() >= pagina.paginas() - 1);
    }

    /** El valor del selector de acción, o {@code null} si no se filtra. */
    private String accionFiltrada() {
        String valor = cbAccion.getValue();
        return valor == null || TODAS.equals(valor) ? null : valor;
    }

    /** El valor del selector de usuario, o {@code null} si no se filtra. */
    private String usuarioFiltrado() {
        String valor = cbUsuario.getValue();
        return valor == null || TODOS.equals(valor) ? null : valor;
    }

    @FXML
    private void actualizar() {
        cargar();
    }

    @FXML
    private void cerrar() {
        Stage stage = (Stage) tablaEventos.getScene().getWindow();
        stage.close();
    }

    /** Nombre de quien hizo la acción; el de sistema si la hizo el arranque. */
    private static String usuarioDe(AuditoriaSistema evento) {
        return evento.getUsuario() == null
                ? BitacoraService.USUARIO_SISTEMA : evento.getUsuario().getNombre();
    }

    private static String horaDe(java.time.LocalTime hora) {
        return hora == null ? "" : String.format("%02d:%02d", hora.getHour(), hora.getMinute());
    }
}
