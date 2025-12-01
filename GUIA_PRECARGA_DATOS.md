# Guía de Precarga de Datos en PostgreSQL

## 🎯 Objetivo

Ejecutar análisis de datagramas en escala de **100 millones de líneas** para precalcular y almacenar las velocidades promedio de todos los arcos en PostgreSQL.

---

## 📋 Pre-requisitos

1. ✅ PostgreSQL configurado (credenciales en `config/database.properties`)
2. ✅ Tablas creadas en la base de datos
3. ✅ Archivo `data/datagrams4history.csv` disponible
4. ✅ Al menos 2-3 PCs disponibles para workers (recomendado para 100M)

---

## 🔧 Paso 1: Crear Tablas en PostgreSQL

```bash
cd /home/tbrueck/Documents/Ingesoft\ IV/proyecto-final/mio

# Ejecutar schema SQL
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
- `arc_stats` → Almacenará velocidades promedio
- `analysis_runs` → Registrará cada experimento
- `lines`, `stops`, `arcs_metadata`

---

## ⚙️ Paso 2: Configurar Tamaño del Experimento

**Editar el tamaño en MioServer.java:**

El proyecto ya está configurado para **100M líneas**. Si quieres verificar o cambiar:

```bash
# Ver configuración actual
grep "EXPERIMENT_SIZE" app/src/main/java/mio/server/MioServer.java
```

Para cambiar el tamaño, edita la línea 32:
```java
private static final int EXPERIMENT_SIZE = 100_000_000; // 100M líneas
```

Opciones:
- `1_000_000` → 1 millón (prueba rápida)
- `10_000_000` → 10 millones
- `100_000_000` → 100 millones (escala completa)
- `-1` → Archivo completo

---

## 🚀 Paso 3: Compilar el Proyecto

```bash
./gradlew clean build -x test
```

---

## 💻 Paso 4: Ejecutar Análisis Distribuido

### Opción A: Local (Mismo PC - 3 Workers)

**Terminal 1 - Master:**
```bash
./gradlew runServer
```

Esperará 10 segundos para que se conecten los workers.

**Terminal 2 - Worker 1:**
```bash
./gradlew runWorker --args='10001'
```

**Terminal 3 - Worker 2:**
```bash
./gradlew runWorker --args='10002'
```

**Terminal 4 - Worker 3:**
```bash
./gradlew runWorker --args='10003'
```

### Opción B: Distribuido (Workers en Diferentes PCs)

**En el PC Master:**
```bash
# 1. Obtener IP del Master
ip addr show | grep "inet " | grep -v 127.0.0.1
# Ejemplo: 192.168.1.100

# 2. Iniciar Master
./gradlew runServer
```

**En cada PC Worker:**

Asegúrate de tener la carpeta `mio/` completa en cada PC, luego:

```bash
# Worker en PC 1:
./gradlew runWorker --args='10001 0.0.0.0 192.168.1.100'

# Worker en PC 2:
./gradlew runWorker --args='10002 0.0.0.0 192.168.1.100'

# Worker en PC 3:
./gradlew runWorker --args='10003 0.0.0.0 192.168.1.100'

# Worker en PC 4 (opcional):
./gradlew runWorker --args='10004 0.0.0.0 192.168.1.100'
```

---

## 📊 Paso 5: Monitorear el Progreso

El Master mostrará:

```
--- INICIANDO ANÁLISIS DE DATAGRAMAS ---
Workers activos: 3
>> MODO EXPERIMENTO: Procesando solo 100,000,000 líneas <<
Total líneas a procesar: 100,000,000

[DB] Creado analysis_run: a1b2c3d4-5678-...

Enviando tarea a Worker 0: líneas 1 a 33333333
Enviando tarea a Worker 1: líneas 33333334 a 66666666
Enviando tarea a Worker 2: líneas 66666667 a 100000000

Worker 0 procesando...
Worker 1 procesando...
Worker 2 procesando...

--- Velocidades promedio por arco ---
Línea 1, Orientación 0, Secuencia 1: 25.30 km/h (45678 mediciones)
Línea 1, Orientación 0, Secuencia 2: 28.45 km/h (38976 mediciones)
...

[DB] Insertados/actualizados 500 registros en arc_stats
[DB] Insertados/actualizados 500 registros en arc_stats
[DB] Insertados/actualizados 320 registros en arc_stats

=== ANÁLISIS COMPLETADO ===
Número de workers (nodos): 3
Líneas procesadas: 100,000,000
Tiempo total: 450000 ms (7.5 minutos)
Velocidad de procesamiento: 222222.22 eventos/seg
================================

[DB] Experimento registrado en base de datos: a1b2c3d4-...
```

---

## ✅ Paso 6: Verificar Datos en PostgreSQL

### Ver cantidad de arcos procesados:
```bash
PGPASSWORD=aoDzBIiEQXYRBTBtuAHWFPzXmjPvvTRo psql \
  -h turntable.proxy.rlwy.net \
  -U postgres \
  -p 28619 \
  -d railway \
  -c "SELECT 
        COUNT(*) as total_arcos,
        SUM(count) as total_mediciones,
        AVG(avg_speed) as velocidad_promedio_global
      FROM arc_stats
      WHERE avg_speed > 0;"
```

**Salida esperada:**
```
 total_arcos | total_mediciones | velocidad_promedio_global 
-------------+------------------+---------------------------
        1320 |        98456789  |            24.5678
