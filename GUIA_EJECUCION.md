# Guía de Ejecución - Sistema SITM-MIO

## 📋 Pre-requisitos

1. **Java 21** instalado
2. **PostgreSQL** (Railway) - Credenciales configuradas en `config/database.properties`
3. **Archivos de datos** en `data/`:
   - `lines-241.csv`
   - `stops-241.csv`
   - `linestops-241.csv`
   - `datagrams4history.csv`

---

## 🚀 Ejecución del Sistema Distribuido

### Paso 1: Crear las Tablas en PostgreSQL (Solo Primera Vez)

```bash
# Ejecutar el schema SQL
PGPASSWORD=aoDzBIiEQXYRBTBtuAHWFPzXmjPvvTRo psql \
  -h turntable.proxy.rlwy.net \
  -U postgres \
  -p 28619 \
  -d railway \
  -f database/schema.sql
```

**Verificar que las tablas se crearon:**
```bash
PGPASSWORD=aoDzBIiEQXYRBTBtuAHWFPzXmjPvvTRo psql \
  -h turntable.proxy.rlwy.net \
  -U postgres \
  -p 28619 \
  -d railway \
  -c "\dt"
```

Deberías ver:
- `arc_stats`
- `analysis_runs`
- `lines`
- `stops`
- `arcs_metadata`

---

### Paso 2: Iniciar el Master Server

**Terminal 1 - Master (PC Principal):**
```bash
./gradlew runServer
```

**Salida esperada:**
```
SISTEMA DE GRAFOS SITM-MIO
Universidad ICESI
Ingeniería de Software IV
Servidor ICE - Análisis de Rutas

=== Inicializando conexión a PostgreSQL ===
[DB] Conexión exitosa a PostgreSQL
[DB] Pool de conexiones: HikariPool-1 (10 conexiones)
[DB] Base de datos vacía, se crearán registros nuevos
============================================

SERVIDOR ICE ACTIVO
Servicios disponibles:
RouteService - Consultas de rutas y paradas
GraphService - Consultas del grafo completo

--- ESPERANDO WORKERS PARA ANÁLISIS ---
El servidor esperará 10 segundos para que los workers se registren...
```

**El servidor esperará 10 segundos para que los workers se conecten.**

---

### Paso 3: Iniciar Workers (Mismo PC - Para Pruebas)

**Terminal 2 - Worker 1:**
```bash
./gradlew runWorker --args='10001'
```

**Terminal 3 - Worker 2:**
```bash
./gradlew runWorker --args='10002'
```

**Terminal 4 - Worker 3 (Opcional):**
```bash
./gradlew runWorker --args='10003'
```

**Salida esperada de cada Worker:**
```
Worker iniciando en puerto 10001...
IP detectada automáticamente: 127.0.0.1
Registrándose en el Master: localhost:10000
Cargando datos del grafo...
Datos cargados: 100 rutas, 800 paradas
Worker registrado exitosamente en el Master
Worker activo y esperando tareas...
```

---

### Paso 4: Análisis Automático de Datagramas

Después de 10 segundos, el **Master** iniciará automáticamente el análisis:

```
--- INICIANDO ANÁLISIS DE DATAGRAMAS ---
Workers activos: 3
Contando líneas del archivo...
>> MODO EXPERIMENTO: Procesando solo 1,000,000 líneas <<
Total líneas a procesar: 1,000,000

[DB] Creado analysis_run: a1b2c3d4-...

Enviando tarea a Worker 0: líneas 1 a 333333
Enviando tarea a Worker 1: líneas 333334 a 666666
Enviando tarea a Worker 2: líneas 666667 a 1000000

--- Velocidades promedio por arco ---
Línea 1, Orientación 0, Secuencia 1: 25.30 km/h (1234 mediciones)
Línea 1, Orientación 0, Secuencia 2: 28.45 km/h (987 mediciones)
...

[DB] Insertados/actualizados 500 registros en arc_stats
[DB] Insertados/actualizados 500 registros en arc_stats
[DB] Insertados/actualizados 320 registros en arc_stats

=== ANÁLISIS COMPLETADO ===
Número de workers (nodos): 3
Líneas procesadas: 1,000,000
Tiempo total: 45000 ms
Velocidad de procesamiento: 22222.22 eventos/seg
================================

[DB] Experimento registrado en base de datos: a1b2c3d4-...

Servidor listo para recibir consultas del cliente visual.
```

---

### Paso 5: Iniciar Cliente Visual (Opcional)

**Terminal 5 - Cliente JavaFX:**
```bash
./gradlew runClient
```

