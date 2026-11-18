package com.sapam.inventario.javafx;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Scale;

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

/**
 * Imprime las etiquetas de producto y herramienta en una etiqueta de 50 x 30 mm.
 *
 * La etiqueta se dibuja a 96 dpi, se copia a 203 dpi y se manda por AWT en un
 * hilo aparte. No se usa el diálogo de impresión ni {@code javafx.print}: con
 * la POS-80 el diálogo dejaba el trabajo retenido y los márgenes de 19 mm del
 * driver recortaban la etiqueta.
 *
 * El contenido se mide antes de imprimir y, si hiciera falta, se encoge lo
 * justo para entrar en los 50 x 30 mm. Así una etiqueta nunca se corta: lo que
 * no cabe se reduce, no se pierde.
 */
public final class ImpresionEtiqueta {

    /** La impresora térmica que entrega las etiquetas. */
    private static final String IMPRESORA_DE_ETIQUETAS = "POS-80";

    /** JavaFX mide los nodos en puntos de 96 dpi. */
    private static final double DPI_PANTALLA = 96.0;

    /** La POS-80 imprime a 203 dpi. */
    private static final double DPI_IMPRESORA = 203.0;

    /** Tope de la copia para no inflar la memoria. */
    private static final double ESCALA_MAXIMA_COPIA = 4.0;

    /** Gris por debajo del cual se manda negro; el resto, blanco. */
    private static final int UMBRAL_TINTA = 180;

    /** La etiqueta física que se usa es de 50 x 30 mm. */
    private static final double ANCHO_ETIQUETA_MM = 50.0;
    private static final double ALTO_ETIQUETA_MM = 30.0;

    /**
     * La POS-80 maneja una página de 80 mm de ancho aunque la etiqueta sea de
     * 50 mm; el contenido se centra dentro de esa página.
     */
    private static final double ANCHO_PAGINA_MM = 80.0;

    private static final double MM_A_PT = 72.0 / 25.4;

    /**
     * Ancho del contenido dibujado dentro de la etiqueta, en píxeles de 96 dpi.
     *
     * Son los 48 mm útiles (50 mm menos 1 mm de margen a cada lado). Antes se
     * dejaban 166 px y el código de una herramienta quedaba demasiado apretado:
     * al reducir la imagen para maquetar la etiqueta y volver a escalarla a
     * 203 dpi, los módulos estrechos del Code 128 se perdían y el lector no
     * los veía.
     */
    private static final double ANCHO_CONTENIDO_PX = 181.0;

    /**
     * Alto reservado para las barras.
     *
     * No se usa la proporción 3:1 del generador: en la etiqueta de 30 mm el
     * alto tiene que dejar sitio al nombre, la serie y el código. Con 40 px
     * (unos 10,6 mm) las barras siguen siendo altas para el lector y la
     * etiqueta entera cabe en los 28 mm útiles sin que el trabajo de
     * impresión la encoja.
     */
    private static final double ALTO_CODIGO_PX = 40.0;

    private static final double ANCHO_ETIQUETA_PX = ANCHO_ETIQUETA_MM * DPI_PANTALLA / 25.4;
    private static final double ALTO_ETIQUETA_PX = ALTO_ETIQUETA_MM * DPI_PANTALLA / 25.4;

    /** Evita que dos clics seguidos manden dos etiquetas a la vez. */
    private static volatile boolean imprimiendo;

    /** La etiqueta maquetada y el hueco donde después se pegan las barras. */
    private record EtiquetaPreparada(VBox etiqueta, Region huecoCodigo) {
    }

    private ImpresionEtiqueta() {
    }

