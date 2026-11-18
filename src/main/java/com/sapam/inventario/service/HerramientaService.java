package com.sapam.inventario.service;

import com.sapam.inventario.entity.Herramienta;
import com.sapam.inventario.entity.PrestamoHerramienta;
import com.sapam.inventario.entity.Usuario;
import com.sapam.inventario.repository.HerramientaRepository;
import com.sapam.inventario.repository.PrestamoHerramientaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Herramientas: alta, edición, baja, préstamo y devolución.
 *
 * Dos reglas sostienen el módulo:
 *
 * <ul>
 *   <li>Una herramienta solo puede tener un préstamo abierto a la vez: mientras
 *       no se devuelva no se vuelve a prestar ni se puede dar de baja, para que
 *       nunca haya dos responsables distintos señalados para el mismo bien.</li>
 *   <li>Quién se la lleva se anota como <em>texto libre</em> —igual que quien
 *       recibe una salida—, porque el trabajador que recibe una herramienta casi
 *       nunca tiene cuenta en el sistema. Quién la entrega es la cuenta con la
 *       sesión abierta, y los dos quedan en la bitácora.</li>
 * </ul>
 */
@Service
public class HerramientaService {

    /** Estados físicos que reconoce la ficha de una herramienta. */
    public static final List<String> ESTADOS_FISICOS =
            List.of("NUEVO", "BUENO", "REGULAR", "MALO", "REPARADO");

    /** Valor de {@code herramientas.estado} para una herramienta dada de baja. */
    public static final String BAJA = "BAJAS";

    @Autowired
    private HerramientaRepository herramientaRepository;

    @Autowired
    private PrestamoHerramientaRepository prestamoRepository;

    @Autowired
    private BitacoraService bitacoraService;

    /**
     * Alta de una herramienta.
     *
     * El código no lo teclea nadie: lo genera el sistema y no se expone en el
     * formulario, igual que el código de barras de los productos. Es el que se
     * imprime en la etiqueta y el que escribe el lector USB en la caja de
     * búsqueda.
     */
    @Transactional
    public Herramienta alta(Herramienta herramienta, Usuario usuarioSesion) {
        if (herramienta.getId() != null) {
            throw new IllegalArgumentException(
                    "Esta herramienta ya existe: usa la edición para cambiarla");
        }

        validar(herramienta);

        if (herramienta.getCodigoBarras() == null || herramienta.getCodigoBarras().isBlank()) {
            herramienta.setCodigoBarras(siguienteCodigoLibre());
        }
        if (herramienta.getFechaRegistro() == null) {
            herramienta.setFechaRegistro(LocalDateTime.now());
        }
        // Quién dio de alta: queda en la ficha y en la bitácora.
        if (herramienta.getUsuarioRegistro() == null) {
            herramienta.setUsuarioRegistro(usuarioSesion);
        }
        if (herramienta.getEstadoFisico() == null || herramienta.getEstadoFisico().isBlank()) {
            herramienta.setEstadoFisico("NUEVO");
        }
        herramienta.setEstado("ACTIVO");

        Herramienta guardada = herramientaRepository.save(herramienta);

        bitacoraService.registrar(
                BitacoraService.CREAR_HERRAMIENTA,
                "HERRAMIENTAS",
                "Código: " + guardada.getCodigoBarras()
                        + " | Nombre: " + guardada.getNombre()
                        + " | Estado: " + guardada.getEstadoFisico()
                        + (guardada.getNumeroSerie() == null || guardada.getNumeroSerie().isBlank()
                                ? "" : " | No. serie: " + guardada.getNumeroSerie()),
                "Alta de herramienta",
                usuarioSesion);

        return guardada;
    }

