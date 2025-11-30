# Análisis de Patrones de Diseño - SITM-MIO

## Resumen Ejecutivo

El sistema **SITM-MIO** implementa una arquitectura distribuida para el análisis de rutas de transporte público. El análisis revela una implementación **sólida** de múltiples patrones de diseño con algunos aspectos que podrían mejorarse.

**Calificación General: 8.5/10** ⭐⭐⭐⭐

## Diagrama de Arquitectura

![Arquitectura y Patrones de Diseño](./architecture_patterns_diagram.png)


## 1. Patrones Arquitectónicos

### 1.1 Master-Worker Pattern ⭐⭐⭐⭐⭐

**Implementación: EXCELENTE**

**Ubicación:**
- Master: [GraphServiceI.java](file:///c:/Users/joshe/Desktop/SITM-MIO-Ingesoft-IV/app/src/main/java/mio/server/services/GraphServiceI.java)
- Worker: [RouteWorkerI.java](file:///c:/Users/joshe/Desktop/SITM-MIO-Ingesoft-IV/app/src/main/java/mio/server/worker/RouteWorkerI.java)
- Interface: [MioGraph.ice](file:///c:/Users/joshe/Desktop/SITM-MIO-Ingesoft-IV/slice/MioGraph.ice#L125-L131)

**Características:**
```java
// Master delega tareas a Workers usando Round-Robin
private RouteWorkerPrx getNextWorker() {
    synchronized(workers) {
        if (workers.isEmpty()) return null;
        int index = nextWorkerIndex.getAndIncrement() % workers.size();
        return workers.get(index);
    }
}
```

**Fortalezas:**
- ✅ **Balanceo de carga** con Round-Robin usando `AtomicInteger`
- ✅ **Fallback local** cuando no hay workers disponibles
- ✅ **Registro dinámico** de workers mediante `registerWorker()`
- ✅ **Manejo robusto de errores** con recuperación automática
- ✅ **Separación clara** de responsabilidades entre Master y Worker

**Áreas de Mejora:**
- ⚠️ No hay monitoreo de salud de workers (health checks)
- ⚠️ No se remueven workers que fallan permanentemente
- 💡 **Recomendación:** Implementar heartbeat y auto-remoción de workers inactivos

---

### 1.2 Client-Server Pattern ⭐⭐⭐⭐⭐

**Implementación: EXCELENTE**

**Ubicación:**
- Server: [MioServer.java](file:///c:/Users/joshe/Desktop/SITM-MIO-Ingesoft-IV/app/src/main/java/mio/server/MioServer.java)
- Client: [MioGraphClient.java](file:///c:/Users/joshe/Desktop/SITM-MIO-Ingesoft-IV/app/src/main/java/mio/client/MioGraphClient.java)

**Características:**
- ✅ **Comunicación remota** mediante ICE (ZeroC Ice)
- ✅ **Múltiples servicios** expuestos: `RouteService` y `GraphService`
- ✅ **Manejo de excepciones** personalizadas (`StopNotFoundException`, `LineNotFoundException`)
- ✅ **Configuración externa** mediante archivos de configuración

**Fortalezas:**
- Separación clara entre cliente y servidor
- Uso de proxies para abstracción de comunicación remota
- Manejo apropiado de ciclo de vida de conexiones

---

## 2. Patrones Creacionales

### 2.1 Factory Pattern ⭐⭐⭐⭐

**Implementación: BUENA**

**Ubicación:** [RepositoryFactory.java](file:///c:/Users/joshe/Desktop/SITM-MIO-Ingesoft-IV/app/src/main/java/mio/server/repository/RepositoryFactory.java)

```java
public static StopRepository createStopRepository(String type, String source) {
    if ("CSV".equalsIgnoreCase(type)) {
        return new CsvStopRepository(source);
    }
    throw new IllegalArgumentException("Tipo de repositorio no soportado: " + type);
}
```

**Fortalezas:**
- ✅ **Abstracción de creación** de repositorios
- ✅ **Fácil extensión** para nuevos tipos (JDBC, MongoDB, etc.)
- ✅ **Métodos estáticos** para simplicidad de uso
- ✅ **Validación de tipos** con mensajes de error claros

**Áreas de Mejora:**
- ⚠️ Uso de `if-else` en lugar de un patrón más escalable
- 💡 **Recomendación:** Usar un `Map<String, Supplier<Repository>>` para registro dinámico:

```java
private static final Map<String, Function<String, StopRepository>> FACTORIES = Map.of(
    "CSV", CsvStopRepository::new,
    "JDBC", JdbcStopRepository::new  // Fácil agregar nuevos
);

public static StopRepository createStopRepository(String type, String source) {
    Function<String, StopRepository> factory = FACTORIES.get(type.toUpperCase());
    if (factory == null) {
        throw new IllegalArgumentException("Tipo no soportado: " + type);
    }
    return factory.apply(source);
}
```

---

### 2.2 Dependency Injection ⭐⭐⭐⭐⭐

**Implementación: EXCELENTE**

**Ubicación:** [GraphBuilder.java](file:///c:/Users/joshe/Desktop/SITM-MIO-Ingesoft-IV/app/src/main/java/mio/server/data/GraphBuilder.java#L29-L38)

```java
public GraphBuilder(StopRepository stopRepository, 
                    LineRepository lineRepository, 
                    LineStopRepository lineStopRepository) {
    this.stopRepository = stopRepository;
    this.lineRepository = lineRepository;
    this.lineStopRepository = lineStopRepository;
    // ...
}
```

**Fortalezas:**
- ✅ **Inyección por constructor** (la forma más robusta)
- ✅ **Inversión de dependencias** - depende de interfaces, no implementaciones
- ✅ **Testabilidad** - fácil inyectar mocks para pruebas
- ✅ **Flexibilidad** - cambiar implementaciones sin modificar GraphBuilder

---

## 3. Patrones Estructurales

### 3.1 Repository Pattern ⭐⭐⭐⭐⭐

**Implementación: EXCELENTE**

**Ubicación:**
- Interface: [IRepository.java](file:///c:/Users/joshe/Desktop/SITM-MIO-Ingesoft-IV/app/src/main/java/mio/server/repository/IRepository.java)
- Implementación: [CsvStopRepository.java](file:///c:/Users/joshe/Desktop/SITM-MIO-Ingesoft-IV/app/src/main/java/mio/server/repository/impl/CsvStopRepository.java)

**Características:**
```java
public interface IRepository<T, ID> {
    List<T> findAll();
    Optional<T> findById(ID id);
}

// Implementaciones específicas
public interface StopRepository extends IRepository<Stop, Integer> {}
public interface LineRepository extends IRepository<Line, Integer> {}
public interface LineStopRepository extends IRepository<LineStopData, Integer> {}
```

**Fortalezas:**
- ✅ **Abstracción de acceso a datos** - oculta detalles de CSV
- ✅ **Uso de genéricos** para reutilización
- ✅ **Uso de Optional** para manejo seguro de nulls
- ✅ **Caché interno** en implementaciones CSV
- ✅ **Separación de concerns** - lógica de negocio vs acceso a datos

**Implementación destacada:**
```java
public class CsvStopRepository implements StopRepository {
    private final String filePath;
    private List<Stop> cache;  // ✅ Caché para optimización
    
    @Override
    public Optional<Stop> findById(Integer id) {
        return findAll().stream()
                .filter(s -> s.stopId == id)
                .findFirst();  // ✅ Uso idiomático de Streams
    }
}
```

---

### 3.2 Facade Pattern ⭐⭐⭐⭐

**Implementación: BUENA**

**Ubicación:** [MioGraphClient.java](file:///c:/Users/joshe/Desktop/SITM-MIO-Ingesoft-IV/app/src/main/java/mio/client/MioGraphClient.java)

**Características:**
- Proporciona una interfaz simplificada para interactuar con servicios ICE remotos
- Oculta la complejidad de proxies, comunicadores y manejo de errores

```java
public class MioGraphClient {
    private Communicator communicator;
    private RouteServicePrx routeService;
    private GraphServicePrx graphService;
    
    // Métodos simplificados que ocultan complejidad ICE
    public Line[] getAllLines() {
        try {
            return routeService.getAllLines();
        } catch (Exception e) {
            System.err.println("Error obteniendo rutas: " + e.getMessage());
            return new Line[0];  // ✅ Manejo graceful de errores
        }
    }
}
```

**Fortalezas:**
- ✅ Simplifica el uso de servicios remotos
- ✅ Manejo centralizado de errores
- ✅ Gestión del ciclo de vida de conexiones

---

### 3.3 Adapter Pattern (Implícito) ⭐⭐⭐

**Implementación: MODERADA**

**Ubicación:** [GraphBuilder.java](file:///c:/Users/joshe/Desktop/SITM-MIO-Ingesoft-IV/app/src/main/java/mio/server/data/GraphBuilder.java#L94-L136)

**Características:**
- Convierte datos de repositorios (modelos de datos) a estructuras ICE
- Método `buildArcs()` adapta `LineStopData` a objetos `Arc`

```java
private void buildArcs(List<LineStopData> lineStops) {
    // Agrupa y transforma datos del repositorio
    Map<String, List<LineStopData>> grouped = lineStops.stream()
        .collect(Collectors.groupingBy(ls -> 
            ls.getLineId() + "_" + ls.getLineVariant() + "_" + ls.getOrientation()));
    
    // Crea arcos (adaptación de formato)
    for (Map.Entry<String, List<LineStopData>> entry : grouped.entrySet()) {
        // ... construcción de Arc desde LineStopData
    }
}
```

---

## 4. Patrones Comportamentales

### 4.1 Strategy Pattern (Implícito) ⭐⭐⭐

**Implementación: MODERADA**

**Ubicación:** [PathFinder.java](file:///c:/Users/joshe/Desktop/SITM-MIO-Ingesoft-IV/app/src/main/java/mio/server/util/PathFinder.java)

**Características:**
- Algoritmo BFS encapsulado en métodos estáticos
- Podría mejorarse para permitir diferentes estrategias de búsqueda

**Mejora Sugerida:**
```java
public interface RouteStrategy {
    RouteResult findRoute(int origin, int dest, Map<Integer, Stop> stops, List<Arc> arcs);
}

public class BFSRouteStrategy implements RouteStrategy { /* ... */ }
public class DijkstraRouteStrategy implements RouteStrategy { /* ... */ }
public class AStarRouteStrategy implements RouteStrategy { /* ... */ }
```

---

### 4.2 Template Method Pattern ⭐⭐⭐⭐

**Implementación: BUENA**

**Ubicación:** Repositorios CSV

**Características:**
- Estructura común para leer CSV y convertir a objetos
- Cada repositorio implementa la conversión específica

```java
// Patrón común en todos los repositorios CSV
@Override
public List<T> findAll() {
    if (cache != null) return cache;  // Template step 1: Check cache
    
    try {
        List<DataModel> dataList = CSVReader.readXXX(filePath);  // Step 2: Read
        cache = new ArrayList<>();
        for (DataModel data : dataList) {
            cache.add(convertToEntity(data));  // Step 3: Convert (específico)
        }
        return cache;
    } catch (IOException e) {
        throw new RuntimeException("Error leyendo CSV", e);
    }
}
```

---

## 5. Otros Patrones y Prácticas

### 5.1 Singleton (Anti-Pattern Evitado) ⭐⭐⭐⭐⭐

**Fortaleza:**
- ✅ **No se usa Singleton** innecesariamente
- ✅ Se prefiere **inyección de dependencias**
- ✅ Mejor testabilidad y mantenibilidad

---

### 5.2 Utility Class Pattern ⭐⭐⭐⭐

**Ubicación:** [PathFinder.java](file:///c:/Users/joshe/Desktop/SITM-MIO-Ingesoft-IV/app/src/main/java/mio/server/util/PathFinder.java)

```java
public class PathFinder {
    // ✅ Métodos estáticos sin estado
    public static Map<String, Object> findShortestRoute(...) { }
    public static Set<Integer> findReachableStops(...) { }
}
```

**Fortalezas:**
- ✅ Métodos estáticos puros (sin efectos secundarios)
- ✅ Reutilización de algoritmos
- ✅ Separación de lógica algorítmica

**Mejora Sugerida:**
- 💡 Hacer la clase `final` y agregar constructor privado para prevenir instanciación

---

## 6. Principios SOLID

### 6.1 Single Responsibility Principle (SRP) ⭐⭐⭐⭐⭐

**Cumplimiento: EXCELENTE**

- ✅ `GraphBuilder` - solo construye el grafo
- ✅ `PathFinder` - solo algoritmos de búsqueda
- ✅ `RepositoryFactory` - solo creación de repositorios
- ✅ `GraphServiceI` - solo coordinación Master-Worker
- ✅ `RouteWorkerI` - solo ejecución de tareas

---

### 6.2 Open/Closed Principle (OCP) ⭐⭐⭐⭐

**Cumplimiento: BUENO**

**Fortalezas:**
- ✅ Fácil agregar nuevos tipos de repositorios (JDBC, MongoDB)
- ✅ Fácil agregar nuevos servicios ICE
- ✅ Interfaces bien definidas

**Mejora:**
- ⚠️ Factory usa `if-else` en lugar de registro dinámico

---

### 6.3 Liskov Substitution Principle (LSP) ⭐⭐⭐⭐⭐

**Cumplimiento: EXCELENTE**

- ✅ Todas las implementaciones de `IRepository` son intercambiables
- ✅ Uso correcto de interfaces

---

### 6.4 Interface Segregation Principle (ISP) ⭐⭐⭐⭐

**Cumplimiento: BUENO**

- ✅ Interfaces específicas: `StopRepository`, `LineRepository`, `LineStopRepository`
- ✅ Servicios ICE separados: `RouteService` vs `GraphService`

---

### 6.5 Dependency Inversion Principle (DIP) ⭐⭐⭐⭐⭐

**Cumplimiento: EXCELENTE**

```java
// ✅ GraphBuilder depende de interfaces, no implementaciones
public GraphBuilder(StopRepository stopRepository,  // Interface
                    LineRepository lineRepository,   // Interface
                    LineStopRepository lineStopRepository) { }
```

---

## 7. Calidad de Implementación

### 7.1 Manejo de Errores ⭐⭐⭐⭐⭐

**Excelente:**
- ✅ Excepciones personalizadas en ICE
- ✅ Validaciones apropiadas
- ✅ Mensajes de error descriptivos
- ✅ Fallback en Master-Worker

```java
if (!graphBuilder.getStopsMap().containsKey(originStopId)) {
    StopNotFoundException ex = new StopNotFoundException();
    ex.stopId = originStopId;
    ex.message = "Parada de origen no encontrada: " + originStopId;
    throw ex;
}
```

---

### 7.2 Concurrencia ⭐⭐⭐⭐

**Buena:**
- ✅ Uso de `AtomicInteger` para Round-Robin
- ✅ Sincronización en lista de workers
- ✅ Thread-safe

```java
private RouteWorkerPrx getNextWorker() {
    synchronized(workers) {  // ✅ Protección de acceso concurrente
        if (workers.isEmpty()) return null;
        int index = nextWorkerIndex.getAndIncrement() % workers.size();
        return workers.get(index);
    }
}
```

---

### 7.3 Código Limpio ⭐⭐⭐⭐⭐

**Excelente:**
- ✅ Nombres descriptivos
- ✅ Métodos cortos y enfocados
- ✅ Comentarios apropiados
- ✅ Formato consistente

---

## 8. Resumen de Patrones Identificados

| Patrón | Calificación | Ubicación Principal |
|--------|--------------|---------------------|
| **Master-Worker** | ⭐⭐⭐⭐⭐ | GraphServiceI, RouteWorkerI |
| **Client-Server** | ⭐⭐⭐⭐⭐ | MioServer, MioGraphClient |
| **Repository** | ⭐⭐⭐⭐⭐ | IRepository, CsvXxxRepository |
| **Factory** | ⭐⭐⭐⭐ | RepositoryFactory |
| **Dependency Injection** | ⭐⭐⭐⭐⭐ | GraphBuilder, Services |
| **Facade** | ⭐⭐⭐⭐ | MioGraphClient |
| **Adapter** | ⭐⭐⭐ | GraphBuilder.buildArcs() |
| **Template Method** | ⭐⭐⭐⭐ | Repositorios CSV |
| **Strategy** (potencial) | ⭐⭐⭐ | PathFinder |
| **Utility Class** | ⭐⭐⭐⭐ | PathFinder |

---

## 9. Recomendaciones de Mejora

### 9.1 Prioridad Alta 🔴

1. **Health Checks para Workers**
   ```java
   public interface RouteWorker {
       RouteResult findRoute(...);
       boolean isHealthy();  // ⭐ Nuevo método
   }
   ```

2. **Mejora del Factory Pattern**
   - Usar registro dinámico en lugar de `if-else`
   - Permitir registro de nuevos tipos en runtime

### 9.2 Prioridad Media 🟡

3. **Strategy Pattern para Algoritmos de Búsqueda**
   - Permitir diferentes algoritmos (BFS, Dijkstra, A*)
   - Configuración dinámica de estrategia

4. **Métricas y Monitoreo**
   - Tiempo de respuesta de workers
   - Tasa de éxito/fallo
   - Distribución de carga

### 9.3 Prioridad Baja 🟢

5. **Caché Distribuido**
   - Compartir resultados entre workers
   - Reducir cálculos redundantes

6. **Circuit Breaker Pattern**
   - Protección contra workers que fallan repetidamente

---

## 10. Conclusión

El código del sistema SITM-MIO demuestra una **arquitectura bien diseñada** con implementaciones sólidas de patrones de diseño fundamentales. Los puntos más destacados son:

### Fortalezas Principales ✅
1. **Excelente separación de concerns** mediante Repository Pattern
2. **Arquitectura Master-Worker robusta** con fallback
3. **Fuerte adherencia a principios SOLID**
4. **Inyección de dependencias consistente**
5. **Manejo de errores profesional**

### Áreas de Oportunidad 💡
1. Monitoreo de salud de workers
2. Estrategias de búsqueda intercambiables
3. Mejoras en el Factory Pattern

### Calificación Final: **8.5/10** ⭐⭐⭐⭐

El sistema está **listo para producción** con las mejoras sugeridas como optimizaciones futuras.