    /**
     * Arma y manda una etiqueta.
     *
     * @param codigo       imagen del código de barras ya generada
     * @param nombre       nombre del producto o de la herramienta
     * @param serie        número de serie; puede ser nulo o vacío
     * @param textoCodigo  código que se lee debajo de las barras
     */
    public static void imprimir(BufferedImage codigo, String nombre, String serie, String textoCodigo) {
        if (codigo == null || imprimiendo) {
            return;
        }
        imprimiendo = true;
        try {
            EtiquetaPreparada etiqueta = crearEtiqueta(nombre, serie, textoCodigo);
            double anchoImagenPt = etiqueta.etiqueta().getWidth() * 72.0 / DPI_PANTALLA;
            double altoImagenPt = etiqueta.etiqueta().getHeight() * 72.0 / DPI_PANTALLA;
            RegistroImpresion.anotar("etiqueta: se pide imprimir (mide "
                    + Math.round(etiqueta.etiqueta().getWidth()) + " x "
                    + Math.round(etiqueta.etiqueta().getHeight()) + " px)");

            PrintService impresora = impresoraDeEtiquetas();
            if (impresora == null) {
                imprimiendo = false;
                RegistroImpresion.anotar("etiqueta: no hay ninguna impresora instalada");
                aviso(Alert.AlertType.WARNING, "No se encontró ninguna impresora instalada.");
                return;
            }
            RegistroImpresion.anotar("etiqueta: se imprime en " + impresora.getName());

            BufferedImage imagen = copiar(etiqueta, codigo);
            RegistroImpresion.anotar("etiqueta: copia de " + imagen.getWidth() + " x "
                    + imagen.getHeight() + " px a 203 dpi");

            String nombreJob = "Etiqueta " + (nombre == null || nombre.isBlank() ? "" : nombre);
            Thread hilo = new Thread(() -> imprimirEnSegundoPlano(
                    impresora, imagen, anchoImagenPt, altoImagenPt, nombreJob),
                    "impresion-etiqueta");
            hilo.setDaemon(true);
            hilo.start();
        } catch (RuntimeException fallo) {
            imprimiendo = false;
            RegistroImpresion.anotar("etiqueta: falló al preparar la copia con "
                    + fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            aviso(Alert.AlertType.WARNING, "No se pudo preparar la etiqueta: " + fallo.getMessage());
        }
    }

    /**
     * Compone la etiqueta sin mandarla a la impresora.
     *
     * Se expone para la prueba de regresión: el bitmap final tiene que seguir
     * conteniendo un código que se pueda decodificar. Debe llamarse desde el
     * hilo de JavaFX, igual que {@link #imprimir}.
     */
    public static BufferedImage componer(BufferedImage codigo, String nombre,
                                         String serie, String textoCodigo) {
        EtiquetaPreparada etiqueta = crearEtiqueta(nombre, serie, textoCodigo);
        return copiar(etiqueta, codigo);
    }

    /** Dibuja la etiqueta con el ancho de 50 mm y deja que el alto lo ponga el contenido. */
    private static EtiquetaPreparada crearEtiqueta(String nombre, String serie, String textoCodigo) {
        // Hueco en blanco para las barras. El código se pega después del
        // snapshot, ya rasterizado a resolución de impresora: así no pasa por
        // el reescalado de JavaFX, que era lo que se comía los módulos
        // estrechos de un código de herramienta.
        Region huecoCodigo = new Region();
        huecoCodigo.setPrefSize(ANCHO_CONTENIDO_PX, ALTO_CODIGO_PX);
        huecoCodigo.setMinSize(ANCHO_CONTENIDO_PX, ALTO_CODIGO_PX);
        huecoCodigo.setMaxSize(ANCHO_CONTENIDO_PX, ALTO_CODIGO_PX);
        huecoCodigo.setStyle("-fx-background-color: white;");

        Label lblNombre = new Label(nombre == null ? "" : nombre);
        lblNombre.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
        // Una sola línea: si el nombre es largo se recorta con puntos
        // suspensivos. Dejar que ocupe dos renglones hacía crecer la etiqueta
        // por encima de los 28 mm útiles y la impresión la encogía entera,
        // incluidas las barras.
        lblNombre.setWrapText(false);
        lblNombre.setMaxWidth(ANCHO_CONTENIDO_PX);
        lblNombre.setAlignment(Pos.CENTER);
        lblNombre.setTextAlignment(TextAlignment.CENTER);

        Label lblSerie = null;
        if (serie != null && !serie.isBlank()) {
            lblSerie = new Label(serie);
            lblSerie.setStyle("-fx-font-size: 10px;");
            lblSerie.setMaxWidth(ANCHO_CONTENIDO_PX);
            lblSerie.setAlignment(Pos.CENTER);
            lblSerie.setTextAlignment(TextAlignment.CENTER);
        }

        Label lblCodigo = new Label(textoCodigo == null ? "" : textoCodigo);
        lblCodigo.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        lblCodigo.setMaxWidth(ANCHO_CONTENIDO_PX);
        lblCodigo.setAlignment(Pos.CENTER);
        lblCodigo.setTextAlignment(TextAlignment.CENTER);

        VBox etiqueta = new VBox(2);
        etiqueta.setAlignment(Pos.CENTER);
        etiqueta.setPrefWidth(ANCHO_ETIQUETA_PX);
        etiqueta.setMaxWidth(ANCHO_ETIQUETA_PX);
        etiqueta.setPadding(new Insets(2));
        etiqueta.setStyle("-fx-background-color: white;");
        etiqueta.getChildren().add(huecoCodigo);
        etiqueta.getChildren().add(lblNombre);
        if (lblSerie != null) {
            etiqueta.getChildren().add(lblSerie);
        }
        etiqueta.getChildren().add(lblCodigo);

        // Un Group deja que la etiqueta mida lo que ocupa su contenido. El
        // nombre va en una sola línea para que el alto no pase de los 28 mm
        // útiles y la impresión no tenga que encoger las barras.
        Group raiz = new Group(etiqueta);
        new Scene(raiz, ANCHO_ETIQUETA_PX, ALTO_ETIQUETA_PX);
        etiqueta.applyCss();
        etiqueta.autosize();
        etiqueta.layout();
        return new EtiquetaPreparada(etiqueta, huecoCodigo);
    }

    /** Copia la etiqueta a la resolución de la POS-80, en blanco y negro puro. */
    private static BufferedImage copiar(EtiquetaPreparada preparada, BufferedImage codigo) {
        VBox etiqueta = preparada.etiqueta();
        double escala = Math.min(ESCALA_MAXIMA_COPIA,
                Math.max(1.0, DPI_IMPRESORA / DPI_PANTALLA));
        SnapshotParameters parametros = new SnapshotParameters();
        parametros.setFill(Color.WHITE);
        parametros.setTransform(new Scale(escala, escala));

        int ancho = Math.max(1, (int) Math.ceil(etiqueta.getWidth() * escala));
        int alto = Math.max(1, (int) Math.ceil(etiqueta.getHeight() * escala));
        WritableImage copia = new WritableImage(ancho, alto);
        etiqueta.snapshot(parametros, copia);

        BufferedImage salida = aBuffered(aTintaSolida(copia));
        pegarCodigo(salida, preparada, escala, codigo);
        return salida;
    }

    /**
     * Pega el código de barras en el hueco reservado, a la resolución final.
     *
     * El hueco se localiza dentro de la etiqueta ya maquetada y se pasa a
     * píxeles de impresora con la misma escala que el snapshot. Las barras se
     * dibujan con interpolación de vecino más cercano para que un módulo no se
     * convierta en dos ni desaparezca.
     */
    private static void pegarCodigo(BufferedImage destino,
                                    EtiquetaPreparada preparada,
                                    double escala,
                                    BufferedImage codigo) {
        if (codigo == null) {
            return;
        }
        Point2D origen = preparada.etiqueta().localToScene(0, 0);
        Bounds hueco = preparada.huecoCodigo().localToScene(
                preparada.huecoCodigo().getBoundsInLocal());
        int x = (int) Math.round((hueco.getMinX() - origen.getX()) * escala);
        int y = (int) Math.round((hueco.getMinY() - origen.getY()) * escala);
        int ancho = Math.max(1, (int) Math.round(hueco.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(hueco.getHeight() * escala));

        pegarCodigo(destino, x, y, ancho, alto, codigo);
    }

    /** Pega un código ya rasterizado sin suavizado; separado para poder probarlo. */
    static void pegarCodigo(BufferedImage destino,
                            int x, int y, int ancho, int alto,
                            BufferedImage codigo) {
        ImpresionCodigoBarras.pegar(destino, x, y, ancho, alto, codigo);
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

    private static PrintService impresoraDeEtiquetas() {
        for (PrintService impresora : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (impresora.getName().regionMatches(
                    true, 0, IMPRESORA_DE_ETIQUETAS, 0, IMPRESORA_DE_ETIQUETAS.length())) {
                return impresora;
            }
        }
        return PrintServiceLookup.lookupDefaultPrintService();
    }

    /**
     * Manda la etiqueta con AWT en el hilo de fondo.
     *
     * La página es de 80 x 30 mm porque es el ancho que maneja el driver; el
     * contenido se limita a los 50 x 30 mm de la etiqueta y se centra. Si
     * quedara más alto que 30 mm, se encoge dentro del margen de 1 mm en vez
     * de recortarse.
     */
    private static void imprimirEnSegundoPlano(
            PrintService impresora,
            BufferedImage imagen,
            double anchoImagenPt,
            double altoImagenPt,
            String nombreJob) {
        long inicio = System.nanoTime();
        try {
            PrinterJob trabajo = PrinterJob.getPrinterJob();
            trabajo.setPrintService(impresora);
            trabajo.setJobName(nombreJob);

            PageFormat formato = trabajo.defaultPage();
            formato.setOrientation(PageFormat.PORTRAIT);
            double anchoPaginaPt = ANCHO_PAGINA_MM * MM_A_PT;
            double altoPaginaPt = ALTO_ETIQUETA_MM * MM_A_PT;
            Paper papel = new Paper();
            papel.setSize(anchoPaginaPt, altoPaginaPt);
            papel.setImageableArea(0, 0, anchoPaginaPt, altoPaginaPt);
            formato.setPaper(papel);
            RegistroImpresion.anotar("etiqueta: página " + Math.round(ANCHO_PAGINA_MM)
                    + " x " + Math.round(ALTO_ETIQUETA_MM)
                    + " mm, etiqueta " + Math.round(ANCHO_ETIQUETA_MM)
                    + " x " + Math.round(ALTO_ETIQUETA_MM) + " mm");

            // La etiqueta ya se dibuja con su propio aire dentro de los
            // 50 x 30 mm. No se le resta otro margen aquí: hacerlo obligaba a
            // encogerla un 4 % y ese reescalado volvía a romper el código.
            double anchoContenidoPt = ANCHO_ETIQUETA_MM * MM_A_PT;
            double altoContenidoPt = ALTO_ETIQUETA_MM * MM_A_PT;
            trabajo.setPrintable((grafico, pagina, indice) -> {
                if (indice > 0) {
                    return Printable.NO_SUCH_PAGE;
                }
                double anchoDisponible = Math.min(
                        pagina.getImageableWidth(), anchoContenidoPt);
                double altoDisponible = Math.min(
                        pagina.getImageableHeight(), altoContenidoPt);
                double escala = Math.min(1.0, Math.min(
                        anchoDisponible / anchoImagenPt,
                        altoDisponible / altoImagenPt));
                double anchoDibujo = anchoImagenPt * escala;
                double altoDibujo = altoImagenPt * escala;
                double x = pagina.getImageableX()
                        + (pagina.getImageableWidth() - anchoDibujo) / 2.0;
                double y = pagina.getImageableY()
                        + (pagina.getImageableHeight() - altoDibujo) / 2.0;

                Graphics2D g2 = (Graphics2D) grafico;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(imagen,
                        (int) Math.round(x), (int) Math.round(y),
                        (int) Math.round(anchoDibujo), (int) Math.round(altoDibujo),
                        null);
                return Printable.PAGE_EXISTS;
            }, formato);

            RegistroImpresion.anotar("etiqueta: entrando a job.print");
            trabajo.print();
            RegistroImpresion.anotar("etiqueta: job.print terminó en "
                    + (System.nanoTime() - inicio) / 1_000_000 + " ms");
        } catch (PrinterException | RuntimeException fallo) {
            RegistroImpresion.anotar("etiqueta: job.print falló con "
                    + fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            Platform.runLater(() -> aviso(Alert.AlertType.WARNING,
                    "No se pudo imprimir la etiqueta en " + impresora.getName()
                            + ".\n\nRevisa que el rollo no se haya acabado y que la "
                            + "cola de impresión no esté pausada."));
        } finally {
            imprimiendo = false;
        }
    }

    private static void aviso(Alert.AlertType tipo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
