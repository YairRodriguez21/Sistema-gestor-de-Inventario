package com.sapam.inventario;

import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.UsuarioRepository;
import com.sapam.inventario.service.AuthService;
import com.sapam.inventario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contraseñas en texto plano.
 *
 * Es una decisión del proyecto y no un olvido: quien prepara o revisa este
 * sistema tiene que poder leer en la tabla de usuarios las cuentas y las
 * contraseñas que quedan anotadas en el acta, sin tener que descifrar nada. El
 * precio —un volcado de la base deja todas las cuentas a la vista— queda
 * escrito en {@code SecurityConfig.passwordEncoder()} y aquí se deja también
 * por escrito lo que se decidió, para que nadie lo cambie sin leerlo.
 *
 * Lo que sí tiene que seguir funcionando lo compruebo en estas pruebas:
 *
 * <ul>
 *   <li>lo que se guarda es literalmente lo que se teclea, sin prefijo de
 *       codificador y sin sobresaltos;</li>
 *   <li>el login de la API y el de la ventana de escritorio validan contra la
 *       misma columna, así que no pueden disentir;</li>
 *   <li>editar una cuenta sin escribir contraseña la deja intacta, y cambiarla
 *       deja la vieja inservible.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ContrasenasTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioService usuarioService;
    @Autowired private AuthService authService;

    @Test
    void laContrasenaQueSeGuardaEsLaQueSeTecleo() {
        String email = "clara-" + System.nanoTime() + "@prueba.com";
        Usuario creada = nuevaCuenta(email, "secreta123");

        String enBase = usuarioRepository.findById(creada.getId()).orElseThrow().getPassword();

        assertEquals("secreta123", enBase,
                "queda en claro a propósito: es lo que pide el acta. Si algún día "
                        + "se decide cifrarla, hay que cambiar esta prueba y avisar "
                        + "de que las cuentas dejan de leerse en la tabla");
        assertFalse(enBase.startsWith("{"), "no debe llevar prefijo de codificador");
    }

    @Test
    void elLoginRestValidaLoQueHayEnLaColumna() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@sgia.com\", \"password\": \"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@sgia.com\", \"password\": \"clave-mala\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void laVentanaDeEscritorioValidaLaMismaColumna() {
        String email = "escritorio-" + System.nanoTime() + "@prueba.com";
        nuevaCuenta(email, "ventana2026");

        assertTrue(authService.entrar(email, "ventana2026").isPresent(),
                "la ventana debe entrar con la misma contraseña que la API");
        assertTrue(authService.entrar(email, "otraClave").isEmpty(),
                "una contraseña que no es no debe abrir la ventana");
        assertTrue(authService.entrar(email + ".x", "ventana2026").isEmpty(),
                "una cuenta que no existe tampoco entra");
        assertTrue(authService.entrar(email, null).isEmpty(),
                "sin contraseña no se entra: un null no puede igualar nada");
    }

    @Test
    void editarSinEscribirContrasenaNoLaBorra() {
        String email = "editada-" + System.nanoTime() + "@prueba.com";
        Usuario creada = nuevaCuenta(email, "primeraClave");
        String antes = usuarioRepository.findById(creada.getId()).orElseThrow().getPassword();

        Usuario edicion = new Usuario();
        edicion.setId(creada.getId());
        edicion.setNombre("Nombre cambiado");
        edicion.setEmail(email);
        edicion.setRol(Usuario.Rol.usuario);
        usuarioService.guardar(edicion, null);

        String despues = usuarioRepository.findById(creada.getId()).orElseThrow().getPassword();
        assertEquals(antes, despues,
                "si no se escribe contraseña no se toca: null borraría la columna "
                        + "y la cuenta quedaría sin login");
        assertTrue(authService.entrar(email, "primeraClave").isPresent());
    }

    @Test
    void cambiarLaContrasenaDejaLaViejaInutilizable() {
        String email = "cambio-" + System.nanoTime() + "@prueba.com";
        Usuario creada = nuevaCuenta(email, "claveVieja");

        Usuario cambio = new Usuario();
        cambio.setId(creada.getId());
        cambio.setNombre(creada.getNombre());
        cambio.setEmail(email);
        cambio.setRol(Usuario.Rol.usuario);
        cambio.setPassword("claveNueva");
        usuarioService.guardar(cambio, null);

        assertEquals("claveNueva",
                usuarioRepository.findById(creada.getId()).orElseThrow().getPassword(),
                "la nueva contraseña se guarda igual de clara que la anterior");
        assertTrue(authService.entrar(email, "claveNueva").isPresent());
        assertTrue(authService.entrar(email, "claveVieja").isEmpty(),
                "cambiada la contraseña, la anterior deja de servir");
    }

    // ------------------------------------------------------------- auxiliares

    private Usuario nuevaCuenta(String email, String contrasena) {
        Usuario u = new Usuario();
        u.setNombre("Tester Contraseñas");
        u.setEmail(email);
        u.setPassword(contrasena);
        u.setRol(Usuario.Rol.usuario);
        u.setActivo(true);
        return usuarioService.guardar(u, null);
    }
}
