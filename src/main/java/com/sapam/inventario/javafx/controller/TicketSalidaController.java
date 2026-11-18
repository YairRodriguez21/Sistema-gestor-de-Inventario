package com.sapam.inventario.javafx.controller;

import com.sapam.inventario.entity.SalidaAlmacen;
import com.sapam.inventario.javafx.RegistroImpresion;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * El papel que se entrega con la salida.
 *
 * Un descuento en la existencia no basta para responder después a quién se fue
 * el material: el ticket deja en un solo papel los productos, quién lo
 * solicitó, la fecha y hora, con qué orden de trabajo, quién lo entregó y lo
 * que se anotó en observaciones.
 *
 * Está dibujado a 192 px (50.8 mm). La POS-80 imprime en papel de 80 mm; el
 * ticket se queda en 192 px para dejar margen y no pegar el texto al borde.
 * Si la impresora elegida tuviera menos ancho, la imagen se ajusta
 * proporcionalmente en vez de abortar la salida.
 *
 * Se imprime con AWT en una página a la medida: 80 mm de ancho por el alto del
 * ticket más 5 mm. No se usa el rollo por defecto de 3276 mm ni el papel de
 * 210 mm: con esas páginas el trabajo de JavaFX se quedaba abierto hasta
 * cerrar el programa. La copia se hace a 203 dpi y en blanco y negro puro para
 * que el texto salga firme, y el trabajo corre en un hilo aparte para que la
 * ventana del ticket no se congele.
 *
 * No es un bean de Spring: la abre {@link SalidaFormController} con un
 * FXMLLoader común y todo lo que muestra se lo pasan por la llamada. Eso
 * importa porque {@code ProductoSalida.producto} es perezoso —leer el producto
 * aquí saltaría de la transacción y petaría— así que en su lugar llegan los
 * nombres ya resueltos, que es lo único que el papel necesita.
 */
public class TicketSalidaController {

    /** Una renglón del papel: nombre del producto y cuántas unidades salieron. */
    public record ProductoDelTicket(String nombre, int cantidad) {}

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** La impresora térmica que entrega los tickets de salida. */
    private static final String IMPRESORA_DE_TICKETS = "POS-80";

    /** Los puntos por pulgada que usa JavaFX para medir los nodos. */
    private static final double DPI_PANTALLA = 96.0;

    /** Tope de la copia para no inflar la memoria si el driver dice 600 dpi. */
    private static final double ESCALA_MAXIMA_COPIA = 4.0;

    /** Gris por debajo del cual se manda negro; el resto, blanco. */
    private static final int UMBRAL_TINTA = 180;

    /** El papel de la POS-80 mide 80 mm de ancho. */
    private static final double ANCHO_PAPEL_MM = 80.0;

    /** Aire al final del ticket para que la térmica corte sin apretar el texto. */
    private static final double MARGEN_INFERIOR_MM = 5.0;

    private static final double MM_A_PT = 72.0 / 25.4;

    /** Evita que dos clics seguidos manden dos copias del mismo ticket. */
    private volatile boolean imprimiendo;

    @FXML private VBox vbTicket;
    @FXML private VBox vbProductos;
    @FXML private Label lblFolio;
    @FXML private Label lblFechaHora;
    @FXML private Label lblEntrego;
    @FXML private Label lblOrdenTrabajo;
    @FXML private Label lblSolicita;
    @FXML private Label lblArea;
    @FXML private Label lblRecibe;
    @FXML private Label lblMotivo;
    @FXML private Label lblObservaciones;

    /** Llena el papel con la salida recién registrada. */
    public void cargar(SalidaAlmacen salida, List<ProductoDelTicket> productos) {
        lblFolio.setText("Folio: " + salida.getFolio());

        String fechaHora = "";
        if (salida.getFecha() != null && salida.getHora() != null) {
            fechaHora = salida.getFecha().format(FECHA) + "  " + salida.getHora().withNano(0);
        }
        lblFechaHora.setText(fechaHora);

        lblEntrego.setText(nombreDe(salida.getUsuarioEntrega() != null
                ? salida.getUsuarioEntrega()
                : salida.getUsuarioRegistro()));

        lblOrdenTrabajo.setText(vacio(salida.getOrdenTrabajo()) ? "—" : salida.getOrdenTrabajo());

        lblSolicita.setText(textoConPuesto(salida.getPersonaSolicita(), salida.getPuestoSolicita()));
        lblArea.setText(vacio(salida.getDepartamentoSolicita())
                ? "—" : salida.getDepartamentoSolicita());
        lblRecibe.setText(textoConPuesto(salida.getPersonaRecibe(), salida.getPuestoRecibe()));

        // "OTRO" no explica nada por sí solo: en el papel va la explicación,
        // no la palabra que solo sirve para decir que no es ninguno de los otros.
        String motivo = salida.getMotivoSalida() == null ? "—" : salida.getMotivoSalida();
        if (!vacio(salida.getMotivoOtro())) {
            motivo = motivo + " — " + salida.getMotivoOtro();
        }
        lblMotivo.setText(motivo);

        lblObservaciones.setText(vacio(salida.getObservaciones())
                ? "(sin observaciones)"
                : salida.getObservaciones());

        vbProductos.getChildren().clear();
        if (productos == null || productos.isEmpty()) {
            vbProductos.getChildren().add(etiqueta("Sin productos", true));
            return;
        }
        for (ProductoDelTicket producto : productos) {
            vbProductos.getChildren().add(renglonDeProducto(producto));
        }
    }

