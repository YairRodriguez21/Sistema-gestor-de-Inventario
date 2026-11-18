package com.sapam.inventario.javafx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import com.sapam.inventario.InventarioApplication;

/**
 * Lanzador de la aplicación de escritorio.
 *
 * No lleva {@code @SpringBootApplication}: la clase de aplicación es única
 * ({@link InventarioApplication}) y de aquí solo se arranca. Tenía dos, y cada
 * una imponía su propia configuración de escaneo sobre el mismo paquete.
 */
public class JavaFxApplication extends Application {

    private static ConfigurableApplicationContext context;

    /** Para que las dos vías de salida no apaguen el programa dos veces. */
    private static volatile boolean saliendo = false;

    @Override
    public void init() {
        // Los argumentos entran por JavaFX, no por SpringApplication: si aquí
        // no se pasan, todo lo que se teclee al abrir el programa se pierde y
        // la aplicación se conecta siempre a la base que dice el
        // application.properties.
        //
        // Es lo que hace que una misma carpeta sirva para el servidor de la
        // oficina y para el del almacén: sin esto, mandarle
        // --spring.datasource.url=... a SGIA.bat no servía de nada.
        context = SpringApplication.run(InventarioApplication.class,
                getParameters().getRaw().toArray(new String[0]));
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/login.fxml"));
        loader.setControllerFactory(context::getBean);
        Parent root = loader.load();

        Scene scene = new Scene(root);
        primaryStage.setTitle("SGIA");
        primaryStage.setScene(scene);

        // La X no se limita a cerrar la ventana: ver salir(). Sin esto el
        // programa se quedaba vivo sin ventana, con el puerto ocupado.
        primaryStage.setOnCloseRequest(evento -> salir());

        primaryStage.show();
    }

    @Override
    public void stop() {
        salir();
    }

    /**
     * Apaga el programa de verdad, no solo su ventana.
     *
     * Cerrar el contexto y dejar que la JVM decida por su cuenta no basta, y
     * es lo que venía pasando: al pulsar la X la ventana desaparecía pero el
     * proceso seguía corriendo sin nada en pantalla. Tomcat y el pool de
     * conexiones se quedan con hilos que no son daemon, y la JVM no termina
     * nunca mientras exista uno. El resultado era un SGIA invisible que
     * seguía atado al puerto 8080, de modo que el siguiente arranque no
     * levantaba la API porque otro seguía adentro — y como el arranque va en
     * {@code javaw}, ni siquiera se veía el error.
     *
     * {@code System.exit(0)} es lo que remata esos hilos. Por eso se llama
     * tanto desde el botón de cierre como desde {@link #stop()}: así da igual
     * por cuál de las dos vías llegue el cierre.
     */
    public static void salir() {
        if (saliendo) {
            return;
        }
        saliendo = true;

        // Queda escrito por qué y cuándo se apaga: si el registro de
        // impresión se corta en "entrando a printPage" y esta línea aparece
        // después, el usuario cerró el programa con un trabajo sin terminar.
        RegistroImpresion.anotar("cierre: salir() llamado");

        if (context != null) {
            try {
                SpringApplication.exit(context);
            } catch (Exception ignorada) {
                // Nada de lo que falle al apagar puede dejar el programa
                // colgado sin ventana y con el puerto tomado.
            }
        }

        Platform.exit();
        System.exit(0);
    }

    public static ConfigurableApplicationContext getContext() {
        return context;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
