package com.sapam.inventario.javafx.controller;

import com.sapam.inventario.entity.Herramienta;
import com.sapam.inventario.entity.PrestamoHerramienta;
import com.sapam.inventario.javafx.ImpresionCodigoBarras;
import com.sapam.inventario.javafx.RegistroImpresion;
import com.sapam.inventario.service.CodigoBarrasService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
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
import java.io.ByteArrayInputStream;
import java.time.format.DateTimeFormatter;

/**
 * El papel que se entrega al prestar una herramienta.
 *
 * Lleva quién se la lleva, el área y para dónde va, además de la herramienta,
 * su código, la fecha de entrega y la fecha en que se espera de vuelta. El
 * folio se imprime también como código de barras: escanear ese código en la
 * ventana de Herramientas abre la devolución de ese préstamo concreto, sin
 * buscarlo antes a mano.
 *
 * No es un bean de Spring: lo abre {@link HerramientasController} con un
 * FXMLLoader común. El código de barras se pega después del snapshot, a 203
 * dpi, con la misma técnica de las etiquetas; así el lector no pierde los
 * módulos estrechos al pasar por el reescalado de JavaFX.
 */
public class TicketPrestamoController {

    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** La impresora térmica que entrega los tickets. */
    private static final String IMPRESORA_DE_TICKETS = "POS-80";

    /** JavaFX mide los nodos en puntos de 96 dpi. */
    private static final double DPI_PANTALLA = 96.0;

    /** La POS-80 imprime a 203 dpi. */
    private static final double DPI_IMPRESORA = 203.0;

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

    private final CodigoBarrasService codigos = new CodigoBarrasService();

    private BufferedImage codigoImpresion;

    @FXML private VBox vbTicket;
    @FXML private ImageView imgCodigo;
    @FXML private Label lblFolio;
    @FXML private Label lblCodigoTexto;
    @FXML private Label lblFechaHora;
    @FXML private Label lblRecibe;
    @FXML private Label lblArea;
    @FXML private Label lblDestino;
    @FXML private Label lblHerramienta;
    @FXML private Label lblCodigoHerramienta;
    @FXML private Label lblSerie;
    @FXML private Label lblDevolverAntes;
    @FXML private Label lblEntrego;
    @FXML private Label lblObservaciones;

    /** Llena el papel con el préstamo recién registrado. */
    public void cargar(PrestamoHerramienta prestamo) {
        String folio = prestamo == null ? null : prestamo.getFolio();
        lblFolio.setText("Folio: " + (vacio(folio) ? "—" : folio));
        lblCodigoTexto.setText(vacio(folio) ? "Sin código de ticket" : "Código: " + folio);

        if (vacio(folio)) {
            imgCodigo.setImage(null);
            codigoImpresion = null;
        } else {
            try {
                // La vista previa usa el PNG que ya genera el servicio; la
                // impresión guarda aparte el bitmap de 640 px para pegarlo sin
                // volver a pasar por JavaFX.
                byte[] png = codigos.png(folio, CodigoBarrasService.TAMANO_DEFECTO);
                imgCodigo.setImage(new Image(new ByteArrayInputStream(png)));
                codigoImpresion = codigos.generar(folio, CodigoBarrasService.TAMANO_DEFECTO);
            } catch (IllegalArgumentException e) {
                imgCodigo.setImage(null);
                codigoImpresion = null;
                lblCodigoTexto.setText("No se pudo generar el código del ticket: " + e.getMessage());
            }
        }

        if (prestamo == null) {
            return;
        }

        lblFechaHora.setText(prestamo.getFechaEntrega() == null
                ? "—" : prestamo.getFechaEntrega().format(FECHA_HORA));
        lblRecibe.setText(vacio(prestamo.getPersonaRecibe()) ? "—" : prestamo.getPersonaRecibe());
        lblArea.setText(vacio(prestamo.getAreaResponsable()) ? "—" : prestamo.getAreaResponsable());
        lblDestino.setText(vacio(prestamo.getDestino()) ? "—" : prestamo.getDestino());
        lblDevolverAntes.setText(prestamo.getFechaEstimadaDevolucion() == null
                ? "—" : prestamo.getFechaEstimadaDevolucion().format(FECHA));
        lblEntrego.setText(vacio(prestamo.getPersonaEntrega()) ? "—" : prestamo.getPersonaEntrega());
        lblObservaciones.setText(vacio(prestamo.getObservaciones())
                ? "(sin observaciones)" : prestamo.getObservaciones());

        Herramienta herramienta = prestamo.getHerramienta();
        lblHerramienta.setText(herramienta == null || vacio(herramienta.getNombre())
                ? "—" : herramienta.getNombre());
        lblCodigoHerramienta.setText(herramienta == null || vacio(herramienta.getCodigoBarras())
                ? "—" : herramienta.getCodigoBarras());
        lblSerie.setText(herramienta == null || vacio(herramienta.getNumeroSerie())
                ? "—" : herramienta.getNumeroSerie());
    }

    /**
     * Compone el ticket sin mandarlo a la impresora.
     *
     * Se expone para la prueba de regresión: el bitmap final tiene que seguir
     * conteniendo un código que se pueda decodificar. Debe llamarse desde el
     * hilo de JavaFX, igual que {@link #imprimirTicket()}.
     */
    public BufferedImage componer() {
        asegurarLayout();
        double escala = Math.min(ESCALA_MAXIMA_COPIA,
                Math.max(1.0, DPI_IMPRESORA / DPI_PANTALLA));

        SnapshotParameters parametros = new SnapshotParameters();
        parametros.setFill(Color.WHITE);
        parametros.setTransform(new Scale(escala, escala));

        int ancho = Math.max(1, (int) Math.ceil(vbTicket.getWidth() * escala));
        int alto = Math.max(1, (int) Math.ceil(vbTicket.getHeight() * escala));
        WritableImage copia = new WritableImage(ancho, alto);
        vbTicket.snapshot(parametros, copia);

        BufferedImage imagen = aBuffered(aTintaSolida(copia));
        pegarCodigo(imagen, escala);
        return imagen;
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
            RegistroImpresion.anotar("ticket de préstamo: ya hay una impresión en curso");
            return;
        }

