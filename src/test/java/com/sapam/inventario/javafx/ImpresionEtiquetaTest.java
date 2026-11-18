package com.sapam.inventario.javafx;

import com.sapam.inventario.service.CodigoBarrasService;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * La etiqueta impresa tiene que seguir siendo legible después de pegar las
 * barras en el lienzo de 203 dpi.
 *
 * Antes el código se metía en la escena de JavaFX a 166 px y el snapshot lo
 * volvía a escalar; ese ida y vuelta se comía los módulos estrechos de un
 * código de herramienta ({@code HER-} más seis caracteres) y el lector no lo
 * veía, aunque el producto de cinco dígitos sí se leyera.
 */
class ImpresionEtiquetaTest {

    @Test
    void elCodigoDeHerramientaPegadoALaEtiquetaSePuedeLeer() {
        CodigoBarrasService codigos = new CodigoBarrasService();
        BufferedImage codigo = codigos.generar(
                "HER-ABC123", CodigoBarrasService.TAMANO_DEFECTO);

        // Lienzo del tamaño de la etiqueta a 203 dpi. El hueco de las barras
        // ocupa los 48 mm útiles (383 px) y va arriba, como en la etiqueta.
        BufferedImage etiqueta = new BufferedImage(400, 208, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = etiqueta.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, etiqueta.getWidth(), etiqueta.getHeight());
        g.dispose();

        ImpresionEtiqueta.pegarCodigo(etiqueta, 8, 4, 383, 85, codigo);

        assertEquals(Optional.of("HER-ABC123"), codigos.decodificar(etiqueta),
                "el código de una herramienta tiene que seguir leyéndose en la etiqueta");
    }
}
