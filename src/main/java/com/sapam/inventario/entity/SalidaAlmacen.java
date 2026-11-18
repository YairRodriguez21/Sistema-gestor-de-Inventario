package com.sapam.inventario.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

@Entity
@Table(name = "salidas_almacen")
public class SalidaAlmacen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String folio; // Folio único por salida

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    @ManyToOne
    @JoinColumn(name = "usuario_registro_id", nullable = false)
    private Usuario usuarioRegistro; // Usuario que registra la salida

    @Column(nullable = false, length = 100)
    private String personaSolicita; // Quien solicita el material

    @Column(length = 50)
    private String puestoSolicita; // Puesto o cargo de quien solicita

    @Column(length = 50)
    private String departamentoSolicita; // Área o departamento

    @ManyToOne
    @JoinColumn(name = "usuario_entrega_id")
    private Usuario usuarioEntrega; // Almacenista quien entrega

    // Texto libre, igual que personaSolicita, y no un usuario del sistema: quien
    // se lleva el material casi nunca tiene acceso para entrar. Quién lo entregó
    // ya queda en usuarioEntrega y quién lo procesó en usuarioRegistro.
    @Column(name = "persona_recibe", nullable = false, length = 100)
    private String personaRecibe; // Quien recibe el material

    // Puesto y área de adscripción de quien recibe, como pide la especificación
    // ("nombre completo, cargo o puesto y área de adscripción"). Son opcionales:
    // el nombre basta cuando el material lo retira alguien de fuera y no tiene
    // área dentro de la organización.
    @Column(name = "puesto_recibe", length = 100)
    private String puestoRecibe;

    @Column(name = "departamento_recibe", length = 100)
    private String departamentoRecibe;

    // 24 caracteres: "MANTENIMIENTO_PREVENTIVO"/"MANTENIMIENTO_CORRECTIVO" no caben en 20
    @Column(nullable = false, length = 50)
    private String motivoSalida; // REPARACION, MANTENIMIENTO_PREVENTIVO, MANTENIMIENTO_CORRECTIVO, INSTALACION, ADMINISTRATIVO, OTRO

    // Qué es en realidad cuando el motivo es OTRO. El desplegable solo admite
    // las seis palabras de la especificación y "OTRO" por sí sola no explica
    // nada a la hora de revisar quién sacó qué y por qué.
    @Column(name = "motivo_otros", length = 255)
    private String motivoOtro;

    // Orden de trabajo que autoriza la salida: la teclea quien entrega el
    // material y sin ella el formulario no deja guardar. Es la clave que amarra
    // la salida con la orden que la justifica.
    //
    // Va sin nullable=false a propósito: spring.jpa.hibernate.ddl-auto=update
    // agrega la columna a la tabla que ya existe, y en MySQL estricto un ALTER
    // TABLE con NOT NULL sobre filas ya escritas falla y la aplicación no
    // arranca. La obligatoriedad vive en el servicio y en el formulario, que
    // es donde de verdad se controla.
    @Column(name = "orden_trabajo", length = 50)
    private String ordenTrabajo;

    @Column(columnDefinition = "text")
    private String observaciones;

    @OneToMany(mappedBy = "salidaAlmacen", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductoSalida> productosEntregados; // Productos con cantidades entregadas

    // Getters, setters, constructors

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFolio() {
        return folio;
    }

    public void setFolio(String folio) {
        this.folio = folio;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public void setHora(LocalTime hora) {
        this.hora = hora;
    }

    public Usuario getUsuarioRegistro() {
        return usuarioRegistro;
    }

    public void setUsuarioRegistro(Usuario usuarioRegistro) {
        this.usuarioRegistro = usuarioRegistro;
    }

    public String getPersonaSolicita() {
        return personaSolicita;
    }

    public void setPersonaSolicita(String personaSolicita) {
        this.personaSolicita = personaSolicita;
    }

    public String getPuestoSolicita() {
        return puestoSolicita;
    }

    public void setPuestoSolicita(String puestoSolicita) {
        this.puestoSolicita = puestoSolicita;
    }

    public String getDepartamentoSolicita() {
        return departamentoSolicita;
    }

    public void setDepartamentoSolicita(String departamentoSolicita) {
        this.departamentoSolicita = departamentoSolicita;
    }

    public Usuario getUsuarioEntrega() {
        return usuarioEntrega;
    }

    public void setUsuarioEntrega(Usuario usuarioEntrega) {
        this.usuarioEntrega = usuarioEntrega;
    }

    public String getPersonaRecibe() {
        return personaRecibe;
    }

    public void setPersonaRecibe(String personaRecibe) {
        this.personaRecibe = personaRecibe;
    }

    public String getPuestoRecibe() {
        return puestoRecibe;
    }

    public void setPuestoRecibe(String puestoRecibe) {
        this.puestoRecibe = puestoRecibe;
    }

    public String getDepartamentoRecibe() {
        return departamentoRecibe;
    }

    public void setDepartamentoRecibe(String departamentoRecibe) {
        this.departamentoRecibe = departamentoRecibe;
    }

    public String getMotivoSalida() {
        return motivoSalida;
    }

    public void setMotivoSalida(String motivoSalida) {
        this.motivoSalida = motivoSalida;
    }

    public String getMotivoOtro() {
        return motivoOtro;
    }

    public void setMotivoOtro(String motivoOtro) {
        this.motivoOtro = motivoOtro;
    }

    public String getOrdenTrabajo() {
        return ordenTrabajo;
    }

    public void setOrdenTrabajo(String ordenTrabajo) {
        this.ordenTrabajo = ordenTrabajo;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public Set<ProductoSalida> getProductosEntregados() {
        return productosEntregados;
    }

    public void setProductosEntregados(Set<ProductoSalida> productosEntregados) {
        this.productosEntregados = productosEntregados;
    }
}