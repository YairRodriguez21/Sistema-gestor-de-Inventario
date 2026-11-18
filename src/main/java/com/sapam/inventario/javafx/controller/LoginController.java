package com.sapam.inventario.javafx.controller;

import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.service.AuthService;
import com.sapam.inventario.service.BitacoraService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import java.util.Optional;

@Controller
public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @Autowired
    private AuthService authService;

    @Autowired
    private BitacoraService bitacoraService;

    @FXML
    private void iniciarSesion() {
        String email = usernameField.getText();
        String password = passwordField.getText();

        // Un solo sitio decide si la contraseña es correcta (AuthService), para
        // que entrar por la ventana y entrar por la API no puedan disentir.
        Optional<Usuario> usuarioOpt = authService.entrar(email, password);

        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();

            // Cuenta desactivada por el administrador -> no entra
            if (Boolean.FALSE.equals(usuario.getActivo())) {
                mostrarError("Cuenta desactivada", "La cuenta está inactiva. Contacte al administrador.");
                return;
            }

            // Dejar registro del inicio de sesión en la bitácora
            bitacoraService.registrar(BitacoraService.LOGIN, "SEGURIDAD",
                    "Email: " + email + " | Rol: " + usuario.getRol(),
                    "Inicio de sesión correcto", usuario);

            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/main.fxml"));
                loader.setControllerFactory(com.sapam.inventario.javafx.JavaFxApplication.getContext()::getBean);
                Parent root = loader.load();
                
                MainController mainController = loader.getController();
                mainController.setUsuarioActual(usuario);
                
                Stage stage = (Stage) usernameField.getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.setTitle("SAPAM - Inventario");
                stage.setMaximized(true);
                
            } catch (Exception e) {
                e.printStackTrace();
                mostrarError("Error", "Error al cargar la aplicación");
            }
        } else {
            mostrarError("Error de Login", "Usuario o contraseña incorrectos");
        }
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}