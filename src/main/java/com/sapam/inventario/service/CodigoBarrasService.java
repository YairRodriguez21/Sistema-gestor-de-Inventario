package com.sapam.inventario.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.Code128Writer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Genera y lee códigos de barras lineales (Code 128).
 *
 * El lector que se usa es de una sola dimensión, así que la etiqueta tiene que
 * llevar un código lineal. Code 128 es el formato que acepta letras y números,
 * así que sirve igual para el código corto de un producto ({@code 10001}), para
 * el folio de una entrada ({@code ENT-...}) y para el código de una herramienta
 * ({@code HER-...}).
 *
 * La imagen sale apaisada, no cuadrada: las barras van a lo ancho y el alto es
 * una tercera parte, que es la proporción con la que se imprimen las etiquetas
 * y la que lee cualquier lector.
 *
 * El texto que se codifica es el identificador que ya guardan las entidades y
 * que se imprime en la etiqueta, de modo que escanear con el lector USB escribe
 * ese mismo valor en la caja de búsqueda y la app lo encuentra. Es lo que
 * devuelve {@code getCodigoEscaneable()} de producto y herramienta.
 */
@Service
public class CodigoBarrasService {

    /** Ancho por defecto de la imagen, en píxeles. */
    public static final int TAMANO_DEFECTO = 640;

    /** El alto es el ancho dividido entre esta cifra: barras apaisadas. */
    private static final int RELACION_ALTO = 3;

    /** Módulos mínimos que se dibujan a lo ancho antes de escalar. */
    private static final int ALTO_MINIMO = 80;

    /**
     * Genera la imagen del código de barras que representa el texto indicado.
     *
     * @param contenido texto a codificar (no puede ser nulo ni vacío)
     * @param anchoPx   ancho de la imagen; el alto se deduce de la proporción
     */
    public BufferedImage generar(String contenido, int anchoPx) {
        if (contenido == null || contenido.isBlank()) {
            throw new IllegalArgumentException("No se puede generar un código sin contenido");
        }
        if (anchoPx <= 0) {
            anchoPx = TAMANO_DEFECTO;
        }

        try {
            // Módulos mínimos, ya con su zona de silencio a cada lado: es la
            // separación blanca sin la que ningún lector arranca.
            BitMatrix matriz = new Code128Writer()
                    .encode(contenido, BarcodeFormat.CODE_128, 0, 1);
            int modulos = matriz.getWidth();
            int fila = matriz.getHeight() / 2;
            int altoPx = Math.max(ALTO_MINIMO, anchoPx / RELACION_ALTO);

            BufferedImage salida = new BufferedImage(anchoPx, altoPx, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = salida.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, anchoPx, altoPx);

            // Cada módulo se dibuja como un rectángulo entero en vez de escalar
            // la matriz con interpolación: una barra reventada por mitades es
            // lo que hace que un lector 1D deje de leer.
            g.setColor(Color.BLACK);
            for (int x = 0; x < modulos; x++) {
                if (!matriz.get(x, fila)) {
                    continue;
                }
                int desde = (int) Math.round((double) x * anchoPx / modulos);
                int hasta = (int) Math.round((double) (x + 1) * anchoPx / modulos);
                g.fillRect(desde, 0, Math.max(1, hasta - desde), altoPx);
            }
            g.dispose();
            return salida;
        } catch (IllegalArgumentException e) {
            // El contenido no es codificable en Code 128: letras fuera de
            // tabla, caracteres que no son ASCII y cosas parecidas.
            throw new IllegalArgumentException(
                    "El contenido no es válido para un código de barras: " + e.getMessage(), e);
        }
    }

    /** Genera el código y lo devuelve listo para enviarse como imagen PNG. */
    public byte[] png(String contenido, int anchoPx) {
        ByteArrayOutputStream flujo = new ByteArrayOutputStream();
        try {
            ImageIO.write(generar(contenido, anchoPx), "png", flujo);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo convertir el código a PNG", e);
        }
        return flujo.toByteArray();
    }

    /**
     * Lee el texto contenido en una imagen de código de barras.
     * Devuelve vacío si la imagen no contiene un código legible.
     */
    public Optional<String> decodificar(BufferedImage imagen) {
        if (imagen == null) {
            return Optional.empty();
        }
        try {
            BinaryBitmap bitmap = new BinaryBitmap(
                    new HybridBinarizer(new BufferedImageLuminanceSource(imagen)));
            Map<DecodeHintType, Object> pistas = new EnumMap<>(DecodeHintType.class);
            pistas.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            String texto = new MultiFormatReader().decode(bitmap, pistas).getText();
            return Optional.ofNullable(texto);
        } catch (NotFoundException e) {
            // No había ningún código en la imagen
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
