package com.sapam.inventario.service;

import com.sapam.inventario.entity.Producto;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.ProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class ProductoService {

    /** Primer número de la serie interna de códigos de barras. */
    private static final int PRIMER_CODIGO = 10001;

    /**
     * Las cinco secciones del inventario, en el orden en que se ofrecen en la
     * pantalla. Es el único sitio donde se decidieron: si mañana entra una,
     * aquí y en el botón de la fila de secciones, y en ningún otro.
     */
    public static final List<String> SECCIONES = List.of(
            "Papelería", "Construcción", "Vehículos", "Uniformes", "Ferretería");

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private BitacoraService bitacoraService;

    /** Usuario logueado en la sesión JavaFX; lo setea MainController al abrir. */
    private static Usuario usuarioSesion;

    public static void setUsuarioSesion(Usuario usuario) {
        usuarioSesion = usuario;
    }

    /**
     * El código de barras ya no lo teclea el usuario: el sistema lo asigna en la
     * serie interna que arranca en {@value #PRIMER_CODIGO}. Un cliente REST sí
     * puede traer el suyo, así que solo se genera cuando viene vacío.
     */
    private void asignarCodigoSiFalta(Producto producto) {
        if (producto.getCodigoBarras() != null && !producto.getCodigoBarras().isBlank()) {
            producto.setCodigoBarras(producto.getCodigoBarras().trim());
            return;
        }
        producto.setCodigoBarras(siguienteCodigoLibre());
    }

    /**
     * Siguiente número libre de la serie interna.
     *
     * Solo cuenta los códigos que pertenecen a ella —cinco dígitos que empiezan
     * por 1— para que un código ajeno, como el 99901 de una carga vieja, no
     * despegue el correlativo. El bucle final salta cualquier hueco que
     * quedara tomado.
     */
    private String siguienteCodigoLibre() {
        int correlativo = PRIMER_CODIGO;
        for (Producto p : productoRepository.findAll()) {
            String codigo = p.getCodigoBarras();
            if (codigo != null && codigo.matches("1\\d{4}")) {
                correlativo = Math.max(correlativo, Integer.parseInt(codigo) + 1);
            }
        }
        String candidato = String.valueOf(correlativo);
        while (productoRepository.findByCodigoBarras(candidato).isPresent()) {
            candidato = String.valueOf(++correlativo);
        }
        return candidato;
    }

    /**
     * Valida que el código de barras no esté duplicado.
     * El dominio es la BD (constraint unique), pero así el usuario ve un
     * mensaje claro en vez de un error SQL crudo tipo "Duplicate entry ...".
     */
    private void validarDuplicado(Producto producto) {
        if (producto.getCodigoBarras() == null || producto.getCodigoBarras().isBlank()) {
            throw new IllegalArgumentException("El código de barras es obligatorio");
        }
        if (producto.getNombre() == null || producto.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio");
        }

        Optional<Producto> existente = productoRepository.findByCodigoBarras(producto.getCodigoBarras());
        if (existente.isPresent() && !existente.get().getId().equals(producto.getId())) {
            throw new IllegalArgumentException(
                "Ya existe un producto con el código " + producto.getCodigoBarras()
                + " (\"" + existente.get().getNombre() + "\"). No se permiten productos duplicados.");
        }
    }

    public List<Producto> listarTodos() {
        return productoRepository.findAll();
    }

    /**
     * Los que siguen de alta, que es lo que debe ver quien está manejando el
     * inventario. Los dados de baja no salen aquí, pero siguen en la base y se
     * traen con {@link #listarTodos()} para que el kardex y la bitácora no
     * pierdan el rastro de lo que pasó con ellos.
     */
    public List<Producto> listarActivos() {
        return productoRepository.findActivos();
    }

    public Optional<Producto> buscarPorId(Integer id) {
        return productoRepository.findById(id);
    }

    public Optional<Producto> buscarPorCodigoBarras(String codigo) {
        return productoRepository.findByCodigoBarras(codigo);
    }

    /** Busca por el código escaneado (lector USB). */
    public Optional<Producto> buscarPorCodigoInterno(String codigoInterno) {
        return productoRepository.findByCodigoInterno(codigoInterno);
    }

    /**
     * Busca por código de barras o, si no coincide, por identificador interno.
     * Es lo que se usa al escanear: el lector escribe el código de la etiqueta.
     */
    public Optional<Producto> buscarPorCodigoBarrasOInterno(String texto) {
        Optional<Producto> porBarras = productoRepository.findByCodigoBarras(texto);
        return porBarras.isPresent() ? porBarras : productoRepository.findByCodigoInterno(texto);
    }

    /**
     * El inventario por secciones: se queda con los productos de la elegida y
     * con ninguno si esa sección no tiene nada. El filtro vacío —el del
     * botón "Todo"— deja pasar todo, incluidos los productos que todavía no
     * tienen sección, que no deben desaparecer del inventario.
     *
     * Sin estado y sin tocar la base, para poder aplicarla desde la ventana
     * y comprobarla desde una prueba.
     */
    public static List<Producto> deSeccion(List<Producto> productos, String seccion) {
        if (seccion == null || seccion.isBlank()) {
            return productos;
        }
        List<Producto> filtrados = new java.util.ArrayList<>();
        for (Producto producto : productos) {
            if (seccion.equals(producto.getSeccion())) {
                filtrados.add(producto);
            }
        }
        return filtrados;
    }

    public Producto guardar(Producto producto) {
        asignarCodigoSiFalta(producto);
        validarDuplicado(producto);
        normalizarSeccion(producto);
        boolean esNuevo = producto.getId() == null;
        Producto guardado = productoRepository.save(producto);

        bitacoraService.registrar(
                esNuevo ? BitacoraService.CREAR_PRODUCTO : BitacoraService.MODIFICAR_PRODUCTO,
                "INVENTARIO",
                "Código: " + guardado.getCodigoBarras()
                        + " | Nombre: " + guardado.getNombre()
                        + " | Sección: " + (guardado.getSeccion() == null
                                ? "(sin sección)" : guardado.getSeccion())
                        + " | Existencia: " + guardado.getExistenciaActual(),
                esNuevo ? "Alta de producto" : "Modificación de producto",
                usuarioSesion);
        return guardado;
    }

    /**
     * Deja la sección como está en la pantalla: sin espacios sobrantes y, si
     * viene en blanco, a {@code null} para no guardar un hueco con rayas.
     *
     * Una sección que no es de las cinco no pasa: el inventario solo se
     * ordena por las que ofrece la pantalla, y un valor inventado no llegaría
     * a ningún botón. En cambio sí se admite vacío —un producto creado por la
     * API o de antes de las secciones—, que entonces solo sale en el inventario
     * completo, nunca filtrado.
     */
    private void normalizarSeccion(Producto producto) {
        String seccion = producto.getSeccion();
        if (seccion == null || seccion.isBlank()) {
            producto.setSeccion(null);
            return;
        }
        seccion = seccion.trim();
        if (!SECCIONES.contains(seccion)) {
            throw new IllegalArgumentException(
                    "La sección \"" + seccion + "\" no es una de las del inventario: "
                    + String.join(", ", SECCIONES) + ".");
        }
        producto.setSeccion(seccion);
    }

    /**
     * Baja lógica: el producto no se borra de la BD para conservar la
     * trazabilidad del kardex y de la bitácora (requisito del sistema).
     */
    public void eliminar(Integer id) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        producto.setEstado("BAJAS");
        productoRepository.save(producto);

        bitacoraService.registrar(BitacoraService.ELIMINAR_PRODUCTO, "INVENTARIO",
                "Código: " + producto.getCodigoBarras() + " | Nombre: " + producto.getNombre(),
                "Baja lógica (el registro se conserva para auditoría)",
                usuarioSesion);
    }

    /** Los que están por debajo de 5 piezas, sin contar los dados de baja. */
    public List<Producto> productosConStockBajo() {
        return productoRepository.findByExistenciaActualLessThanEqual(5).stream()
                .filter(p -> p.getEstado() == null || !"BAJAS".equals(p.getEstado()))
                .toList();
    }
}