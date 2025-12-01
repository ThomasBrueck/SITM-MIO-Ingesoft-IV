# Flujo de Workers para Cálculo de Tiempos de Ruta

## 🎯 Arquitectura Actualizada

### Flujo Completo del Sistema

```
┌─────────────────────────────────────────────────────────────────┐
│  FASE 1: Precálculo de Velocidades (Una vez)                   │
├─────────────────────────────────────────────────────────────────┤
│  Master + Workers → Procesan 100M datagramas                    │
│  ↓                                                              │
│  PostgreSQL: 2,971 arcos con velocidades promedio              │
│  (Filtra velocidades > 120 km/h como anomalías)                │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  FASE 2: Sistema de Consultas (Siempre)                        │
├─────────────────────────────────────────────────────────────────┤
│  1. Master carga arcos con avgSpeed desde PostgreSQL           │
│  2. Workers (PCs remotos) cargan los mismos datos               │
│  3. Cliente solicita ruta desde interfaz gráfica                │
│  4. Master delega cálculo a Worker disponible                   │
│  5. Worker calcula tiempo usando avgSpeed de PostgreSQL         │
│  6. Cliente muestra: distancia + tiempo estimado                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📋 Ejecución Paso a Paso

### **Paso 1: Iniciar Master (Servidor Principal)**

```bash
./gradlew runServer
```

**¿Qué hace?**
- ✅ Carga grafo base (líneas, paradas, arcos)
- ✅ Se conecta a PostgreSQL
- ✅ **Carga 2,971 velocidades precalculadas** → `GraphBuilder.loadArcSpeedsFromDatabase()`
- ✅ Cada `Arc` ahora tiene su `avgSpeed` (ej: 19.5 km/h)
- ✅ Espera que workers se conecten (puerto 10000)

**Mensaje esperado:**
```
[DB] Cargadas 2971 velocidades desde PostgreSQL
[DB] GlobalStats{totalArcs=2971, totalMeasurements=1435506, avgSpeed=19.68}
SERVIDOR LISTO PARA CONSULTAS
```

---

### **Paso 2: Iniciar Workers (PCs Remotos)**

#### **Worker en PC 1:**
```bash
./gradlew runWorker --args="10001 <IP_WORKER_1> <IP_MASTER>"
```

#### **Worker en PC 2:**
```bash
./gradlew runWorker --args="10002 <IP_WORKER_2> <IP_MASTER>"
```

**Ejemplo concreto:**
```bash
# Worker en PC con IP 192.168.1.100, conectándose a Master en 192.168.1.50
./gradlew runWorker --args="10001 192.168.1.100 192.168.1.50"
```

**¿Qué hace cada Worker?**
- ✅ Carga grafo base (igual que Master)
- ✅ **Carga velocidades desde PostgreSQL** → Mismo proceso que Master
- ✅ Cada worker tiene su propia copia del grafo con velocidades
- ✅ Se registra en el Master para recibir tareas de cálculo de rutas
- ✅ Espera solicitudes de cálculo

**Mensaje esperado:**
```
[Worker] Configuración:
  - Puerto: 10001
  - IP Worker: 192.168.1.100
  - IP Master: 192.168.1.50
[DB] Cargadas 2971 velocidades desde PostgreSQL
[Worker] ✓ Registrado exitosamente en el master para consultas de rutas.
[Worker] ACTIVO y esperando tareas en 192.168.1.100:10001...
```

---

### **Paso 3: Iniciar Cliente (Interfaz Gráfica)**

```bash
./gradlew runClient
```

**¿Qué hace?**
- ✅ Se conecta al Master (puerto 10000)
- ✅ Muestra interfaz JavaFX con mapa y formulario
- ✅ Usuario selecciona paradas de origen y destino
- ✅ Cliente solicita ruta al Master

---

### **Paso 4: Usuario Solicita Ruta**

**Flujo interno:**

```
1. Usuario selecciona: Parada A → Parada B
   ↓
2. Cliente llama: routeService.findRoute(A, B)
   ↓
3. Master recibe solicitud
   ↓
4. Master DELEGA a Worker disponible (Round-robin)
   ↓
5. Worker ejecuta:
   - Algoritmo BFS para encontrar ruta óptima
   - Suma distancias: arcos[0].distance + arcos[1].distance + ...
   - **CALCULA TIEMPO**: Σ (arc.distance / arc.avgSpeed) × 60 min
   ↓
6. Worker retorna RouteResult:
   {
     found: true,
     stops: [Stop1, Stop2, Stop3, ...],
     arcs: [Arc1, Arc2, Arc3, ...],
     totalDistance: 5.2 km,
     estimatedTime: 18.5 minutos,  ← CALCULADO POR WORKER
     numTransfers: 1,
     message: "Ruta encontrada"
   }
   ↓
