package com.sapam.inventario.javafx;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Pega un código de barras ya rasterizado en el bitmap final de una etiqueta o
 * un ticket.
 *
 * Es el último paso del armado: el código no pasa por el reescalado de JavaFX,
 * que fue lo que borró los módulos estrechos de las etiquetas de herramienta.
 * Se dibuja con vecino más cercano para que un módulo no se convierta en dos ni
 * desaparezca.
 */
public final class ImpresionCodigoBarras {

    private ImpresionCodigoBarras() {
    }

    public static void pegar(BufferedImage destino,
                             int x, int y, int ancho, int alto,
                             BufferedImage codigo) {
        if (destino == null || codigo == null) {
            return;
        }
        Graphics2D g = destino.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(codigo, x, y, ancho, alto, null);
        g.dispose();
    }
}
