# Post-contenido — Unidad 7: Patrones Arquitectónicos I — De Capas Simples a una Decisión Arquitectónica Justificada

**Curso:** Patrones de Diseño de Software (Código: 1156701-A)  
**Semestre:** Sexto Semestre  
**Estudiante:** Brayan Smith Albarracín Tarazona  
**Institución:** Universidad Francisco de Paula Santander (UFPS)  

---

## 1. Descripción General del Proyecto

Este repositorio contiene la solución completa del post-contenido de la **Unidad 7**, implementando un sistema backend de gestión y recaudo de multas de biblioteca universitaria (`multas-biblioteca-api`) desarrollado con **Java 17**, **Spring Boot 3.x**, **Spring Data JPA** y base de datos relacional en memoria **H2**.

El proyecto resuelve de manera justificada la evolución arquitectónica en dos etapas:
1. **Parte 1 — Arquitectura en Capas:** Construcción estricta de una API REST separada en capas de Presentación (`controller/`), Aplicación (`service/`), Dominio (`model/`) e Infraestructura (`repository/`), con reglas de negocio reales distribuidas apropiadamente.
2. **Parte 2 — Pago en Línea con Dos Pasarelas Intercambiables (Decisión Arquitectónica):** Introducción deliberada de **Arquitectura Hexagonal (Puertos y Adaptadores)** exclusivamente en el subsistema de pagos (`domain/port/` e `infrastructure/pago/`), permitiendo conmutar entre `PagosUDES` y `Wompi` por configuración sin modificar el núcleo de la aplicación.

---

## 2. Diagrama y Estructura de Paquetes

```text
apellido-post1-u7/
├── multas-biblioteca-api/
│   ├── pom.xml
│   ├── .gitignore
│   └── src/
│       ├── main/
│       │   ├── java/com/example/multas/
│       │   │   ├── MultasApplication.java
│       │   │   │
│       │   │   ├── controller/                         <-- Capa de Presentación (REST)
│       │   │   │   ├── MultaController.java
│       │   │   │   ├── GenerarMultaRequest.java
│       │   │   │   └── GlobalExceptionHandler.java
│       │   │   │
│       │   │   ├── service/                            <-- Capa de Aplicación (Casos de uso)
│       │   │   │   └── MultaService.java
│       │   │   │
│       │   │   ├── model/                              <-- Capa de Dominio (Entidades y Excepciones)
│       │   │   │   ├── Multa.java
│       │   │   │   ├── EstadoMulta.java
│       │   │   │   ├── MultaNotFoundException.java
│       │   │   │   ├── LimiteMultasPendientesException.java
│       │   │   │   └── MultaYaPagadaException.java
│       │   │   │
│       │   │   ├── repository/                         <-- Capa de Infraestructura (Spring Data JPA)
│       │   │   │   └── MultaRepository.java
│       │   │   │
│       │   │   ├── domain/                             <-- NUEVO: Puerto Hexagonal (Java Puro, sin Spring)
│       │   │   │   ├── port/
│       │   │   │   │   └── PasarelaPagoPort.java       <-- Interfaz de puerto de salida
│       │   │   │   ├── ResultadoPago.java              <-- Record inmutable de resultado común
│       │   │   │   └── PagoRechazadoException.java
│       │   │   │
│       │   │   └── infrastructure/                     <-- Adaptadores de Infraestructura Externa
│       │   │       ├── pago/
│       │   │       │   ├── PagosUdesAdapter.java       <-- Adaptador HTTP PagosUDES
│       │   │       │   └── WompiAdapter.java           <-- Adaptador HTTP Wompi (centavos/referencia)
│       │   │       └── config/
│       │   │           └── RestTemplateConfig.java
│       │   │
│       │   └── resources/
│       │       └── application.properties
│       │
│       └── test/java/com/example/multas/
│           └── MultaServiceTest.java
│
├── .gitignore
└── README.md
```

---

## 3. Decisiones de Diseño y Justificaciones Técnicas

### Punto de Decisión 1 — Cálculo del Monto: ¿Entidad o Service?
- **Decisión Adoptada:** La fórmula de cálculo del monto (`diasAtraso * 500`, con tope máximo de `$15,000`) se implementó como un método estático puro en la propia entidad `Multa.calcularMonto(int diasAtraso)`.
- **Criterio Técnico:** Una regla de negocio que no requiere dependencias externas, repositorios ni servicios colaboradores pertenece legítimamente al objeto de dominio.
- **Alternativa Descartada y Consecuencias:** Alojar el cálculo como un método privado en `MultaService`. Se descartó porque transformaría a `Multa` en un **modelo anémico** (simple contenedor de datos con getters y setters). Si en el futuro otro componente del sistema (como una tarea por lotes de auditoría o un simulador web) requiere calcular montos, se vería obligado a inyectar todo el `MultaService` o a duplicar la fórmula, violando el principio de encapsulamiento orientado a objetos.

