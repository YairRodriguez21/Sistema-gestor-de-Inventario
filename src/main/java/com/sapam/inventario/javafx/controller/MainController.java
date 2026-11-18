package com.sapam.inventario.javafx.controller;

import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.javafx.JavaFxApplication;
import com.sapam.inventario.reportes.*;
import com.sapam.inventario.repository.SalidaAlmacenRepository;
import com.sapam.inventario.repository.UsuarioRepository;
import com.sapam.inventario.service.BitacoraService;
import com.sapam.inventario.service.ProductoService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.event.ActionEvent;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.util.function.Function;

@Controller
public class MainController {

    private ProductoService productoService;
    private BitacoraService bitacoraService;
    private Usuario usuarioActual;

    @FXML
    private TableView<Producto> tablaProductos;

    @FXML
    private TableColumn<Producto, String> colCodigo;

    @FXML
    private TableColumn<Producto, String> colNombre;

    @FXML
    private TableColumn<Producto, String> colSeccion;

    @FXML
    private TableColumn<Producto, Integer> colStock;

    @FXML
    private TableColumn<Producto, Integer> colMinimo;

    @FXML
    private TableColumn<Producto, Void> colAcciones;

    @FXML
    private TextField txtBuscar;

    @FXML
    private Button btnNuevoProducto;

    @FXML
    private Button btnReporte;

    @FXML
    private Button btnEntrada;

    @FXML
    private Button btnSalida;

    /**
     * Los tres botones que no son de todos los roles: la bitácora es de
     * Administrador y Consulta, la de Usuarios únicamente de Administrador.
     * Herramientas y Kardex sí los ve cualquiera que entre.
     */
    @FXML
    private Button btnBitacora;

    @FXML
    private Button btnUsuarios;

    /** Quién tiene la sesión abierta y con qué rol: se pinta en la cabecera. */
    @FXML private Label lblSesion;

    /**
     * Sección que se está mostrando, para decirlo bajo la tabla. Vacío =
     * inventario completo, que es lo que se pinta con el botón "Todo".
     */
    private String seccionActual = "";

    /** Lo mismo dicho en pantalla: sin esto no se ve que la tabla está filtrada. */
    @FXML private Label lblSeccion;

    /**
     * Ficha del producto marcado, bajo la tabla.
     *
     * Es donde se puede leer lo que no cabe en la tabla: la descripción que
     * se teclea al dar de alta y el código interno, que el sistema genera
     * solo y que no aparece en ningún otro sitio.
     */
    @FXML private Label lblFichaTitulo;

    @FXML private Label lblFicha;

    /**
     * Qué puede hacer la columna de acciones, según el rol. La calcula
     * {@link #configurarRol(String)} y lo aplica la celda al pintarse.
     */
    private boolean puedeEditarProducto;
    private boolean puedeEliminarProducto;
    private boolean puedeDarSalida;

    public void setUsuarioActual(Usuario usuario) {
        this.usuarioActual = usuario;
        // La bitácora necesita saber quién está actuando para registrar
        // quién creó/modificó/borró cada producto.
        ProductoService.setUsuarioSesion(usuario);
        // Los permisos de la interfaz dependen del rol; sin esta llamada,
        // configurarRol() nunca se ejecutaba y todos veían todo.
        if (usuario != null && usuario.getRol() != null) {
            configurarRol(usuario.getRol().name());
        }
    }

    /**
     * Pinta lo que este rol puede hacer y lo que no.
     *
     * Tres niveles, los mismos que la API:
     *
     * <ul>
     *   <li><b>Administrador</b>: todo. Le aparece además la ventana de
     *       Usuarios y la Bitácora.</li>
     *   <li><b>Usuario operativo</b>: mueve almacén —entradas y salidas—,
     *       consulta el kardex, genera reportes y opera herramientas, pero no
     *       toca el catálogo de productos ni ve la bitácora ni las cuentas.
     *       Su parte de herramientas es alta, préstamo y devolución: editar y
     *       dar de baja esa es de Administrador y lo oculta la propia ventana
     *       de herramientas.</li>
     *   <li><b>Consulta</b>: solo lectura. Ve inventario, kardex, reportes,
     *       la bitácora y la ventana de herramientas a mirar, y no escribe
     *       nada en ninguna pantalla.</li>
     * </ul>
     *
     * Un rol desconocido cae en lo más restrictivo: solo lectura de lo
     * esencial, sin ni siquiera la bitácora.
     *
     * En los botones lo que no le toca desaparece, para que un rol de solo
     * consulta vea únicamente lo que puede usar y no tenga que preguntarse si
     * una opción que no le dejan pulsar está rota.
     *
     * Esta matriz es la contraparte de las reglas de {@code SecurityConfig}:
     * ocultar el botón evita la duda, y la regla de la API evita que la misma
     * acción se haga por fuera con el token.
     */
    public void configurarRol(String rol) {
        boolean esAdmin = "admin".equalsIgnoreCase(rol);
        boolean esOperativo = "usuario".equalsIgnoreCase(rol);
        boolean esConsulta = "consulta".equalsIgnoreCase(rol);

        boolean puedeAltaProducto = esAdmin;
        boolean puedeEntrada = esAdmin || esOperativo;
        boolean puedeEditarProducto = esAdmin;
        boolean puedeEliminarProducto = esAdmin;
        boolean puedeDarSalida = esAdmin || esOperativo;
        boolean puedeReporte = esAdmin || esOperativo || esConsulta;
        boolean puedeVerBitacora = esAdmin || esConsulta;

        btnNuevoProducto.setVisible(puedeAltaProducto);
        btnEntrada.setVisible(puedeEntrada);
        btnSalida.setVisible(puedeDarSalida);
        btnReporte.setVisible(puedeReporte);
        btnBitacora.setVisible(puedeVerBitacora);
        btnUsuarios.setVisible(esAdmin);

        this.puedeEditarProducto = puedeEditarProducto;
        this.puedeEliminarProducto = puedeEliminarProducto;
        this.puedeDarSalida = puedeDarSalida;
        tablaProductos.refresh();

        lblSesion.setText(usuarioActual == null
                ? ""
                : usuarioActual.getNombre() + "  ·  " + nombreDeRol(rol));
    }