Se abrirá la interfaz gráfica donde puedes:
- Consultar rutas entre paradas
- Ver velocidades promedio de arcos
- Visualizar el mapa en el navegador

---

## 🌐 Ejecución con Workers en PCs Diferentes

### En el PC del Master:

```bash
# 1. Obtener IP del Master
ip addr show | grep "inet " | grep -v 127.0.0.1

# Ejemplo: 192.168.1.100
```

### En cada PC Worker:

```bash
./gradlew runWorker --args='<puerto> 0.0.0.0 <IP_del_Master>'

# Ejemplos:
# Worker en PC 1:
./gradlew runWorker --args='10001 0.0.0.0 192.168.1.100'

# Worker en PC 2:
./gradlew runWorker --args='10002 0.0.0.0 192.168.1.100'

# Worker en PC 3:
./gradlew runWorker --args='10003 0.0.0.0 192.168.1.100'
```

**Requisitos:**
- Todos los PCs en la misma red
- Puerto 10000 del Master accesible
- Cada Worker debe tener los archivos CSV en `data/`

---

## 🔧 Configuración del Experimento

### Cambiar Tamaño del Dataset

Edita `app/src/main/java/mio/server/MioServer.java`:

```java
// Línea 27
private static final int EXPERIMENT_SIZE = 1_000_000; // 1M líneas

// Opciones:
// 1_000_000    → 1 millón de líneas
// 10_000_000   → 10 millones de líneas
// 100_000_000  → 100 millones de líneas
// -1           → Archivo completo
```

Luego recompila:
```bash
./gradlew build
```

---

## 📊 Verificar Datos en PostgreSQL

### Ver estadísticas guardadas:
```bash
PGPASSWORD=aoDzBIiEQXYRBTBtuAHWFPzXmjPvvTRo psql \
  -h turntable.proxy.rlwy.net \
  -U postgres \
  -p 28619 \
  -d railway \
  -c "SELECT COUNT(*) as total_arcos FROM arc_stats;"
```

### Ver experimentos realizados:
```bash
PGPASSWORD=aoDzBIiEQXYRBTBtuAHWFPzXmjPvvTRo psql \
  -h turntable.proxy.rlwy.net \
  -U postgres \
  -p 28619 \
  -d railway \
  -c "SELECT run_id, datagram_count, num_workers, processing_time_ms, status 
      FROM analysis_runs 
      ORDER BY start_time DESC 
      LIMIT 5;"
```

### Ver velocidad promedio global:
```bash
PGPASSWORD=aoDzBIiEQXYRBTBtuAHWFPzXmjPvvTRo psql \
  -h turntable.proxy.rlwy.net \
  -U postgres \
  -p 28619 \
  -d railway \
  -c "SELECT AVG(avg_speed) as velocidad_promedio_global 
      FROM arc_stats 
      WHERE avg_speed > 0;"
```

---

## ✅ Flujo Completo del Sistema

```
1. Master inicia → Conecta a PostgreSQL → Carga datos del grafo
                 ↓
2. Workers se registran → Cargan datos en memoria
                 ↓
3. Master divide trabajo → Envía chunks a cada worker
                 ↓
4. Workers procesan datagramas → Calculan estadísticas parciales
                 ↓
5. Master agrega resultados → Calcula velocidades promedio
                 ↓
6. Master persiste en PostgreSQL → arc_stats + analysis_runs
                 ↓
7. Próxima ejecución → GraphBuilder carga velocidades desde BD
                 ↓
8. Cliente consulta → Obtiene velocidades sin recalcular
```

---

## 🎯 Ventajas de la Persistencia

1. **Primera ejecución:** Calcula velocidades desde datagramas → Guarda en BD
2. **Siguientes ejecuciones:** Carga velocidades desde BD → No recalcula
3. **Consultas rápidas:** Cliente obtiene velocidades instantáneamente
4. **Tracking:** Todos los experimentos quedan registrados
5. **Análisis:** Comparar performance con diferentes números de workers

---

## 🐛 Solución de Problemas

### Error: "No se detectaron workers activos"
- Verifica que los workers se iniciaron antes de que termine el período de 10 segundos
- Revisa que los puertos no estén ocupados

### Error: "Connection refused" en workers
- Verifica la IP del Master
- Asegúrate de que el firewall permita conexiones en puerto 10000

### Error: "No se pudo conectar a PostgreSQL"
- Verifica las credenciales en `config/database.properties`
- Ejecuta el schema SQL si es la primera vez

### Error: "File not found: data/datagrams4history.csv"
- Asegúrate de que cada Worker tenga los archivos CSV en su carpeta `data/`

---

**¡Listo!** El sistema está configurado para almacenar y reutilizar las velocidades calculadas. 🚀