    /**
     * Corrige los datos de una herramienta ya dada de alta.
     *
     * El código y la fecha de registro no cambian: son el identificador que
     * lleva la etiqueta impresa, y rehacerlo dejaría esa etiqueta sin leerse.
     */
    @Transactional
    public Herramienta modificar(Herramienta herramienta, Usuario usuarioSesion) {
        if (herramienta.getId() == null) {
            throw new IllegalArgumentException("La herramienta no existe todavía");
        }

        validar(herramienta);

        Herramienta previa = herramientaRepository.findById(herramienta.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Herramienta no encontrada: " + herramienta.getId()));

        herramienta.setCodigoBarras(previa.getCodigoBarras());
        herramienta.setFechaRegistro(previa.getFechaRegistro());
        herramienta.setEstado(previa.getEstado());
        // Quién dio de alta no se toca al editar: es un dato histórico, y si
        // se guardara tal cual llegaría vacío desde el formulario y se perdería.
        herramienta.setUsuarioRegistro(previa.getUsuarioRegistro());
        // La fecha de vuelta no se edita aquí: la pone y la cierra el préstamo.
        herramienta.setFechaEstimadaDevolucion(previa.getFechaEstimadaDevolucion());

        Herramienta guardada = herramientaRepository.save(herramienta);

        bitacoraService.registrar(
                BitacoraService.MODIFICAR_HERRAMIENTA,
                "HERRAMIENTAS",
                "Código: " + guardada.getCodigoBarras()
                        + " | Nombre: " + guardada.getNombre()
                        + " | Estado: " + guardada.getEstadoFisico()
                        + (guardada.getAreaResponsable() == null
                                ? "" : " | Área: " + guardada.getAreaResponsable()),
                "Edición de herramienta",
                usuarioSesion);

        return guardada;
    }

    private void validar(Herramienta herramienta) {
        if (herramienta.getNombre() == null || herramienta.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre de la herramienta es obligatorio");
        }
        herramienta.setNombre(herramienta.getNombre().trim());
        if (herramienta.getNumeroSerie() != null) {
            herramienta.setNumeroSerie(herramienta.getNumeroSerie().trim());
        }
        if (herramienta.getEstadoFisico() == null || herramienta.getEstadoFisico().isBlank()) {
            herramienta.setEstadoFisico("NUEVO");
        }
    }

    /**
     * Baja lógica, igual que en productos: la fila no se borra para conservar
     * la trazabilidad de sus préstamos y de la bitácora.
     *
     * Una herramienta con el préstamo abierto no se puede dar de baja: está con
     * alguien y esa persona tiene que devolverla primero.
     */
    @Transactional
    public Herramienta baja(Long id, Usuario usuarioSesion) {
        Herramienta herramienta = herramientaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Herramienta no encontrada: " + id));

        if (BAJA.equals(herramienta.getEstado())) {
            throw new IllegalArgumentException("La herramienta ya está dada de baja");
        }

        Optional<PrestamoHerramienta> abierta = prestamoActivo(id);
        if (abierta.isPresent()) {
            throw new IllegalArgumentException("La herramienta está prestada a "
                    + abierta.get().getPersonaRecibe()
                    + " desde el " + abierta.get().getFechaEntrega().toLocalDate()
                    + ". Hay que registrar su devolución antes de darla de baja.");
        }

        herramienta.setEstado(BAJA);
        Herramienta guardada = herramientaRepository.save(herramienta);

        bitacoraService.registrar(
                BitacoraService.BAJA_HERRAMIENTA,
                "HERRAMIENTAS",
                "Código: " + guardada.getCodigoBarras() + " | Nombre: " + guardada.getNombre(),
                "Baja lógica (el registro se conserva para auditoría)",
                usuarioSesion);

        return guardada;
    }

    /**
     * Código único de la etiqueta, al estilo de los eventos de la bitácora
     * ({@code AUD-...}): uno que no se repite ni se reutiliza.
     *
     * Antes era {@code HER-} más un UUID, es decir 40 caracteres: en código de
     * barras eso ocupa media etiqueta y el lector no lo agarra. Con
     * {@code HER-} más seis caracteres la barra cabe sobrada, y el sorteo se
     * repite hasta dar con uno libre, así que la unicidad queda igual.
     */
    private String siguienteCodigoLibre() {
        String candidato;
        do {
            candidato = candidatoCodigo();
        } while (herramientaRepository.findByCodigoBarras(candidato).isPresent());
        return candidato;
    }

    /**
     * Un candidato a código de etiqueta: {@code HER-} más seis caracteres.
     *
     * Público y estático para que el relleno de códigos antiguos genere la
     * misma forma que el alta y no acabe con dos clases definiendo el formato.
     */
    public static String candidatoCodigo() {
        return "HER-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }

    /** Todo lo que sigue de alta: las dadas de baja no aparecen. */
    @Transactional(readOnly = true)
    public List<Herramienta> listarTodas() {
        return herramientaRepository.findByEstadoNot(BAJA);
    }

    /**
     * Búsqueda por código, por número de serie o por nombre. El lector USB
     * escribe el código de la etiqueta en la caja de búsqueda, así que un
     * código completo encuentra su ficha.
     *
     * El número de serie también entra: es el que va escrito en la herramienta
     * y a veces es lo único que se tiene a la vista. Puede repetirse (el campo
     * es opcional), por eso aquí devuelve lista y no una sola ficha.
     */
    @Transactional(readOnly = true)
    public List<Herramienta> buscar(String texto) {
        if (texto == null || texto.isBlank()) {
            return listarTodas();
        }
        String termino = texto.trim();
        Optional<Herramienta> porCodigo = herramientaRepository.findByCodigoBarras(termino);
        if (porCodigo.isPresent()) {
            return BAJA.equals(porCodigo.get().getEstado()) ? List.of() : List.of(porCodigo.get());
        }
        List<Herramienta> porSerie = herramientaRepository.findByNumeroSerieIgnoreCase(termino)
                .stream()
                .filter(h -> !BAJA.equals(h.getEstado()))
                .toList();
        if (!porSerie.isEmpty()) {
            return porSerie;
        }
        return herramientaRepository.findByNombreContainingIgnoreCase(termino).stream()
                .filter(h -> !BAJA.equals(h.getEstado()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Herramienta> porId(Long id) {
        return herramientaRepository.findById(id);
    }

    /** Préstamo abierto de una herramienta, si alguien la tiene en este momento. */
    @Transactional(readOnly = true)
    public Optional<PrestamoHerramienta> prestamoActivo(Long herramientaId) {
        List<PrestamoHerramienta> abiertos =
                prestamoRepository.findByHerramientaIdAndFechaDevolucionIsNullOrderByIdDesc(herramientaId);
        return abiertos.isEmpty() ? Optional.empty() : Optional.of(abiertos.get(0));
    }

    /** Todos los préstamos sin devolver, el más urgente primero. */
    @Transactional(readOnly = true)
    public List<PrestamoHerramienta> prestamosActivos() {
        return prestamoRepository.findByFechaDevolucionIsNullOrderByFechaEstimadaDevolucionAsc();
    }

    /** Todo lo que ha pasado: los devueltos también, que es la trazabilidad. */
    @Transactional(readOnly = true)
    public List<PrestamoHerramienta> prestamos() {
        return prestamoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<PrestamoHerramienta> prestamoPorId(Long id) {
        return prestamoRepository.findById(id);
    }

    /**
     * Busca un préstamo por el folio que va impreso en su ticket. Es lo que
     * permite devolver una herramienta escaneando el papel que se entregó al
     * prestarla, sin tener que localizarla antes en la tabla.
     */
    @Transactional(readOnly = true)
    public Optional<PrestamoHerramienta> prestamoPorFolio(String folio) {
        if (folio == null || folio.isBlank()) {
            return Optional.empty();
        }
        return prestamoRepository.findByFolio(folio.trim());
    }

    /**
     * Entrega una herramienta sin destino. Se conserva para los llamados que
     * ya existían y para los clientes de la API que todavía no mandan ese dato;
     * la ventana de escritorio usa la versión con destino.
     */
    @Transactional
    public PrestamoHerramienta prestar(Long herramientaId,
                                       String personaRecibe,
                                       String areaResponsable,
                                       LocalDate fechaEstimadaDevolucion,
                                       String observaciones,
                                       Usuario usuarioSesion) {
        return prestar(herramientaId, personaRecibe, areaResponsable, null,
                fechaEstimadaDevolucion, observaciones, usuarioSesion);
    }

    /**
     * Entrega una herramienta.
     *
     * Se rechaza si la herramienta ya está prestada: no puede haber dos
     * responsables señalados a la vez para el mismo bien.
     */
    @Transactional
    public PrestamoHerramienta prestar(Long herramientaId,
                                       String personaRecibe,
                                       String areaResponsable,
                                       String destino,
                                       LocalDate fechaEstimadaDevolucion,
                                       String observaciones,
                                       Usuario usuarioSesion) {
        Herramienta herramienta = herramientaRepository.findById(herramientaId)
                .orElseThrow(() -> new IllegalArgumentException("Herramienta no encontrada: " + herramientaId));

        if (BAJA.equals(herramienta.getEstado())) {
            throw new IllegalArgumentException(
                    "La herramienta está dada de baja y no se puede prestar");
        }

        Optional<PrestamoHerramienta> yaPrestada = prestamoActivo(herramientaId);
        if (yaPrestada.isPresent()) {
            throw new IllegalArgumentException("La herramienta ya está prestada a "
                    + yaPrestada.get().getPersonaRecibe()
                    + " desde el " + yaPrestada.get().getFechaEntrega().toLocalDate()
                    + ". Hay que registrar su devolución antes de volver a prestarla.");
        }

        if (personaRecibe == null || personaRecibe.isBlank()) {
            throw new IllegalArgumentException("Hay que indicar quién recibe la herramienta");
        }

        if (fechaEstimadaDevolucion == null) {
            throw new IllegalArgumentException("La fecha estimada de devolución es obligatoria");
        }
        if (fechaEstimadaDevolucion.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "La fecha estimada de devolución no puede ser anterior a hoy");
        }

        PrestamoHerramienta prestamo = new PrestamoHerramienta();
        prestamo.setHerramienta(herramienta);
        // Folio del préstamo, como el de una entrada o de una salida: lo pone
        // el sistema y es el que aparece en el reporte.
        prestamo.setFolio(siguienteFolioLibre());
        prestamo.setPersonaRecibe(personaRecibe.trim());
        prestamo.setFechaEntrega(LocalDateTime.now());
        // Estado físico al salir, congelado aquí para poder compararlo con el
        // de la devolución (herramientaEstado se queda para esa).
        prestamo.setEstadoEntrega(herramienta.getEstadoFisico());
        // Quién entrega es quien tiene la sesión abierta: es el que responde del bien.
        prestamo.setPersonaEntrega(usuarioSesion == null ? null : usuarioSesion.getNombre());
        prestamo.setAreaResponsable(areaResponsable != null && !areaResponsable.isBlank()
                ? areaResponsable.trim()
                : herramienta.getAreaResponsable());
        prestamo.setDestino(destino != null && !destino.isBlank() ? destino.trim() : null);
        prestamo.setFechaEstimadaDevolucion(fechaEstimadaDevolucion);
        prestamo.setObservaciones(observaciones);
        prestamo.setEstado("PENDIENTE");
        prestamo.setHerramientaEstado(herramienta.getEstadoFisico());
        PrestamoHerramienta guardado = prestamoRepository.save(prestamo);

        // La ficha de la herramienta queda diciendo con quién está y cuándo vuelve.
        herramienta.setAreaResponsable(prestamo.getAreaResponsable());
        herramienta.setFechaEstimadaDevolucion(fechaEstimadaDevolucion);
        herramientaRepository.save(herramienta);

        bitacoraService.registrar(
                BitacoraService.PRESTAMO_HERRAMIENTA,
                "HERRAMIENTAS",
                "Folio: " + prestamo.getFolio()
                        + " | Herramienta: " + herramienta.getNombre()
                        + " | Código: " + herramienta.getCodigoBarras()
                        + " | Recibe: " + prestamo.getPersonaRecibe()
                        + (prestamo.getDestino() == null ? "" : " | Para dónde: " + prestamo.getDestino())
                        + " | Entrega: " + (prestamo.getPersonaEntrega() == null ? "(desconocido)"
                                : prestamo.getPersonaEntrega())
                        + " | Estado al entregar: " + prestamo.getEstadoEntrega()
                        + " | Devolución estimada: " + fechaEstimadaDevolucion,
                observaciones == null || observaciones.isBlank() ? "Préstamo de herramienta" : observaciones,
                usuarioSesion);

        return guardado;
    }

    /**
     * Devolución de un préstamo.
     *
     * Un préstamo ya devuelto no se puede devolver dos veces, y la herramienta
     * queda con el estado físico con el que regresa.
     */
    @Transactional
    public PrestamoHerramienta devolver(Long prestamoId,
                                        LocalDate fechaDevolucion,
                                        String estadoFisico,
                                        String observaciones,
                                        Usuario usuarioSesion) {
        PrestamoHerramienta prestamo = prestamoRepository.findById(prestamoId)
                .orElseThrow(() -> new IllegalArgumentException("Préstamo no encontrado: " + prestamoId));

        if (prestamo.getFechaDevolucion() != null) {
            throw new IllegalArgumentException(
                    "Este préstamo ya fue devuelto el " + prestamo.getFechaDevolucion());
        }
        if (fechaDevolucion == null) {
            throw new IllegalArgumentException("Hay que indicar la fecha de devolución");
        }
        if (fechaDevolucion.isBefore(prestamo.getFechaEntrega().toLocalDate())) {
            throw new IllegalArgumentException(
                    "La devolución no puede ser anterior a la entrega ("
                            + prestamo.getFechaEntrega().toLocalDate() + ")");
        }

        String estadoFinal = estadoFisico == null || estadoFisico.isBlank()
                ? (prestamo.getEstadoEntrega() != null
                        ? prestamo.getEstadoEntrega()
                        : prestamo.getHerramientaEstado())
                : estadoFisico;
        if (estadoFinal == null || estadoFinal.isBlank()) {
            estadoFinal = "BUENO";
        }

        Herramienta herramienta = prestamo.getHerramienta();

        prestamo.setFechaDevolucion(fechaDevolucion);
        prestamo.setEstado("DEVUELTO");
        prestamo.setHerramientaEstado(estadoFinal);
        // Quién recibió la devolución: la cuenta con la que se cerró.
        prestamo.setUsuarioDevolucion(usuarioSesion);
        prestamo.setObservaciones(unir(prestamo.getObservaciones(),
                observaciones == null || observaciones.isBlank() ? null
                        : "Devolución: " + observaciones));
        prestamoRepository.save(prestamo);

        herramienta.setEstadoFisico(estadoFinal);
        herramienta.setFechaEstimadaDevolucion(null);
        herramientaRepository.save(herramienta);

        bitacoraService.registrar(
                BitacoraService.DEVOLUCION_HERRAMIENTA,
                "HERRAMIENTAS",
                "Folio: " + prestamo.getFolio()
                        + " | Herramienta: " + herramienta.getNombre()
                        + " | Código: " + herramienta.getCodigoBarras()
                        + " | Recibió la entrega: " + prestamo.getPersonaRecibe()
                        + " | Estado al salir: " + prestamo.getEstadoEntrega()
                        + " | Devolución: " + fechaDevolucion
                        + " | Estado al volver: " + estadoFinal
                        + " | Registró la devolución: " + (usuarioSesion == null
                                ? "(sin sesión)" : usuarioSesion.getEmail()),
                observaciones == null || observaciones.isBlank() ? "Devolución de herramienta" : observaciones,
                usuarioSesion);

        return prestamo;
    }

    /**
     * Un folio candidato para el préstamo.
     *
     * Seis caracteres después de {@code PRE-} dejan un código de 10 en total,
     * el mismo largo que el de una herramienta. Así el código de barras del
     * ticket cabe con módulos anchos en el papel de 80 mm y el lector lo puede
     * devolver sin que el folio largo de antes lo vuelva a apretar.
     */
    public static String candidatoFolio() {
        return "PRE-" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase(Locale.ROOT);
    }

    /**
     * Folio del préstamo, con el mismo criterio que {@code ENT-} y {@code SAL-}:
     * lo pone el sistema, y se comprueba contra la tabla porque la columna
     * tiene restricción única.
     */
    private String siguienteFolioLibre() {
        String candidato = candidatoFolio();
        while (prestamoRepository.findByFolio(candidato).isPresent()) {
            candidato = candidatoFolio();
        }
        return candidato;
    }

    private static String unir(String existente, String nuevo) {
        if (nuevo == null || nuevo.isBlank()) {
            return existente;
        }
        if (existente == null || existente.isBlank()) {
            return nuevo;
        }
        return existente + " | " + nuevo;
    }
}