### Punto de Decisión 2 — Conteo de Multas Pendientes: ¿Consulta en Base de Datos o Filtrado en Memoria?
- **Decisión Adoptada:** Se implementó la consulta derivada `countByEstudianteIdAndEstado(String estudianteId, EstadoMulta estado)` en `MultaRepository`.
- **Criterio Técnico:** La responsabilidad de decidir ("¿el estudiante excede el tope de 3 multas pendientes?") reside 100% en `MultaService`, pero el cómputo de la métrica se delega a donde es óptimo resolverlo: en el motor SQL de la base de datos (`SELECT COUNT(*)`).
- **Alternativa Descartada y Consecuencias:** Utilizar `findByEstudianteId(...)` para cargar todas las multas del estudiante a memoria JVM y filtrarlas mediante Java Streams (`multas.stream().filter(...).count()`). Si bien funcional con conjuntos de datos de laboratorio, esta alternativa escala deficientemente: en un sistema real con miles de registros históricos por estudiante, transferiría grandes volúmenes de datos por la red, saturaría la memoria heap de la JVM y degradaría exponencialmente la latencia de creación de multas.

### Punto de Decisión 3 — Selección del Adaptador Activo de Pago en Tiempo de Arranque
- **Decisión Adoptada:** Se utilizó `@ConditionalOnProperty(prefix = "app.pagos", name = "proveedor", havingValue = "...")` en cada adaptador (`PagosUdesAdapter` y `WompiAdapter`).
- **Criterio Técnico:** Cada sede universitaria opera de manera fija con un único proveedor durante el piloto (Sede Principal con PagosUDES, Sede Piloto con Wompi). Al evaluar la propiedad en tiempo de inicialización del contexto Spring, únicamente se registra en el contenedor el bean correspondiente. De este modo, `MultaService` inyecta directamente `PasarelaPagoPort` sin requerir anotaciones `@Qualifier` ni condicionales.
- **Alternativa Descartada y Consecuencias:** Inyectar un `Map<String, PasarelaPagoPort>` o una lista de adaptadores y seleccionar la pasarela dinámicamente por solicitud en tiempo de ejecución. Aunque brinda mayor dinamismo, se descartó porque el caso de negocio no requería selección por transacción sino por entorno/sede; además, hubiera obligado a `MultaService` a conocer las claves de configuración de los proveedores, rompiendo el desacoplamiento.

### Punto de Decisión 4 — Diseño del Puerto y Neutralidad del Tipo de Resultado (`ResultadoPago`)
- **Decisión Adoptada:** `PasarelaPagoPort` devuelve exclusivamente el tipo de dominio neutral `ResultadoPago(proveedor, exitoso, referenciaExterna, mensaje)`, modelado como un Java Record puro sin ninguna dependencia externa.
- **Criterio Técnico:** PagosUDES responde con `idTransaccion` y `estadoTransaccion` ("APROBADA"), mientras que Wompi responde en centavos con `reference` y `status` ("APPROVED"). Cada adaptador asume la responsabilidad de traducir estas discrepancias externas al contrato canónico del dominio.
- **Alternativa Descartada y Consecuencias:** Si `ResultadoPago` expusiera campos específicos como `idTransaccion` en lugar de `referenciaExterna`, o si el puerto devolviera el DTO HTTP propio de PagosUDES, WompiAdapter tendría que forzar conversiones artificiales o dejar campos nulos. Más aún, `MultaService` quedaría acoplado a los formatos de terceros, y la incorporación de una tercera pasarela (como PSE o PayU) rompería el código del servicio.

---

## 4. Trade-off Considerado — Parte 2 (Capa Simple vs. Hexagonal)

Para la Parte 2 se consideraron tres alternativas arquitectónicas:
1. **Opción A (Bifurcación en `MultaService`):** Rápida de codificar, pero MultaService se acoplaría a clientes HTTP y formatos externos.
2. **Opción B (Patrón Strategy dentro de `service/`):** Mantiene la arquitectura en capas simple y resuelve el polimorfismo, pero la interfaz de estrategia y los clientes HTTP compartirían la misma capa y dependencias.
3. **Opción C (Puerto de Dominio con Adaptadores - Hexagonal):** **Elegida.** Se creó un puerto puro en `domain/port/` y adaptadores desacoplados en `infrastructure/pago/`.

