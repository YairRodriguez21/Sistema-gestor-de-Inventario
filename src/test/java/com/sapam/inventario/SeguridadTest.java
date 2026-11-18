package com.sapam.inventario;

import com.sapam.inventario.config.JwtTokenUtil;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Antes había dos tablas de usuario sin correspondencia: la aplicación de
 * escritorio validaba contra {@code usuarios} y el login REST contra
 * {@code users}, así que un mismo usuario y contraseña no servían en los dos
 * sitios. Ahora todo se lee de {@code usuarios}.
 */
@SpringBootTest
class SeguridadTest {

    @Autowired private UserDetailsService userDetailsService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private JwtTokenUtil jwtTokenUtil;

    private Usuario guardar(String email, Usuario.Rol rol) {
        Usuario u = new Usuario();
        u.setNombre("Tester " + rol.name());
        u.setEmail(email);
        u.setPassword("123456");
        u.setRol(rol);
        u.setActivo(true);
        return usuarioRepository.save(u);
    }

    @Test
    void elLoginRestSeAutenticaContraLaUnicaTablaDeUsuarios() {
        String email = "unica@" + System.nanoTime() + ".com";
        guardar(email, Usuario.Rol.usuario);

        UserDetails detalle = userDetailsService.loadUserByUsername(email);

        assertEquals(email, detalle.getUsername(), "debe leer de usuarios, no de otra tabla");
        assertTrue(detalle.isEnabled(), "los usuarios activos deben quedar habilitados");
        assertTrue(detalle.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_OPERATIVO")),
                "el rol usuario debe mapearse a ROLE_OPERATIVO");
    }

    @Test
    void elRolSaleDeLaBaseDeDatosNoDelTextoDelCorreo() {
        // Contiene "admin" en el correo pero su rol real es consulta: antes el
        // código adivinaba el rol con contains("admin") y devolvía ADMIN.
        String email = "admin-cuenta-" + System.nanoTime() + "@prueba.com";
        guardar(email, Usuario.Rol.consulta);

        UserDetails detalle = userDetailsService.loadUserByUsername(email);

        assertTrue(detalle.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_CONSULTA")),
                "el rol debe salir de la columna usuarios.rol, no del correo");
        assertFalse(detalle.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")),
                "no debe inferirse ADMIN por contener 'admin' en el correo");
    }

    @Test
    void elRolDeConsultaEstaAdmitido() {
        assertEquals("ADMIN", Usuario.Rol.admin.rolSpring());
        assertEquals("OPERATIVO", Usuario.Rol.usuario.rolSpring());
        assertEquals("CONSULTA", Usuario.Rol.consulta.rolSpring());
    }

    @Test
    void losRolesDelTokenSeLeenDeLaBase() {
        String email = "token-" + System.nanoTime() + "@prueba.com";
        guardar(email, Usuario.Rol.usuario);

        String[] roles = jwtTokenUtil.rolesDesdeBase(email);
        assertArrayEquals(new String[]{"OPERATIVO"}, roles,
                "el token debe llevar el rol que consta en la base");
    }

    @Test
    void elUsuarioInactivoSeDeshabilita() {
        String email = "inactivo-" + System.nanoTime() + "@prueba.com";
        Usuario u = guardar(email, Usuario.Rol.usuario);
        u.setActivo(false);
        usuarioRepository.save(u);

        assertFalse(userDetailsService.loadUserByUsername(email).isEnabled(),
                "un usuario desactivado no debe poder iniciar sesión");
    }

    @Test
    void elUsuarioDesconocidoFallaConMensajeClaro() {
        var excepcion = assertThrows(org.springframework.security.core.userdetails
                .UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("no-existe@" + System.nanoTime() + ".com"));
        assertTrue(excepcion.getMessage().contains("Usuario no encontrado"));
    }
}