```

### Ver experimentos realizados:
```bash
PGPASSWORD=aoDzBIiEQXYRBTBtuAHWFPzXmjPvvTRo psql \
  -h turntable.proxy.rlwy.net \
  -U postgres \
  -p 28619 \
  -d railway \
  -c "SELECT 
        run_id,
        datagram_count,
        num_workers,
        processing_time_ms / 1000.0 as time_seconds,
        arcs_processed,
        status,
        start_time
      FROM analysis_runs
      ORDER BY start_time DESC
      LIMIT 5;"
```

### Ver velocidades de arcos específicos:
```bash
PGPASSWORD=aoDzBIiEQXYRBTBtuAHWFPzXmjPvvTRo psql \
  -h turntable.proxy.rlwy.net \
  -U postgres \
  -p 28619 \
  -d railway \
  -c "SELECT 
        line_id,
        orientation,
        sequence_num,
        avg_speed,
        count as mediciones,
        last_updated
      FROM arc_stats
      WHERE line_id = 1 AND orientation = 0
      ORDER BY sequence_num
      LIMIT 10;"
```

---

## 🔄 Paso 7: Ejecutar Múltiples Escalas (Opcional)

Para cumplir con el requerimiento D (1M, 10M, 100M):

### Experimento 1: 1 Millón
```bash
# 1. Cambiar en MioServer.java:
#    EXPERIMENT_SIZE = 1_000_000

# 2. Recompilar
./gradlew build -x test

# 3. Ejecutar Master + Workers
# (Seguir Paso 4)

# 4. Verificar en BD
```

### Experimento 2: 10 Millones
```bash
# 1. Cambiar en MioServer.java:
#    EXPERIMENT_SIZE = 10_000_000

# 2. Recompilar y ejecutar
```

### Experimento 3: 100 Millones (Ya configurado)
```bash
# Ya está configurado con 100M
# Solo ejecutar Master + Workers
```

---

## 📈 Beneficios de la Precarga

Una vez completada la precarga:

### ✅ Consultas Instantáneas
El cliente puede consultar velocidades sin recalcular:
```bash
./gradlew runClient
# Consultar ruta entre paradas → Respuesta inmediata
```

### ✅ Reutilización de Datos
Siguientes ejecuciones del servidor:
```bash
./gradlew runServer
# Al iniciar, carga velocidades desde PostgreSQL
# No necesita procesar datagramas nuevamente
```

**Salida al cargar:**
```
[DB] Cargadas 1320 velocidades desde PostgreSQL
[DB] GlobalStats{arcos=1320, con datos=1320, mediciones=98456789, 
                 velocidad promedio=24.57 km/h, min=8.23, max=45.89}
```

### ✅ Análisis Comparativo
Puedes comparar performance con diferentes números de workers:
```sql
SELECT 
  num_workers,
  AVG(processing_time_ms / 1000.0) as avg_time_seconds,
  AVG(datagram_count::decimal / (processing_time_ms / 1000.0)) as avg_throughput
FROM analysis_runs
WHERE status = 'completed'
GROUP BY num_workers
ORDER BY num_workers;
```

---

## 🎯 Tiempo Estimado

### Con 3 Workers (mismo PC):
- **1M líneas:** ~30 segundos
- **10M líneas:** ~5 minutos
- **100M líneas:** ~50 minutos

### Con 3 Workers (PCs diferentes):
- **1M líneas:** ~15 segundos
- **10M líneas:** ~2.5 minutos  
- **100M líneas:** ~25 minutos

**Nota:** Tiempos aproximados, dependen del hardware.

---

## 🐛 Solución de Problemas

### Error: "No se detectaron workers"
- Iniciar workers ANTES de que pasen los 10 segundos
- Verificar conectividad de red

### Error: "Out of Memory"
- Aumentar memoria de la JVM:
  ```bash
  export GRADLE_OPTS="-Xmx4g"
  ./gradlew runServer
  ```

### Error: "Connection to PostgreSQL failed"
- Verificar credenciales en `config/database.properties`
- Verificar conectividad: `ping turntable.proxy.rlwy.net`

### Proceso muy lento
- Usar más workers distribuidos
- Verificar que el archivo CSV no esté en red lenta

---

## ✅ Checklist Final

Antes de considerar completada la precarga:

- [ ] Tablas creadas en PostgreSQL
- [ ] EXPERIMENT_SIZE configurado a 100M
- [ ] Análisis ejecutado exitosamente
- [ ] Master muestra "ANÁLISIS COMPLETADO"
- [ ] BD muestra: `SELECT COUNT(*) FROM arc_stats;` → > 1000 arcos
- [ ] BD muestra: `SELECT * FROM analysis_runs;` → Experimento registrado
- [ ] Cliente puede consultar velocidades sin delays

---

## 🎓 Resumen

**Para precarga completa de 100M datagramas:**

```bash
# 1. Crear tablas
PGPASSWORD=... psql ... -f database/schema.sql

# 2. Compilar (ya configurado para 100M)
./gradlew build -x test

# 3. Ejecutar Master (Terminal 1)
./gradlew runServer

# 4. Ejecutar Workers (Terminales 2, 3, 4...)
./gradlew runWorker --args='10001'
./gradlew runWorker --args='10002'
./gradlew runWorker --args='10003'

# 5. Esperar que termine (~25-50 min con 3 workers)

# 6. Verificar en BD
PGPASSWORD=... psql ... -c "SELECT COUNT(*) FROM arc_stats;"
```

**Resultado:** Todas las velocidades promedio almacenadas y listas para consulta instantánea. 🚀
