# SGIA — Sistema de Gestión de Inventario

Aplicación de escritorio para llevar el inventario de almacén: alta de productos,
entradas y salidas con código de barras, kardex, reportes, préstamo de herramientas
y bitácora de todo lo que pasa.

Hecha con **Spring Boot** (el cerebro) + **JavaFX** (la ventana) + **MySQL** (los datos).

---

## Índice

1. [Qué hace](#qué-hace)
2. [Requisitos](#requisitos)
3. [Cómo se corre](#cómo-se-corre)
4. [Cuentas de prueba](#cuentas-de-prueba)
5. [Estructura del código](#estructura-del-código)
6. [Las dos formas de entrar a los datos](#las-dos-formas-de-entrar-a-los-datos)
7. [Cómo se prueban los cambios](#cómo-se-prueban-los-cambios)
8. [Cosas que conviene saber](#cosas-que-conviene-saber)

---

## Qué hace

| Módulo | Qué hace |
|---|---|
| **Productos** | Alta, edición y baja lógica. Cada uno lleva código de barras (Code 128) e interno |
| **Entradas** | Registrar lo que entra al almacén. Escanear un código agrega 1 unidad |
| **Salidas** | Registrar lo que sale. Escanear descuenta |
| **Kardex** | Historial completo de movimientos de un producto, con folio y saldo |
| **Herramientas** | Préstamo con ticket (quién, área, destino y código) y devolución manual o escaneando el ticket. El operativo **solo presta y devuelve** |
| **Reportes** | Existencias, productos agotados, stock bajo, movimientos por trabajador, etc. |
| **Bitácora** | Quién hizo qué y cuándo. Paginada de 200 en 200 |
| **Cuentas** | Administración de usuarios y roles (solo admin) |

**No hay bajas físicas.** `RepositorioSGIA` prohíbe `delete`: todo se marca como
`BAJAS` o `activo = false`, para que la trazabilidad no se rompa nunca.

---

## Requisitos

- **JDK 17** o superior
- **MySQL 8** corriendo
- Una base de datos llamada `sapam_inventario`

No necesitas instalar Maven: el proyecto trae su propio lanzador (`mvnw`).

### Configurar la base de datos

La configuración vive en [`src/main/resources/application.properties`](src/main/resources/application.properties):

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/sapam_inventario?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=1234
```

Cambia ahí la URL, el usuario y la contraseña según tu máquina.

> **Las tablas se crean solas.** Hibernate está en `ddl-auto=update`, así que al
> arrancar crea las tablas que falten y agrega las columnas nuevas. No hay que
> correr ningún SQL a mano para empezar.

### Crear la base de datos

```sql
CREATE DATABASE sapam_inventario CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## Cómo se corre

### Aplicación de escritorio (la ventana)

```powershell
.\mvnw.cmd javafx:run
```

Arranca Spring **dentro de la misma ventana** que JavaFX. Sale la pantalla de acceso.

### Solo la API REST (sin ventana)

```powershell
.\mvnw.cmd spring-boot:run
```

Queda escuchando en `http://localhost:8080`.

> ⚠️ **No arranques las dos a la vez** si ambas apuntan al mismo MySQL con
> `ddl-auto=update`: pueden pisarse creando tablas.

---

## Cuentas de prueba

`SecurityConfig` crea estas cuentas **al primer arrancar**, solo si no existen ya.
Todas con la contraseña `123456`:

| Correo | Rol | Qué puede hacer |
|---|---|---|
| `admin@sgia.com` | **admin** | Todo |
| `operativo@sgia.com` | **usuario** | Entradas, salidas, kardex, reportes y herramientas **solo para prestar y devolver** |
| `consulta@sgia.com` | **consulta** | Solo lectura y bitácora |

### La matriz de permisos, en corto

| | admin | usuario | consulta |
|---|:---:|:---:|:---:|
| Ver inventario, kardex, reportes | ✅ | ✅ | ✅ |
| Registrar entradas y salidas | ✅ | ✅ | ❌ |
| Préstamo / devolución de herramientas | ✅ | ✅ | ❌ |
| Alta, edición y baja de productos | ✅ | ❌ | ❌ |
| Alta de herramientas | ✅ | ❌ | ❌ |
| Administrar cuentas | ✅ | ❌ | ❌ |
| Ver la bitácora | ✅ | ❌ | ✅ |

Esto se aplica **en dos lugares**: en la ventana (los botones se ocultan) **y** en
la API (`SecurityConfig` devuelve 403). Ocultar un botón no basta.

---

## Estructura del código

Todo está en `src/main/java/com/sapam/inventario/`, separado por capas.
Cada carpeta tiene una sola responsabilidad:

```
com.sapam.inventario
│
├── InventarioApplication     ← Arranca Spring. El punto de entrada del backend
│
├── entity/        (13)  ← Las tablas de MySQL, escritas como clases de Java
├── repository/    (14)  ← "Cómo leer y escribir esas tablas" (Spring Data JPA)
├── service/       (12)  ← La lógica: validar, calcular, registrar movimientos
├── controller/    (14)  ← La API REST: /api/productos, /api/entradas, etc.
├── config/         (6)  ← Seguridad, CORS, reglas por rol
├── reportes/      (15)  ← DTOs y el servicio que arma los reportes
│
└── javafx/        (12)  ← La ventana de escritorio (JavaFX)
    ├── JavaFxApplication      ← Arranca Spring y abre la ventana de acceso
    └── controller/            ← Un controlador por ventana
```

### ¿Dónde busco...?

| Quiero saber... | Voy a |
|---|---|
| Cómo se ve una tabla en MySQL | `entity/Producto.java`, `entity/Usuario.java` |
| Cómo se consulta la base | `repository/*.java` |
| Dónde se valida o se calcula algo | `service/*.java` |
| Cómo responde la API | `controller/*.java` |
| Qué puede cada rol | `config/SecurityConfig.java` |
| Cómo se ve una ventana | `src/main/resources/view/*.fxml` |
| Qué hace cada ventana | `javafx/controller/*.java` |
| Cómo se arma un reporte | `reportes/ReporteService.java` |

### La ventana y el código

Las ventanas están en **`src/main/resources/view/*.fxml`**. Un `.fxml` es XML que
describe la pantalla (botones, tablas, textos). El código que la maneja está en
`javafx/controller/`, y la conexión se hace por nombre:

```xml
<!-- En main.fxml -->
<Label fx:id="lblSeccion" ... />
<Button onAction="#buscarProducto" ... />
```

- `fx:id="lblSeccion"` → en el controlador hay un campo `@FXML private Label lblSeccion;`
- `onAction="#buscarProducto"` → en el controlador hay un método `@FXML private void buscarProducto()`

> ⚠️ **Si borras un `fx:id` del `.fxml` pero el campo sigue en el controlador, la
> compilación pasa bien y la ventana revienta con `NullPointerException` al usarse.**
> Por eso existe la prueba `todosLosIdDelXmlDeLaPantallaPrincipalTienenSuCampo`.

---

## Las dos formas de entrar a los datos

Es la duda que más se repite, así que va clara:

| | Escritorio (JavaFX) | API REST |
|---|---|---|
| **Entrada** | `JavaFxApplication` | `http://localhost:8080/api/...` |
| **Llega a los datos...** | pidiéndole a Spring los servicios **en memoria** | pidiéndole a los `controller/` por HTTP |
| **¿Se usa hoy?** | ✅ Sí, es la forma real | ⚠️ Existe y está probada, pero **la ventana no le habla** |

O sea: **la API ya está construida y probada** (con JWT y permisos por rol), pero
la ventana de escritorio no la usa todavía — habla directo con los servicios.
Es la pieza que falta si algún día se quiere acceso desde otra máquina sin
instalar el programa.

---

## Cómo se prueban los cambios

```powershell
.\mvnw.cmd clean test
```

**156 pruebas, 0 fallos** al cierre de este documento.

> ⚠️ Usa **`clean` siempre**. Si no, Maven reusa clases viejas de `target/` y
> puedes estar probando código que ya no existe.

Las pruebas usan **H2**, que es una base de datos de mentira que vive en memoria.
MySQL solo lo toca la aplicación real. Por eso puedes correr las 156 pruebas sin
tener MySQL encendido.

Dónde está cada cosa:

| Pruebas | Archivo |
|---|---|
| Ventanas y `.fxml` | `FxmlTest.java` |
| API REST y permisos por rol | `ApiRestTest.java` |
| Bitácora y paginación | `BitacoraTest.java` |
| Códigos de barras | `CodigoBarrasTest.java`, `CodigoBarrasServiceTest.java` |
| Reglas de negocio (stock, herramientas, secciones) | `HerramientaTest.java`, `SalidaAlmacenTest.java`, etc. |

---

## Cosas que conviene saber

**No se borra nada.** `RepositorioSGIA` prohíbe `delete`. Las bajas son lógicas
(`estado = BAJAS`, `activo = false`). Si buscas un `DELETE` en el código, no lo vas
a encontrar: es a propósito.

**El responsable es quien inició sesión.** Cada movimiento, préstamo o edición
guarda quién lo hizo, y eso se refleja en la bitácora. No se puede registrar algo
sin dueño.

**Las contraseñas van en texto plano** en la tabla `usuarios`. Es una decisión
tomada y consciente del proyecto, no un olvido.

**Los códigos de barras son Code 128** (una dimensión), no QR. Las etiquetas son
de 50 x 30 mm y los tickets de préstamo van en el rollo de 80 mm, así que los
códigos son cortos. No queda ninguna referencia a QR en el código ni en la base
de datos.

**Cada préstamo de herramienta entrega un ticket.** Lleva quién se la lleva, el
área, para dónde va, la herramienta, las fechas y un folio `PRE-` impreso en
Code 128. Para devolverla se puede usar el botón **Devolver** de la fila o
escanear ese código en la caja de búsqueda de Herramientas: la app localiza el
préstamo y abre el mismo cuadro de devolución. No hace falta que el lector mande
Enter ni que escriba el código exactamente pelado: el buscador reconoce el folio
completo aunque venga con prefijo o salto de línea.

**El módulo QR fue eliminado por completo**: entidades, rutas, migraciones y
referencias. Si algo falla buscando `/qr/**`, es porque esa ruta ya no existe.

**Las migraciones de SQL se corren a mano** cuando toca, con
`cmd /c "mysql -uroot -p -D sapam_inventario < archivo.sql"`. Son idempotentes:
puedes correrlas dos veces sin romper nada.

---

## Comandos rápidos

```powershell
# Ventana de escritorio
.\mvnw.cmd javafx:run

# Solo API en el puerto 8080
.\mvnw.cmd spring-boot:run

# Probar todo
.\mvnw.cmd clean test

# Empaquetar
.\mvnw.cmd clean package
```

> En PowerShell hay que escribir `.\mvnw.cmd` con el prefijo `.\` — si lo escribes
> a secas falla.
