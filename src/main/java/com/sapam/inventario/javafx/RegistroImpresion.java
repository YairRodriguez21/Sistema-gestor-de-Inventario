package com.sapam.inventario.javafx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Deja constancia de cada intento de impresión en {@code sgia-impresion.log}.
 *
 * El registro existe porque el fallo de este módulo no era un error de Java
 * sino una caída nativa de la JVM en {@code glass.dll}, y una caída así no
 * deja rastro en consola: solo el archivo {@code hs_err_pid*.log}, que no
 * dice en qué paso del programa estaba. Cruzando las marcas de tiempo de
 * aquí con el registro de impresión de Windows se ve exactamente dónde se
 * atasca el trabajo.
 *
 * Es también la primera cosa que hay que mirar cuando el programa falla en
 * otra computadora: está escrito en el directorio de trabajo, así que si
 * {@code SGIA.bat} se lanza desde su carpeta, el archivo queda al lado.
 *
 * Anotar nunca puede ser motivo de que falle una impresión: si el directorio
 * no admite escribir —una carpeta de solo lectura, por ejemplo— se sigue
 * imprimiendo igual y ya.
 */
public final class RegistroImpresion {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss.SSS");

    private static final Path ARCHIVO = Path.of("sgia-impresion.log");

    private RegistroImpresion() {
    }

    /** Escribe una línea con hora al final del archivo de registro. */
    public static void anotar(String mensaje) {
        try {
            Files.writeString(ARCHIVO,
                    LocalDateTime.now().format(FECHA) + "  " + mensaje
                            + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | SecurityException ignorada) {
            // Ver arriba: anotar es auxiliar, la impresión va primero.
        }
    }
}