    /**
     * Un producto por renglón, sin tabla.
     *
     * La tabla de JavaFX se dibuja al ancho de pantalla y en un rollo térmico
     * sale desalineada con sus bordes y su encabezado; un par de etiquetas
     * entra limpia en cualquier ancho.
     */
    private static HBox renglonDeProducto(ProductoDelTicket producto) {
        Label nombre = etiqueta(producto.nombre(), false);
        nombre.setWrapText(true);
        HBox.setHgrow(nombre, Priority.ALWAYS);

        Label cantidad = etiqueta(String.valueOf(producto.cantidad()), true);
        cantidad.setMinWidth(26);
        cantidad.setAlignment(Pos.CENTER_RIGHT);

        return new HBox(6, nombre, cantidad);
    }

    private static Label etiqueta(String texto, boolean negrita) {
        Label label = new Label(texto);
        label.setStyle("-fx-font-size: 11px;" + (negrita ? " -fx-font-weight: bold;" : ""));
        return label;
    }

    /**
     * Busca la impresora de tickets por nombre. Si no está instalada, se usa
     * la predeterminada.
     */
    private static PrintService impresoraDeTickets() {
        for (PrintService impresora : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (impresora.getName().regionMatches(
                    true, 0, IMPRESORA_DE_TICKETS, 0, IMPRESORA_DE_TICKETS.length())) {
                return impresora;
            }
        }
        return PrintServiceLookup.lookupDefaultPrintService();
    }