    /**
     * Nombre del rol tal como lo entiende quien lo está usando.
     *
     * Sale de {@link Usuario.Rol#descripcion()} para que la cabecera y la
     * tabla de cuentas digan lo mismo. Se recibe la cadena —que puede venir
     * como {@code admin} o como {@code ADMIN}—, por eso se recorre el enum y
     * no se hace {@code valueOf}.
     */
    private static String nombreDeRol(String rol) {
        for (Usuario.Rol candidato : Usuario.Rol.values()) {
            if (candidato.name().equalsIgnoreCase(rol)) {
                return candidato.descripcion();
            }
        }
        return "Rol sin definir";
    }

    @FXML
    public void initialize() {
        productoService = JavaFxApplication.getContext().getBean(ProductoService.class);
        bitacoraService = JavaFxApplication.getContext().getBean(BitacoraService.class);

        colCodigo.setCellValueFactory(new PropertyValueFactory<>("codigoBarras"));
        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colStock.setCellValueFactory(new PropertyValueFactory<>("existenciaActual"));
        colMinimo.setCellValueFactory(new PropertyValueFactory<>("existenciaMinima"));
        colSeccion.setCellValueFactory(new PropertyValueFactory<>("seccion"));

        configurarBotones();
        colorearFilas();
        escucharSeleccion();
        cargarProductos();

        // El lector USB escribe donde esté el foco: si la caja de búsqueda no
        // lo tiene, el código escaneado se pierde por la ventana y no pasa
        // nada en pantalla, que era justo lo que ocurría al escanear. Pide el
        // foco cuando la escena ya está montada, y callado si el toolkit no
        // corre (las pruebas cargan el FXML sin ventana).
        txtBuscar.sceneProperty().addListener((obs, antes, ahora) -> {
            if (ahora != null) {
                try {
                    javafx.application.Platform.runLater(txtBuscar::requestFocus);
                } catch (IllegalStateException sinToolkit) {
                    // Fuera de la aplicación no hay hilo de JavaFX que atienda.
                }
            }
        });
    }