7. Master reenvía resultado a Cliente
   ↓
8. Cliente muestra en interfaz:
   "Distancia: 5.2 km | Tiempo estimado: 18.5 min"
```

---

## 🔑 Componentes Clave

### **1. RouteResult (Actualizado en MioGraph.ice)**

```ice
struct RouteResult {
    bool found;
    StopList stops;
    ArcList arcs;
    double totalDistance;      // km
    double estimatedTime;      // minutos ← NUEVO CAMPO
    int numTransfers;
    string message;
}
```

### **2. RouteWorkerI.calculateEstimatedTime()**

```java
private double calculateEstimatedTime(List<Arc> arcs) {
    double totalTimeMinutes = 0.0;
    
    for (Arc arc : arcs) {
        if (arc.avgSpeed > 0) {
            // Velocidad desde PostgreSQL
            double timeHours = arc.distance / arc.avgSpeed;
            totalTimeMinutes += (timeHours * 60.0);
        } else {
            // Fallback: 15 km/h si no hay dato
            double timeHours = arc.distance / 15.0;
            totalTimeMinutes += (timeHours * 60.0);
        }
    }
    
    return totalTimeMinutes;
}
```

### **3. GraphBuilder.loadArcSpeedsFromDatabase()**

Este método se ejecuta **AUTOMÁTICAMENTE** en:
- ✅ Master al iniciar
- ✅ Cada Worker al iniciar

Carga las velocidades precalculadas desde PostgreSQL:

```java
for (ArcStat stat : dbStats) {
    Arc arc = findArc(stat.lineId, stat.orientation, stat.sequenceNum);
    if (arc != null) {
        arc.avgSpeed = stat.sumDistance / (stat.sumTime / 3600000.0);
    }
}
```

---

## ✅ Verificación de Funcionamiento

### **1. Verificar que Master cargó velocidades:**

Buscar en logs del Master:
```
[DB] Cargadas 2971 velocidades desde PostgreSQL
```

### **2. Verificar que Worker cargó velocidades:**

Buscar en logs de cada Worker:
```
[DB] Cargadas 2971 velocidades desde PostgreSQL
[Worker] ACTIVO y esperando tareas...
```

### **3. Verificar cálculo de ruta:**

Cuando cliente solicita ruta, buscar en logs del Worker:
```
[Worker] Ruta calculada: 5.2 km, 18.53 minutos estimados
[Worker] ✓ 8 arcos con velocidad de PostgreSQL
```

---

## 🌐 Configuración para PCs Remotos

### **Requisitos:**

1. **Todos los PCs deben tener:**
   - Java 21
   - Gradle
   - Copia del proyecto MIO
   - Archivo `database.properties` con credenciales de PostgreSQL

2. **Conectividad de red:**
   - Master debe estar accesible en puerto 10000
   - Workers deben ser accesibles en sus puertos (10001, 10002, etc.)
   - PostgreSQL en Railway accesible desde todos los PCs

3. **PostgreSQL en la nube (Railway):**
   - ✅ Ya configurado: `turntable.proxy.rlwy.net:28619`
   - ✅ Accesible desde cualquier PC con internet
   - ✅ Todos los workers comparten los mismos datos

---

## 🔄 Ventajas del Diseño

| Aspecto | Beneficio |
|---------|-----------|
| **Escalabilidad** | Agregar más workers = más capacidad de cálculo |
| **Sin recálculo** | Velocidades ya precalculadas en PostgreSQL |
| **Distribución** | Workers en diferentes PCs físicos |
| **Consistencia** | Todos usan los mismos datos (PostgreSQL compartido) |
| **Performance** | Cálculo de ruta < 1 segundo |
| **Tolerancia a fallos** | Si un worker falla, otros continúan |

---

## 📊 Datos Actuales en PostgreSQL

```sql
-- Total arcos con velocidades precalculadas
SELECT COUNT(*) FROM arc_stats;
-- Resultado: 2,971 arcos

-- Velocidad promedio global
SELECT AVG(avg_speed) FROM arc_stats WHERE avg_speed > 0;
-- Resultado: 19.68 km/h

-- Total mediciones acumuladas
SELECT SUM(count) FROM arc_stats;
-- Resultado: 1,435,506 mediciones
```

---

## 🚀 Próximos Pasos

1. ✅ Compilar proyecto: `./gradlew build`
2. ✅ Iniciar Master en PC principal
3. ✅ Iniciar Workers en otros PCs (usar IPs correctas)
4. ✅ Iniciar Cliente en PC del Master
5. ✅ Solicitar ruta desde interfaz
6. ✅ Verificar que muestra tiempo estimado calculado por workers
