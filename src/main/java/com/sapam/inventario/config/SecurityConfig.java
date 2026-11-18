package com.sapam.inventario.config;

import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final UsuarioRepository usuarioRepository;

    public SecurityConfig(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Codificador de contraseñas: ninguno, texto plano.
     *
     * Es una decisión del proyecto y no un descuido: quien prepara o revisa
     * este sistema tiene que poder leer en la tabla de usuarios las cuentas y
     * las contraseñas que quedan anotadas en el acta, sin tener que
     * descifrarlas. El precio se anota también aquí, para que nadie lo pase
     * por alto: un volcado de la base deja todas las cuentas a la vista, así
     * que no conviene reutilizar esas contraseñas en ningún otro sistema.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }

    // Spring Security 6 no expone AuthenticationManager como bean por defecto
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Si la base está sin las cuentas de rol, las crea en {@code usuarios}.
     *
     * Idempotente: solo inserta cuando el correo aún no existe, así nunca toca
     * las cuentas que ya haya dado de alta el usuario. Antes se sembraban en la
     * tabla {@code users}, que era la que usaba el login REST mientras el de
     * escritorio usaba {@code usuarios}: un mismo usuario no entraba en ambos.
     */
    @Bean
    public CommandLineRunner initDatabase() {
        return args -> {
            // Las cuentas sembradas nacen en texto plano, como todo lo demás.
            String password = "123456";
            sembrar("admin@sgia.com", password, "Administrador del Sistema", Usuario.Rol.admin);
            sembrar("operativo@sgia.com", password, "Usuario Operativo", Usuario.Rol.usuario);
            sembrar("consulta@sgia.com", password, "Usuario de Consulta", Usuario.Rol.consulta);
        };
    }

    private void sembrar(String email, String password, String nombre, Usuario.Rol rol) {
        if (usuarioRepository.findByEmail(email).isPresent()) {
            return;
        }
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setPassword(password);
        usuario.setNombre(nombre);
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return email -> {
            Usuario usuario = usuarioRepository.findByEmail(email)
                    .orElseThrow(() -> new org.springframework.security.core.userdetails
                            .UsernameNotFoundException("Usuario no encontrado: " + email));

            // El rol sale de la base de datos, no del texto del correo.
            String rol = usuario.getRol() == null
                    ? Usuario.Rol.consulta.rolSpring()
                    : usuario.getRol().rolSpring();

            return org.springframework.security.core.userdetails.User
                    .withUsername(usuario.getEmail())
                    .password(usuario.getPassword())
                    .roles(rol)
                    .disabled(!Boolean.TRUE.equals(usuario.getActivo()))
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Token JWT en el header Authorization
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            // Sin token válido -> 401; con token pero sin permiso -> 403
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

            .authorizeHttpRequests(auth -> auth
                // Rutas públicas
                .requestMatchers(
                    "/api/auth/**",
                    "/error",
                    "/api/productos/codigo-barras/**",
                    "/api/entradas/codigo-barras/**",
                    "/api/salidas/codigo-barras/**",
                    "/api/herramientas/codigo-barras/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                
                // Lo que está admitido a varios roles y no a todos.
                //
                // Los códigos de barras quedaron arriba en permitAll —el lector
                // USB los pide sin sesión— y se repiten aquí por si alguna vez
                // dejan de ser públicos: no deben caer en la regla de
                // Administrador de abajo.
                //
                // Los 13 reportes son de solo lectura y los genera tanto
                // Consulta como el operativo, que es quien pide saber qué hay
                // en almacén. Va antes del resto para no ser sombreado por
                // ADMIN.
                .requestMatchers(
                    "/api/productos/codigo-barras/**",
                    "/api/entradas/codigo-barras/**",
                    "/api/salidas/codigo-barras/**",
                    "/api/herramientas/codigo-barras/**",
                    "/api/reportes/**"
                ).hasAnyRole("CONSULTA", "OPERATIVO", "ADMIN")
                
                // Lo que lee cualquiera con sesión, y lo que no.
                //
                // El kardex es la trazabilidad de un producto: lo mira el rol
                // Consulta y también el operativo, que necesita ver qué pasó
                // con la existencia antes de dar una salida.
                //
                // La bitácora, en cambio, no es de operativo. Es la pantalla
                // de quién hizo qué, y quien solo mueve almacén no tiene por
                // qué revisarla: la leen Consulta y Administrador. Ocultar el
                // botón de la ventana evita la duda; esta regla evita que se
                // lea por fuera con un token de operativo.
                .requestMatchers("/api/kardex/**")
                    .hasAnyRole("CONSULTA", "OPERATIVO", "ADMIN")
                .requestMatchers("/api/auditoria/**")
                    .hasAnyRole("CONSULTA", "ADMIN")

                // GET productos para todos
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/productos")
                    .hasAnyRole("CONSULTA", "OPERATIVO", "ADMIN")
                
                // ROL OPERATIVO
                .requestMatchers(
                    // Registrar una entrada es trabajo del operativo: así lo
                    // permite también la ventana de escritorio (botón Entrada).
                    "/api/entradas/**",
                    "/api/salidas/**",
                    "/api/herramientas/prestamo",
                    "/api/herramientas/devolver",
                    "/api/prestamos/activos"
                ).hasAnyRole("OPERATIVO", "ADMIN")
                // Leer también: antes de prestar hay que ver la ficha.
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                        "/api/herramientas", "/api/herramientas/{id}")
                    .hasAnyRole("OPERATIVO", "ADMIN")

                // El alta de herramientas no está en ningún matcher de arriba
                // y por eso cae en la regla de Administrador de abajo: meter
                // piezas al catálogo es cosa de quien lo administra, no de
                // quien se las lleva a usar. Igual que en la ventana, donde el
                // botón "+ Nueva" solo aparece para administrador.
                
                // ROL ADMIN (debe ir al final)
                //
                // Kardex, auditoría y reportes ya entraron arriba con sus
                // reglas: aquí solo queda lo que es exclusivo de
                // Administrador —las cuentas, el catálogo y el resto de
                // operaciones—.
                .requestMatchers(
                    "/api/usuarios/**",
                    "/api/productos/**",
                    "/api/entradas/**",
                    "/api/salidas/**",
                    "/api/herramientas/**",
                    "/api/prestamos/**"
                ).hasRole("ADMIN")
                
                .anyRequest().authenticated()
            );

        return http.build();
    }
}