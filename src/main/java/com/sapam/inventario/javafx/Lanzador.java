package com.sapam.inventario.javafx;

import javafx.application.Application;

/**
 * Punto de entrada para el programa ya empaquetado.
 *
 * Existe porque {@link JavaFxApplication} es una subclase de
 * {@code Application}, y JavaFX se niega a arrancarla si se la pide
 * directamente desde el classpath: sale el aviso
 * {@code "JavaFX runtime components are missing"} y no se abre la ventana.
 *
 * Al meter una clase común por medio que solo llama a
 * {@code Application.launch(JavaFxApplication.class)}, esa comprobación no
 * aplica y el programa arranca igual que con {@code mvn javafx:run}.
 *
 * No es código de negocio: es el pasador de arranque. Ver {@code SGIA.bat}.
 */
public final class Lanzador {

    private Lanzador() {
    }

    public static void main(String[] args) {
        Application.launch(JavaFxApplication.class, args);
    }
}