    /**
     * Deja la copia en blanco y negro puro.
     *
     * La térmica imprime puntos, no grises. Si se le manda texto con bordes
     * suavizados, el driver los traman y el resultado se ve claro y borroso.
     * Con un umbral, las letras salen en negro sólido.
     */
    private static WritableImage aTintaSolida(WritableImage original) {
        int ancho = (int) original.getWidth();
        int alto = (int) original.getHeight();
        WritableImage salida = new WritableImage(ancho, alto);
        PixelReader lector = original.getPixelReader();
        PixelWriter escritor = salida.getPixelWriter();
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int argb = lector.getArgb(x, y);
                int rojo = (argb >> 16) & 0xFF;
                int verde = (argb >> 8) & 0xFF;
                int azul = argb & 0xFF;
                int gris = (rojo * 299 + verde * 587 + azul * 114) / 1000;
                escritor.setArgb(x, y, gris < UMBRAL_TINTA ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return salida;
    }

    /**
     * Pasa la copia de JavaFX a una imagen que entiende AWT.
     *
     * AWT imprime la imagen en la página a la medida; la conversión se hace en
     * el hilo de JavaFX antes de lanzar el trabajo, para no tocar nodos de la
     * interfaz desde el hilo de impresión.
     */
    private static BufferedImage aBuffered(WritableImage original) {
        int ancho = (int) original.getWidth();
        int alto = (int) original.getHeight();
        BufferedImage salida = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        PixelReader lector = original.getPixelReader();
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                salida.setRGB(x, y, lector.getArgb(x, y));
            }
        }
        return salida;
    }

    /**
     * Manda el ticket a la impresora.
     *
     * La copia se arma en el hilo de JavaFX; el trabajo AWT se lanza aparte
     * para que la ventana del ticket no se quede congelada mientras Windows
     * escribe en la cola. La página se ajusta al alto real del ticket.
     */
    @FXML
    private void imprimirTicket() {
        if (imprimiendo) {
            RegistroImpresion.anotar("ticket: ya hay una impresión en curso");
            return;
        }

        RegistroImpresion.anotar("ticket: se pide imprimir (mide "
                + Math.round(vbTicket.getWidth()) + " x "
                + Math.round(vbTicket.getHeight()) + " px)");

        PrintService impresora = impresoraDeTickets();
        if (impresora == null) {
            RegistroImpresion.anotar("ticket: no hay ninguna impresora instalada");
            aviso(Alert.AlertType.WARNING, "No se encontró ninguna impresora instalada.");
            return;
        }
        RegistroImpresion.anotar("ticket: se imprime en " + impresora.getName());

        double anchoTicketPt = Math.max(1.0, vbTicket.getWidth() * 72.0 / DPI_PANTALLA);
        double altoTicketPt = Math.max(1.0, vbTicket.getHeight() * 72.0 / DPI_PANTALLA);

        // Página a la medida: el ancho del rollo y el alto justo del ticket.
        // Así la térmica no avanza 210 mm por cada salida y el trabajo termina
        // solo, sin quedarse esperando a que se cierre la aplicación.
        double anchoPapelPt = Math.max(
                ANCHO_PAPEL_MM * MM_A_PT,
                anchoTicketPt + 10.0 * MM_A_PT);
        double altoPapelPt = altoTicketPt + MARGEN_INFERIOR_MM * MM_A_PT;
        RegistroImpresion.anotar("ticket: página a la medida "
                + Math.round(anchoPapelPt / MM_A_PT) + " x "
                + Math.round(altoPapelPt / MM_A_PT) + " mm");

        // Copia a la resolución de la térmica (203 dpi) y en blanco y negro
        // puro. El snapshot se hace aquí, en el hilo de JavaFX, y de aquí en
        // adelante ya no se toca ningún nodo de la interfaz.
        double escalaCopia = Math.min(ESCALA_MAXIMA_COPIA,
                Math.max(1.0, 203.0 / DPI_PANTALLA));
        SnapshotParameters maquetaDeCopia = new SnapshotParameters();
        maquetaDeCopia.setFill(Color.WHITE);
        if (escalaCopia > 1.0) {
            maquetaDeCopia.setTransform(new Scale(escalaCopia, escalaCopia));
        }
        int anchoCopia = (int) Math.ceil(vbTicket.getWidth() * escalaCopia);
        int altoCopia = (int) Math.ceil(vbTicket.getHeight() * escalaCopia);
        WritableImage copia = new WritableImage(anchoCopia, altoCopia);
        vbTicket.snapshot(maquetaDeCopia, copia);
        BufferedImage imagen = aBuffered(aTintaSolida(copia));
        RegistroImpresion.anotar("ticket: copia de " + anchoCopia + " x " + altoCopia
                + " px a 203 dpi");

        double anchoPapel = anchoPapelPt;
        double altoPapel = altoPapelPt;
        double anchoTicket = anchoTicketPt;
        double altoTicket = altoTicketPt;
        imprimiendo = true;
        Thread hilo = new Thread(() -> imprimirEnSegundoPlano(
                impresora, imagen, anchoPapel, altoPapel, anchoTicket, altoTicket),
                "impresion-ticket");
        hilo.setDaemon(true);
        hilo.start();
    }

    /**
     * Imprime la copia con AWT en el hilo de fondo.
     *
     * No se usa {@code javafx.print.PrinterJob}: con la POS-80 su
     * {@code printPage} se quedaba bloqueado hasta cerrar la aplicación y la
     * ventana del ticket parecía congelada. AWT entrega el trabajo a la cola
     * de Windows y devuelve el control.
     */
    private void imprimirEnSegundoPlano(
            PrintService impresora,
            BufferedImage imagen,
            double anchoPapelPt,
            double altoPapelPt,
            double anchoTicketPt,
            double altoTicketPt) {
        long inicio = System.nanoTime();
        try {
            PrinterJob trabajo = PrinterJob.getPrinterJob();
            trabajo.setPrintService(impresora);

            PageFormat formato = trabajo.defaultPage();
            formato.setOrientation(PageFormat.PORTRAIT);
            Paper papel = new Paper();
            papel.setSize(anchoPapelPt, altoPapelPt);
            papel.setImageableArea(0, 0, anchoPapelPt, altoPapelPt);
            formato.setPaper(papel);

            trabajo.setPrintable((grafico, pagina, indice) -> {
                if (indice > 0) {
                    return Printable.NO_SUCH_PAGE;
                }
                Graphics2D g2 = (Graphics2D) grafico;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                double x = pagina.getImageableX()
                        + Math.max(0.0, (pagina.getImageableWidth() - anchoTicketPt) / 2.0);
                double y = pagina.getImageableY();
                g2.drawImage(imagen,
                        (int) Math.round(x), (int) Math.round(y),
                        (int) Math.round(anchoTicketPt), (int) Math.round(altoTicketPt),
                        null);
                return Printable.PAGE_EXISTS;
            }, formato);

            RegistroImpresion.anotar("ticket: entrando a job.print");
            trabajo.print();
            RegistroImpresion.anotar("ticket: job.print terminó en "
                    + (System.nanoTime() - inicio) / 1_000_000 + " ms");
        } catch (PrinterException | RuntimeException fallo) {
            RegistroImpresion.anotar("ticket: job.print falló con "
                    + fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            Platform.runLater(() -> aviso(Alert.AlertType.WARNING,
                    "No se pudo imprimir el ticket en " + impresora.getName()
                            + ".\n\nRevisa que el rollo no se haya acabado y que la "
                            + "cola de impresión no esté pausada."));
        } finally {
            imprimiendo = false;
        }
    }

    @FXML
    private void cerrarTicket() {
        Stage stage = (Stage) vbTicket.getScene().getWindow();
        stage.close();
    }

    private static String nombreDe(com.sapam.inventario.entity.Usuario usuario) {
        if (usuario == null) {
            return "—";
        }
        String nombre = usuario.getNombreCompleto();
        return vacio(nombre) ? usuario.getEmail() : nombre;
    }

    private static String textoConPuesto(String nombre, String puesto) {
        if (vacio(nombre)) {
            return "—";
        }
        return vacio(puesto) ? nombre : nombre + "  (" + puesto + ")";
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private void aviso(Alert.AlertType tipo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