### Análisis Honesto de Costos y Beneficios:
- **Qué se ganó:** Aislamiento total del núcleo. El dominio (`domain/`) no importa Spring, ni RestTemplate, ni JSON. Añadir una tercera pasarela implica crear un nuevo adaptador en `infrastructure/` sin tocar una sola línea de `MultaService` ni de `MultaController`.
- **Costo adicional asumido:** Incremento en la dispersión de paquetes (se sumaron `domain/` e `infrastructure/`), duplicación aparente de modelos (DTOs internos de cada pasarela + DTO del puerto `ResultadoPago`), y una curva de navegación ligeramente más amplia para desarrolladores acostumbrados a capas simples estándar.
- **Veredicto:** Si al finalizar el piloto la universidad cancelara Wompi y adoptara una única pasarela institucional definitiva sin cambios futuros, el equipo justificaría simplificar la solución hacia la Opción B para reducir la indirección innecesaria; no obstante, mientras exista coexistencia e incertidumbre de proveedores, el puerto hexagonal es la única arquitectura que protege el dominio.

---

## 5. Verificación de Endpoints de la API

La API fue verificada exhaustivamente contra los checkpoints del laboratorio:

| Método HTTP | Endpoint | Descripción | Código Esperado | Validación Observable |
| :--- | :--- | :--- | :--- | :--- |
| **GET** | `/api/multas` | Listar todas las multas registradas | `200 OK` | Retorna arreglo JSON con las multas activas |
| **POST** | `/api/multas` | Generar nueva multa | `201 Created` | Calcula monto (`diasAtraso * 500`) y estado `PENDIENTE` |
| **POST** | `/api/multas` | Intento con campos vacíos o días < 1 | `400 Bad Request` | Capturado por Bean Validation en `GlobalExceptionHandler` |
| **POST** | `/api/multas` | Intentar generar 4ta multa a mismo estudiante | `409 Conflict` | Lanza `LimiteMultasPendientesException` |
| **GET** | `/api/multas/{id}` | Buscar multa inexistente | `404 Not Found` | Lanza `MultaNotFoundException` |
| **PATCH** | `/api/multas/{id}/pagar` | Pagar en ventanilla física | `200 OK` | Marca estado `PAGADA`, metodo `VENTANILLA`, fecha actual |
| **PATCH** | `/api/multas/{id}/pagar` | Reintentar pago de multa ya pagada | `409 Conflict` | Lanza `MultaYaPagadaException` |
| **POST** | `/api/multas/{id}/pagar-en-linea` | Pago en línea con pasarela activa | `200 OK` | Marca estado `PAGADA` con metodo `PAGOSUDES` o `WOMPI` |
| **POST** | `/api/multas/{id}/pagar-en-linea` | Pago rechazado por pasarela | `402 Payment Required`| Lanza `PagoRechazadoException` con mensaje del proveedor |

### Ejemplos con `curl`:

```bash
# 1. Generar multa
curl -X POST http://localhost:8080/api/multas \
  -H "Content-Type: application/json" \
  -d '{"estudianteId":"EST-11567","concepto":"Libro Arquitectura Limpia","diasAtraso":6}'
# Retorna 201 Created (monto = 3000)

# 2. Pagar en línea
curl -X POST http://localhost:8080/api/multas/1/pagar-en-linea
# Retorna 200 OK con estado PAGADA

# 3. Intentar pagar nuevamente
curl -X POST http://localhost:8080/api/multas/1/pagar-en-linea
# Retorna 409 Conflict
```

---

## 6. Cómo Ejecutar

### Prerrequisitos
- Java JDK 17 o superior instalado.
- Apache Maven 3.8 o superior.

### Comandos de Ejecución:
```bash
# Entrar a la carpeta del proyecto
cd multas-biblioteca-api

# Compilar y empaquetar
mvn clean package

# Ejecutar pruebas unitarias
mvn test

# Iniciar la aplicación Spring Boot
mvn spring-boot:run
```

Consola H2 disponible en: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:multas_biblioteca_db`, Usuario: `sa`, Contraseña en blanco).

---

## 7. Herramientas Utilizadas

- **Lenguaje:** Java 17 LTS
- **Framework:** Spring Boot 3.2.5 (Spring Web, Spring Data JPA, Spring Validation)
- **Base de Datos:** H2 Database (en memoria)
- **Cliente HTTP:** Spring RestTemplate
- **Pruebas:** JUnit 5, Mockito
- **Control de Versiones:** Git, GitHub

---

## 8. Conclusiones

1. La arquitectura en capas tradicional ofrece una separación limpia y predecible para flujos CRUD y casos de uso con persistencia relacional homogénea, siempre que se evite el modelo anémico delegando las reglas puras a la entidad y las reglas de agregación a la base de datos.
2. La introducción de un puerto de dominio con adaptadores (arquitectura hexagonal) en el módulo de pagos demostró ser la alternativa óptima ante integraciones externas heterogéneas, permitiendo conmutar entre pasarelas incompatibles en tiempo de arranque mediante configuración sin alterar las capas de servicio y presentación.
3. El dilema arquitectónico evidenció que los estilos no son excluyentes ni dogmáticos: adoptar un núcleo hexagonal focalizado sobre una arquitectura en capas existente equilibra eficazmente la simplicidad operativa con la extensibilidad ante requerimientos volátiles.