    private void configurarBotones() {
        colAcciones.setCellFactory(col -> new TableCell<Producto, Void>() {
            private final Button btnEditar = new Button("Editar");
            private final Button btnSalida = new Button("Salida");
            private final Button btnEliminar = new Button("Eliminar");
            private final HBox pane = new HBox(5, btnEditar, btnSalida, btnEliminar);

            {
                btnEditar.setStyle("-fx-background-color: #FFC107; -fx-text-fill: black;");
                btnSalida.setStyle("-fx-background-color: #17A2B8; -fx-text-fill: white;");
                btnEliminar.setStyle("-fx-background-color: #DC3545; -fx-text-fill: white;");

                btnEditar.setOnAction(event -> {
                    Producto p = getTableView().getItems().get(getIndex());
                    editarProducto(p);
                });

                btnSalida.setOnAction(event -> {
                    Producto p = getTableView().getItems().get(getIndex());
                    darSalida(p);
                });

                btnEliminar.setOnAction(event -> {
                    Producto p = getTableView().getItems().get(getIndex());
                    eliminarProducto(p);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                // La celda repite por qué le toca a este rol: cada botón decide
                // solo, en lugar de ocultar la columna entera y perder también
                // la salida de almacén.
                btnEditar.setVisible(puedeEditarProducto);
                btnEliminar.setVisible(puedeEliminarProducto);
                btnSalida.setVisible(puedeDarSalida);
                boolean alguno = puedeEditarProducto || puedeEliminarProducto || puedeDarSalida;
                setGraphic(alguno ? pane : null);
            }
        });
    }

    /**
     * Pinta cada fila de la tabla general según lo que queda del producto,
     * que es lo que promete la leyenda del pie.
     *
     * La fila pintada no puede tapar la seleccionada: el kardex y la salida
     * se abren sobre la fila marcada, y si el color de la fila ganara no se
     * vería cuál es. Por eso la selección manda y solo ella se colorea.
     *
     * Lo que se decide se va en {@link #colorDeFila(Producto)} y no aquí, para
     * poder probar los umbrales sin tener que montar una tabla dentro de un
     * escenario de JavaFX.
     */
    private void colorearFilas() {
        tablaProductos.setRowFactory(tabla -> filaDeProductos());
    }

    /**
     * Una fila de la tabla general, ya con su color puesto.
     *
     * Va suelta de la ventana para que se pueda montar una tabla de mentira
     * y probarla sin arrancar la aplicación entera.
     */
    public static TableRow<Producto> filaDeProductos() {
        return new TableRow<>() {
            @Override
            protected void updateItem(Producto producto, boolean vacio) {
                super.updateItem(producto, vacio);
                pintar();
            }

            @Override
            public void updateSelected(boolean seleccionada) {
                super.updateSelected(seleccionada);
                pintar();
            }

            private void pintar() {
                String color = isSelected() ? SELECCIONADA : colorDeFila(getItem());
                setStyle(color.isEmpty() ? "" : "-fx-background-color: " + color + ";");
            }
        };
    }

    /** Color de la fila marcada, que manda sobre el de su stock. */
    private static final String SELECCIONADA = "#BBDEFB";

    /**
     * Color de la fila de un producto, o cadena vacía si no se pinta nada.
     *
     * Rojo: no queda ninguna pieza. Amarillo: sí queda, pero por debajo del
     * mínimo, que es exactamente lo que levanta el reporte de stock mínimo.
     * Verde: llega o sobra. Sin fila (las que sobran al final de la tabla)
     * no se pinta, para que no quede un bloque de color suelto.
     *
     * Un mínimo sin fijar no es un mínimo bajo: cuenta como cero, así nada
     * puede quedar por debajo y la fila no se marca amarilla sin motivo.
     */
    public static String colorDeFila(Producto producto) {
        if (producto == null) {
            return "";
        }
        int actual = producto.getExistenciaActual() == null ? 0 : producto.getExistenciaActual();
        int minimo = producto.getExistenciaMinima() == null ? 0 : producto.getExistenciaMinima();

        if (actual <= 0) {
            return "#F8D7DA";
        }
        return actual < minimo ? "#FFF3CD" : "#D4EDDA";
    }

    /**
     * La ficha de abajo sigue a la fila marcada: es lo que se mira para saber
     * de qué producto se trata sin tener que abrirlo.
     *
     * Va por escucha y no desde el clic, porque la fila se marca también al
     * escanear un código y al recargar la lista, y la ficha tendría que
     * quedarse atrás en esas dos.
     */
    private void escucharSeleccion() {
        tablaProductos.getSelectionModel().selectedItemProperty().addListener(
                (obs, antes, ahora) -> actualizarFicha(ahora));
        actualizarFicha(null);
    }

    private void actualizarFicha(Producto producto) {
        lblFichaTitulo.setText(producto == null
                ? "Selecciona un producto para ver su ficha"
                : producto.getNombre());
        lblFicha.setText(fichaDe(producto));
    }

    /**
     * Lo que se cuenta del producto marcado, aparte de lo que ya enseña la
     * tabla.
     *
     * La descripción va al final y solo si hay: es lo único que se teclea al
     * dar de alta y no aparece en ninguna columna. El código interno también,
     * porque el sistema lo genera solo y no hay otra pantalla donde leerlo.
     *
     * No se muestran marca, modelo, ubicación física ni observaciones: son
     * campos de la tabla que ningún formulario escribe, así que saldrían
     * vacíos en todo el inventario. Si algún día se les abre hueco en el
     * alta, se añaden aquí.
     */
    public static String fichaDe(Producto producto) {
        if (producto == null) {
            return "";
        }

        StringBuilder texto = new StringBuilder();
        texto.append("Sección: ").append(oSinDato(producto.getSeccion()));
        texto.append("  ·  Cód. barras: ").append(oSinDato(producto.getCodigoBarras()));
        texto.append("  ·  Cód. interno: ").append(oSinDato(producto.getCodigoInterno()));
        texto.append("\nStock: ").append(
                producto.getExistenciaActual() == null ? 0 : producto.getExistenciaActual());
        if (producto.getExistenciaMinima() != null) {
            texto.append("  ·  mínimo ").append(producto.getExistenciaMinima());
        }

        String descripcion = producto.getDescripcion();
        if (descripcion != null && !descripcion.isBlank()) {
            texto.append("\nDescripción: ").append(descripcion.trim());
        }

        return texto.toString();
    }

    /** Lo que el producto no tiene, dicho en pantalla y no dejado en blanco. */
    private static String oSinDato(String valor) {
        return valor == null || valor.isBlank() ? "(sin dato)" : valor;
    }

    private void cargarProductos() {
        if (productoService != null) {
            // Solo los de alta: los dados de baja se van de la lista en cuanto
            // se confirman, que es la única señal de que el botón hizo su tarea.
            mostrarProductos(ProductoService.deSeccion(productoService.listarActivos(), seccionActual));
        }
    }

    /**
     * Pinta la tabla y, si hay sección puesta, deja dicho cuál y cuántos
     * productos trae: sin ese aviso, ver tres filas en pantalla parece un
     * fallo de la base y no un filtro que se puso a mano.
     */
    private void mostrarProductos(List<Producto> productos) {
        tablaProductos.setItems(FXCollections.observableArrayList(productos));
        lblSeccion.setText(seccionActual.isEmpty()
                ? ""
                : "Sección: " + seccionActual + "  ·  " + productos.size() + " producto(s)");
    }

    /**
     * Botón de sección pulsado: el texto del botón es la sección, así que no
     * hacen falta cinco métodos iguales. "Todo" y cualquier otro texto que no
     * sea una sección vuelven al inventario completo.
     */
    @FXML
    private void filtrarSeccion(ActionEvent evento) {
        String texto = ((Button) evento.getSource()).getText();
        seccionActual = ProductoService.SECCIONES.contains(texto) ? texto : "";
        cargarProductos();
    }

    @FXML
    private void buscarProducto() {
        String codigo = txtBuscar.getText();
        if (codigo == null || codigo.trim().isEmpty()) {
            cargarProductos();
            return;
        }

        // Un lector USB escribe el código escaneado y manda Enter:
        // se busca primero por código de barras y después por identificador.
        Optional<Producto> producto = productoService.buscarPorCodigoBarrasOInterno(codigo.trim());
        if (producto.isPresent() && "BAJAS".equals(producto.get().getEstado())) {
            // Escanear un producto dado de baja no lo vuelve a poner en la mesa:
            // se avisa y sigue fuera del inventario.
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Producto dado de baja");
            alert.setHeaderText(null);
            alert.setContentText("El producto " + producto.get().getNombre()
                    + " está en estado BAJAS: no aparece en el inventario ni admite entradas ni salidas.\n\n"
                    + "Su historial sigue en el kardex y en la bitácora.");
            alert.showAndWait();
            txtBuscar.clear();
            cargarProductos();
            return;
        }
        if (producto.isPresent()) {
            tablaProductos.setItems(FXCollections.observableArrayList(producto.get()));
            // Escanear deja el producto elegido en la tabla: con la fila
            // seleccionada se puede pulsar Salida, Pedir kardex o Editar sin
            // volver a buscarlo con el ratón.
            tablaProductos.getSelectionModel().select(producto.get());
            tablaProductos.scrollTo(producto.get());
            // Lo que se muestra ya no es la sección, es lo escaneado: el aviso
            // de abajo deja de ser cierto y se borra. El filtro no se olvida.
            lblSeccion.setText("");
            // La caja se vacía y vuelve a recibir el foco: el siguiente código
            // se puede escanear sin tocar el teclado ni el ratón.
            txtBuscar.clear();
            txtBuscar.requestFocus();
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Búsqueda");
            alert.setHeaderText(null);
            alert.setContentText("No se encontró producto con código: " + codigo);
            alert.showAndWait();
            txtBuscar.clear();
            cargarProductos();
        }
    }

    @FXML
    private void abrirNuevoProducto() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/producto-form.fxml"));
            loader.setControllerFactory(JavaFxApplication.getContext()::getBean);
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Nuevo Producto");
            stage.setScene(new Scene(root));
            stage.showAndWait();
            cargarProductos();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Abre el formulario de entrada de almacén (varios productos por folio). */
    @FXML
    private void abrirEntrada() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/entrada-form.fxml"));
            loader.setControllerFactory(JavaFxApplication.getContext()::getBean);
            Parent root = loader.load();

            EntradaFormController controlador = loader.getController();
            controlador.abrir(usuarioActual);

            Stage stage = new Stage();
            stage.setTitle("Entrada de Almacén");
            stage.setScene(new Scene(root));
            stage.showAndWait();
            cargarProductos();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Abre el kardex del producto seleccionado en la tabla (o pide que se elija
     * uno si no hay nada seleccionado).
     *
     * Es la vía rápida para mirar la trazabilidad de un producto: se escanea su
     * código, aparece la fila y con el botón se ve todo su historial sin pasar por
     * el diálogo de reportes.
     */
    @FXML
    private void abrirKardex() {
        Optional<Producto> producto = Optional.ofNullable(
                tablaProductos.getSelectionModel().getSelectedItem());
        if (producto.isEmpty()) {
            producto = preguntarDe("Kardex de un producto", "Producto", "Producto:",
                    productosActivos(), p -> p.getNombre() + "  (" + p.getCodigoBarras() + ")");
        }
        if (producto.isEmpty()) {
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/kardex-producto.fxml"));
            loader.setControllerFactory(JavaFxApplication.getContext()::getBean);
            Parent root = loader.load();

            ((KardexProductoController) loader.getController()).setProducto(producto.get());

            Stage stage = new Stage();
            stage.setTitle("Kardex — " + producto.get().getNombre());
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Abre la bitácora del sistema: los últimos 200 eventos, filtrables por
     * acción y por usuario.
     *
     * Es solo consulta y está disponible para los tres roles (también para
     * Consulta, que es el que solo mira): nadie puede borrarla ni editarla,
     * ni siquiera desde esta pantalla.
     */
    @FXML
    private void abrirBitacora() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/bitacora.fxml"));
            loader.setControllerFactory(JavaFxApplication.getContext()::getBean);
            Parent root = loader.load();

            ((BitacoraController) loader.getController()).cargar();

            Stage stage = new Stage();
            stage.setTitle("Bitácora del sistema");
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Abre el módulo de herramientas: alta, préstamo y devolución.
     *
     * El préstamo solo se registra cuando se devuelve o se vuelve a prestar, y
     * una herramienta solo puede tener uno abierto a la vez, así que nunca hay
     * dos responsables señalados para el mismo bien.
     */
    @FXML
    private void abrirHerramientas() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/herramientas.fxml"));
            loader.setControllerFactory(JavaFxApplication.getContext()::getBean);
            Parent root = loader.load();

            ((HerramientasController) loader.getController()).abrir(usuarioActual);

            Stage stage = new Stage();
            stage.setTitle("Herramientas");
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Abre la administración de cuentas: alta, edición y baja lógica.
     *
     * Es de Administrador y se llega únicamente desde el botón que
     * {@link #configurarRol(String)} le oculta a los demás, de modo que ni si
     *quiera se le ofrece a un rol que no puede usarla. Aun así la ventana
     * vuelve a comprobarlo al abrirse: el permiso no vive en un botón.
     */
    @FXML
    private void abrirUsuarios() {
        if (usuarioActual == null || usuarioActual.getRol() != Usuario.Rol.admin) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/usuarios.fxml"));
            loader.setControllerFactory(JavaFxApplication.getContext()::getBean);
            Parent root = loader.load();

            UsuariosController controlador = loader.getController();
            if (!controlador.abrir(usuarioActual)) {
                return;
            }

            Stage stage = new Stage();
            stage.setTitle("Usuarios del sistema");
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Opciones de reporte disponibles en la aplicación de escritorio. */
    private static final String RPT_EXISTENCIAS = "Existencias actuales";
    private static final String RPT_AGOTADOS = "Productos agotados";
    private static final String RPT_STOCK_MINIMO = "Productos bajo stock mínimo";
    private static final String RPT_ENTRADAS = "Entradas de almacén por periodo";
    private static final String RPT_SALIDAS = "Salidas de almacén por periodo";
    private static final String RPT_HISTORIAL = "Historial de movimientos";
    private static final String RPT_KARDEX = "Kardex de un producto";
    private static final String RPT_TRABAJADOR = "Materiales entregados a un trabajador";
    private static final String RPT_DEPARTAMENTO = "Materiales entregados a un departamento";
    private static final String RPT_MOVIMIENTOS_USUARIO = "Movimientos de un usuario";
    private static final String RPT_HERRAMIENTAS_PRESTADAS = "Herramientas prestadas";
    private static final String RPT_HERRAMIENTAS_VENCIDAS = "Herramientas pendientes de devolución";

    /**
     * Los reportes que quedan de la especificación, todos disponibles aquí.
     *
     * Los de herramientas estaban fuera de este menú mientras ese módulo no
     * tenía pantalla: siempre habrían devuelto filas vacías porque no había
     * forma de dar nada de alta.
     */
    private static final List<String> RPT_OPCIONES = List.of(
            RPT_EXISTENCIAS, RPT_AGOTADOS, RPT_STOCK_MINIMO,
            RPT_ENTRADAS, RPT_SALIDAS, RPT_HISTORIAL, RPT_KARDEX,
            RPT_TRABAJADOR, RPT_DEPARTAMENTO, RPT_MOVIMIENTOS_USUARIO,
            RPT_HERRAMIENTAS_PRESTADAS, RPT_HERRAMIENTAS_VENCIDAS);

    /**
     * Genera un reporte real desde la base de datos, lo muestra en pantalla y
     * permite exportarlo a CSV.
     *
     * El cálculo vive en {@code ReporteService} y las columnas en
     * {@code ReportesTabla}; aquí solo se pide el reporte, se piden los datos
     * que necesite (periodo, trabajador, producto…) y se dibuja el resultado.
     */
    @FXML
    private void generarReporte() {
        Optional<String> elegido = preguntar("Generar reporte", "Reporte de inventario",
                "Reporte:", RPT_OPCIONES, RPT_EXISTENCIAS);
        if (elegido.isEmpty()) {
            return;
        }
        String opcion = elegido.get();

        ReporteService reporteService = JavaFxApplication.getContext().getBean(ReporteService.class);
        ReportesTabla.Tabla tabla;

        switch (opcion) {
            case RPT_AGOTADOS ->
                    tabla = ReportesTabla.productosAgotados(reporteService.productosAgotados(usuarioActual));
            case RPT_STOCK_MINIMO ->
                    tabla = ReportesTabla.productosStockMinimo(
                            reporteService.productosStockMinimo(usuarioActual));

            case RPT_ENTRADAS -> {
                Optional<LocalDate[]> periodo = preguntarPeriodo();
                if (periodo.isEmpty()) {
                    return;
                }
                tabla = ReportesTabla.entradasPeriodo(reporteService.entradasPorPeriodo(
                        periodo.get()[0], periodo.get()[1], usuarioActual));
            }
            case RPT_SALIDAS -> {
                Optional<LocalDate[]> periodo = preguntarPeriodo();
                if (periodo.isEmpty()) {
                    return;
                }
                tabla = ReportesTabla.salidasPeriodo(reporteService.salidasPorPeriodo(
                        periodo.get()[0], periodo.get()[1], usuarioActual));
            }
            case RPT_HISTORIAL -> {
                Optional<LocalDate[]> periodo = preguntarPeriodo();
                if (periodo.isEmpty()) {
                    return;
                }
                tabla = ReportesTabla.historialMovimientos(reporteService.historialMovimientos(
                        periodo.get()[0], periodo.get()[1], usuarioActual));
            }

            case RPT_KARDEX -> {
                Optional<Producto> producto = preguntarDe("Kardex del producto", "Producto",
                        "Producto:", productosActivos(),
                        p -> p.getNombre() + "  (" + p.getCodigoBarras() + ")");
                if (producto.isEmpty()) {
                    return;
                }
                tabla = ReportesTabla.kardexProducto(reporteService.kardexProducto(
                        producto.get().getId().longValue(), usuarioActual));
            }

            case RPT_TRABAJADOR -> {
                Optional<String> persona = preguntarDe("Materiales por trabajador",
                        "Persona que solicitó", "Trabajador:",
                        salidas().personasQueSolicitaron(), p -> p);
                if (persona.isEmpty()) {
                    return;
                }
                tabla = ReportesTabla.materialesTrabajador(
                        reporteService.materialesPorTrabajador(persona.get(), usuarioActual));
            }

            case RPT_DEPARTAMENTO -> {
                Optional<String> departamento = preguntarDe("Materiales por departamento",
                        "Área o departamento", "Departamento:",
                        salidas().departamentosQueSolicitaron(), d -> d);
                if (departamento.isEmpty()) {
                    return;
                }
                tabla = ReportesTabla.materialesDepartamento(
                        reporteService.materialesPorDepartamento(departamento.get(), usuarioActual));
            }

            case RPT_MOVIMIENTOS_USUARIO -> {
                Optional<Usuario> usuario = preguntarDe("Movimientos por usuario", "Usuario",
                        "Usuario:", usuarios(),
                        u -> u.getNombre() + "  (" + u.getEmail() + ")");
                if (usuario.isEmpty()) {
                    return;
                }
                tabla = ReportesTabla.movimientosPorUsuario(reporteService.movimientosPorUsuario(
                        usuario.get().getId(), usuarioActual));
            }

            case RPT_HERRAMIENTAS_PRESTADAS ->
                    tabla = ReportesTabla.herramientasPrestadas(
                            reporteService.herramientasPrestadas(usuarioActual));
            case RPT_HERRAMIENTAS_VENCIDAS ->
                    tabla = ReportesTabla.herramientasPendientesDevolucion(
                            reporteService.herramientasPendientesDevolucion(usuarioActual));

            default -> tabla = ReportesTabla.existenciasActuales(
                    reporteService.existenciasActuales(usuarioActual));
        }

        mostrarReporte(opcion, tabla.encabezados(), tabla.filas());
    }

    /** Rango de fechas para los reportes de periodo (por defecto, el último mes). */
    private Optional<LocalDate[]> preguntarPeriodo() {
        DatePicker desde = new DatePicker(LocalDate.now().minusMonths(1));
        DatePicker hasta = new DatePicker(LocalDate.now());

        GridPane rejilla = new GridPane();
        rejilla.setHgap(10);
        rejilla.setVgap(10);
        rejilla.add(new Label("Desde:"), 0, 0);
        rejilla.add(desde, 1, 0);
        rejilla.add(new Label("Hasta:"), 0, 1);
        rejilla.add(hasta, 1, 1);

        Dialog<LocalDate[]> dialogo = new Dialog<>();
        dialogo.setTitle("Periodo del reporte");
        dialogo.setHeaderText("Rango de fechas");
        dialogo.getDialogPane().setContent(rejilla);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogo.setResultConverter(boton -> {
            if (boton != ButtonType.OK || desde.getValue() == null || hasta.getValue() == null) {
                return null;
            }
            return new LocalDate[]{desde.getValue(), hasta.getValue()};
        });

        Optional<LocalDate[]> rango = Optional.ofNullable(dialogo.showAndWait().orElse(null));
        if (rango.isEmpty()) {
            return Optional.empty();
        }
        if (rango.get()[0].isAfter(rango.get()[1])) {
            new Alert(Alert.AlertType.ERROR, "La fecha inicial no puede ser posterior a la final")
                    .showAndWait();
            return Optional.empty();
        }
        return rango;
    }

    /**
     * Cuadro con una lista para elegir un elemento. Devuelve vacío si se cancela
     * o si no hay nada que elegir (y avisa en ese caso).
     */
    private <T> Optional<T> preguntarDe(String titulo, String cabecera, String etiqueta,
                                        List<T> opciones, Function<T, String> etiquetador) {
        if (opciones.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION,
                    "No hay registros para elegir en: " + titulo).showAndWait();
            return Optional.empty();
        }

        List<String> etiquetas = opciones.stream().map(etiquetador).toList();
        Optional<String> elegida = preguntar(titulo, cabecera, etiqueta, etiquetas, etiquetas.get(0));
        if (elegida.isEmpty()) {
            return Optional.empty();
        }
        int indice = etiquetas.indexOf(elegida.get());
        return indice < 0 ? Optional.empty() : Optional.of(opciones.get(indice));
    }

    private Optional<String> preguntar(String titulo, String cabecera, String etiqueta,
                                       List<String> opciones, String predeterminado) {
        ChoiceDialog<String> dialogo = new ChoiceDialog<>(predeterminado, opciones);
        dialogo.setTitle(titulo);
        dialogo.setHeaderText(cabecera);
        dialogo.setContentText(etiqueta);
        return dialogo.showAndWait();
    }

    /** Productos que se pueden llevar al reporte de kardex. */
    private List<Producto> productosActivos() {
        return productoService.listarActivos().stream()
                .sorted(Comparator.comparing(Producto::getNombre))
                .toList();
    }

    private SalidaAlmacenRepository salidas() {
        return JavaFxApplication.getContext().getBean(SalidaAlmacenRepository.class);
    }

    private List<Usuario> usuarios() {
        return JavaFxApplication.getContext().getBean(UsuarioRepository.class).findAll().stream()
                .filter(u -> !Boolean.FALSE.equals(u.getActivo()))
                .sorted(Comparator.comparing(Usuario::getNombre))
                .toList();
    }

    /** Muestra el reporte en pantalla y ofrece exportarlo a CSV. */
    private void mostrarReporte(String nombreReporte, List<String> encabezados,
                                List<List<String>> filas) {
        TextArea area = new TextArea(renderTabla(encabezados, filas));
        area.setEditable(false);
        area.setWrapText(false);
        area.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12;");

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Reporte de inventario");
        dialogo.setHeaderText(nombreReporte + "  ·  " + filas.size() + " registro(s)");
        dialogo.getDialogPane().setContent(area);
        dialogo.getDialogPane().setPrefSize(980, 540);

        ButtonType guardar = new ButtonType("Guardar CSV…", ButtonBar.ButtonData.OK_DONE);
        ButtonType cerrar = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogo.getDialogPane().getButtonTypes().addAll(guardar, cerrar);

        Optional<ButtonType> resultado = dialogo.showAndWait();
        if (resultado.isPresent() && resultado.get() == guardar) {
            guardarCsv(nombreReporte, encabezados, filas);
        }
    }

    /** Pide dónde guardar y escribe el reporte como CSV (UTF-8 con BOM para Excel). */
    private void guardarCsv(String nombreReporte, List<String> encabezados,
                            List<List<String>> filas) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar reporte");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivo CSV", "*.csv"));
        chooser.setInitialFileName(nombreReporte.replaceAll("\\s+", "_").toLowerCase()
                + "_" + LocalDate.now() + ".csv");

        File archivo = chooser.showSaveDialog(tablaProductos.getScene().getWindow());
        if (archivo == null) {
            return;
        }

        StringBuilder contenido = new StringBuilder("\uFEFF"); // BOM: Excel sí abre los acentos
        contenido.append(csv(encabezados)).append("\r\n");
        for (List<String> fila : filas) {
            contenido.append(csv(fila)).append("\r\n");
        }

        try {
            Files.write(archivo.toPath(), contenido.toString().getBytes(StandardCharsets.UTF_8));
            new Alert(Alert.AlertType.INFORMATION,
                    "Reporte guardado en:\n" + archivo.getAbsolutePath()).showAndWait();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR,
                    "No se pudo guardar el archivo:\n" + e.getMessage()).showAndWait();
        }
    }

    /** Texto alineado en columnas para mostrar el reporte en pantalla. */
    private String renderTabla(List<String> encabezados, List<List<String>> filas) {
        int[] ancho = new int[encabezados.size()];
        for (int i = 0; i < encabezados.size(); i++) {
            ancho[i] = encabezados.get(i).length();
        }
        for (List<String> fila : filas) {
            for (int i = 0; i < fila.size() && i < ancho.length; i++) {
                ancho[i] = Math.max(ancho[i], fila.get(i).length());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(pintaColumnas(encabezados, ancho)).append('\n');
        for (int i = 0; i < encabezados.size(); i++) {
            if (i > 0) {
                sb.append("  ");
            }
            sb.append("-".repeat(ancho[i]));
        }
        sb.append('\n');
        for (List<String> fila : filas) {
            sb.append(pintaColumnas(fila, ancho)).append('\n');
        }
        return sb.toString();
    }

    private String pintaColumnas(List<String> columnas, int[] ancho) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columnas.size() && i < ancho.length; i++) {
            if (i > 0) {
                sb.append("  ");
            }
            String valor = s(columnas.get(i));
            sb.append(valor);
            sb.append(" ".repeat(Math.max(0, ancho[i] - valor.length())));
        }
        return sb.toString();
    }

    private String csv(List<String> columnas) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columnas.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String valor = s(columnas.get(i));
            if (valor.contains(",") || valor.contains("\"") || valor.contains("\n")) {
                valor = "\"" + valor.replace("\"", "\"\"") + "\"";
            }
            sb.append(valor);
        }
        return sb.toString();
    }

    /** Valor en texto para mostrar; {@code null} queda como cadena vacía. */
    private String s(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private void editarProducto(Producto producto) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/producto-form.fxml"));
            loader.setControllerFactory(JavaFxApplication.getContext()::getBean);
            Parent root = loader.load();
            ProductoFormController controller = loader.getController();
            controller.setProducto(producto);
            Stage stage = new Stage();
            stage.setTitle("Editar Producto");
            stage.setScene(new Scene(root));
            stage.showAndWait();
            cargarProductos();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void darSalida(Producto producto) {
        abrirVentanaDeSalida(producto);
    }

    /**
     * Abre el formulario de salida con la lista vacía.
     *
     * Es el botón de la barra: sirve para surtir una tanda entera sin tener
     * que buscar primero un producto del que partir.
     */
    @FXML
    private void abrirSalida() {
        abrirVentanaDeSalida(null);
    }

    /**
     * Un solo camino para las dos formas de llegar: desde el botón de la barra
     * (ningún producto, lista vacía) o desde el de la fila (ese producto entra
     * ya puesto y lo demás se le va agregando encima).
     */
    private void abrirVentanaDeSalida(Producto producto) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/salida-form.fxml"));
            loader.setControllerFactory(JavaFxApplication.getContext()::getBean);
            Parent root = loader.load();

            SalidaFormController controller = loader.getController();
            controller.setUsuarioActual(usuarioActual);
            if (producto != null) {
                controller.setProducto(producto);
            }

            Stage stage = new Stage();
            stage.setTitle(producto == null
                    ? "Salida de Almacén"
                    : "Dar Salida - " + producto.getNombre());
            stage.setScene(new Scene(root));
            stage.showAndWait();
            cargarProductos();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void eliminarProducto(Producto producto) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar eliminación");
        confirm.setHeaderText(null);
        confirm.setContentText("¿Eliminar producto: " + producto.getNombre() + "?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            productoService.eliminar(producto.getId());
            cargarProductos();
        }
    }

    @FXML
    private void cerrarSesion() {
        // Registrar el cierre de sesión en la bitácora antes de salir
        try {
            bitacoraService.registrar(BitacoraService.LOGOUT, "SEGURIDAD",
                    usuarioActual != null ? usuarioActual.getEmail() : "desconocido",
                    "Cierre de sesión desde la interfaz",
                    usuarioActual);
        } catch (Exception e) {
            // No bloquear el cierre por un error de auditoría
        }

        // Cerrar sesión no apaga la aplicación: deja la misma ventana en la
        // pantalla de acceso, para que el equipo siga encendido y el que venga
        // entre con su usuario y su rol. Al volver a entrar ya se fija de nuevo
        // quién responde del inventario.
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/login.fxml"));
            loader.setControllerFactory(JavaFxApplication.getContext()::getBean);
            Parent raiz = loader.load();

            Stage stage = (Stage) tablaProductos.getScene().getWindow();
            stage.setScene(new Scene(raiz));
            stage.setTitle("SGIA");
            stage.setMaximized(false);
            stage.sizeToScene();

            usuarioActual = null;
            ProductoService.setUsuarioSesion(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
