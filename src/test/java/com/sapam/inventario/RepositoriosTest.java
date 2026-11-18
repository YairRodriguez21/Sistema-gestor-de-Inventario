package com.sapam.inventario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.repository.Repository;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * En este sistema nada se borra: todo se da de baja con estado ({@code BAJAS}
 * o {@code activo=false}) para conservar la trazabilidad.
 *
 * Ese criterio vivía en la costumbre y en la buena voluntad, porque
 * {@code CrudRepository}/{@code JpaRepository} traen {@code delete},
 * {@code deleteById}, {@code deleteAll} y borrados masivos por lote. Aquí se
 * comprueba sobre <b>todos</b> los repositorios del paquete, escaneándolo de
 * verdad, así que un repositorio nuevo queda cubierto sin acordarse de
 * añadirlo a ninguna lista.
 *
 * Si esta prueba falla, es que alguien le ha vuelto a poner a un repositorio
 * una base que sí borra.
 */
class RepositoriosTest {

    @Test
    void ningunRepositorioExponeMetodosDeBorrado() {
        List<Class<?>> interfaces = repositorios();

        assertTrue(interfaces.size() >= 14,
                "se esperaban al menos catorce interfaces de repositorio y se hallaron "
                        + interfaces.size() + ": " + nombres(interfaces));

        for (Class<?> repositorio : interfaces) {
            Set<String> borrados = Arrays.stream(repositorio.getMethods())
                    .map(Method::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith("delete"))
                    .collect(Collectors.toSet());

            assertTrue(borrados.isEmpty(),
                    repositorio.getSimpleName() + " expone métodos de borrado " + borrados
                            + "; en SGIA se da de baja, no se borra");
        }
    }

    /** Todas las interfaces del paquete de repositorios que hay hoy. */
    private static List<Class<?>> repositorios() {
        List<Class<?>> encontrados = new ArrayList<>();
        for (BeanDefinition definicion : new EscaneaRepositorios()
                .findCandidateComponents("com.sapam.inventario.repository")) {
            try {
                encontrados.add(Class.forName(definicion.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("no se pudo cargar " + definicion.getBeanClassName(), e);
            }
        }
        return encontrados;
    }

    /**
     * El escáner de Spring descarta las interfaces por defecto (está pensado
     * para beans), y aquí justo lo que interesa son ellas: restringe el filtro
     * a los que cuelgan de {@link Repository} y deja pasar las interfaces.
     */
    private static final class EscaneaRepositorios extends ClassPathScanningCandidateComponentProvider {

        private EscaneaRepositorios() {
            super(false);
            addIncludeFilter(new AssignableTypeFilter(Repository.class));
        }

        @Override
        protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
            return beanDefinition.getMetadata().isInterface();
        }
    }

    private static List<String> nombres(List<Class<?>> clases) {
        List<String> nombres = new ArrayList<>();
        for (Class<?> clase : clases) {
            nombres.add(clase.getSimpleName());
        }
        return nombres;
    }
}