        PrintService impresora = impresoraDeTickets();
        if (impresora == null) {
            RegistroImpresion.anotar("ticket de préstamo: no hay ninguna impresora instalada");
            aviso(Alert.AlertType.WARNING, "No se encontró ninguna impresora instalada.");
            return;
        }

        asegurarLayout();
        double anchoTicketPt = Math.max(1.0, vbTicket.getWidth() * 72.0 / DPI_PANTALLA);
        double altoTicketPt = Math.max(1.0, vbTicket.getHeight() * 72.0 / DPI_PANTALLA);

        // Página a la medida: el ancho del rollo y el alto justo del ticket.
        // Así la térmica no avanza de más y el trabajo termina solo.
        double anchoPapelPt = Math.max(
                ANCHO_PAPEL_MM * MM_A_PT,
                anchoTicketPt + 10.0 * MM_A_PT);
        double altoPapelPt = altoTicketPt + MARGEN_INFERIOR_MM * MM_A_PT;

        BufferedImage imagen;
        try {
            imagen = componer();
        } catch (RuntimeException fallo) {
            RegistroImpresion.anotar("ticket de préstamo: falló al preparar la copia con "
                    + fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            aviso(Alert.AlertType.WARNING,
                    "No se pudo preparar el ticket: " + fallo.getMessage());
            return;
        }

        RegistroImpresion.anotar("ticket de préstamo: se imprime en " + impresora.getName()
                + " (copia de " + imagen.getWidth() + " x " + imagen.getHeight()
                + " px a 203 dpi, página de "
                + Math.round(anchoPapelPt / MM_A_PT) + " x "
                + Math.round(altoPapelPt / MM_A_PT) + " mm)");

        imprimiendo = true;
        Thread hilo = new Thread(() -> imprimirEnSegundoPlano(
                impresora, imagen, anchoPapelPt, altoPapelPt, anchoTicketPt, altoTicketPt),
                "impresion-ticket-prestamo");
        hilo.setDaemon(true);
        hilo.start();
    }

    /**
     * Imprime la copia con AWT en el hilo de fondo.
     *
     * No se usa {@code javafx.print.PrinterJob}: con la POS-80 su
     * {@code printPage} se quedaba bloqueado hasta cerrar la aplicación.
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
            trabajo.setJobName("Ticket de préstamo");

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

            RegistroImpresion.anotar("ticket de préstamo: entrando a job.print");
            trabajo.print();
            RegistroImpresion.anotar("ticket de préstamo: job.print terminó en "
                    + (System.nanoTime() - inicio) / 1_000_000 + " ms");
        } catch (PrinterException | RuntimeException fallo) {
            RegistroImpresion.anotar("ticket de préstamo: job.print falló con "
                    + fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            Platform.runLater(() -> aviso(Alert.AlertType.WARNING,
                    "No se pudo imprimir el ticket en " + impresora.getName()
                            + ".\n\nRevisa que el rollo no se haya acabado y que la "
                            + "cola de impresión no esté pausada."));
        } finally {
            imprimiendo = false;
        }
    }

    /**
     * Pega el código de barras en el hueco del visor, a la resolución final.
     *
     * El hueco se localiza dentro del ticket ya maquetado y se pasa a píxeles
     * de impresora con la misma escala que el snapshot. La vista previa que
     * queda debajo se cubre con las barras nítidas.
     */
    private void pegarCodigo(BufferedImage destino, double escala) {
        if (codigoImpresion == null || imgCodigo.getImage() == null) {
            return;
        }
        Point2D origen = vbTicket.localToScene(0, 0);
        Bounds area = imgCodigo.localToScene(imgCodigo.getBoundsInLocal());
        int x = (int) Math.round((area.getMinX() - origen.getX()) * escala);
        int y = (int) Math.round((area.getMinY() - origen.getY()) * escala);
        int ancho = Math.max(1, (int) Math.round(area.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(area.getHeight() * escala));

        ImpresionCodigoBarras.pegar(destino, x, y, ancho, alto, codigoImpresion);
    }

    /**
     * Fuera de una ventana (por ejemplo en la prueba que compone el ticket) hay
     * que crear una escena y forzar el layout para que los nodos tengan tamaño
     * y se pueda calcular el hueco de las barras.
     */
    private void asegurarLayout() {
        Parent raiz = vbTicket.getParent();
        if (vbTicket.getScene() == null && raiz != null) {
            new Scene(raiz);
        }
        vbTicket.applyCss();
        vbTicket.autosize();
        vbTicket.layout();
    }

    /** Deja solo negro y blanco para que la térmica no tramee los bordes. */
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

    private static PrintService impresoraDeTickets() {
        for (PrintService impresora : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (impresora.getName().regionMatches(
                    true, 0, IMPRESORA_DE_TICKETS, 0, IMPRESORA_DE_TICKETS.length())) {
                return impresora;
            }
        }
        return PrintServiceLookup.lookupDefaultPrintService();
    }

    @FXML
    private void cerrarTicket() {
        Stage stage = (Stage) vbTicket.getScene().getWindow();
        stage.close();
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static void aviso(Alert.AlertType tipo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
