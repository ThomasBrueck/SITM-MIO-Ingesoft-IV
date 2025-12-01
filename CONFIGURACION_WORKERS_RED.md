# Configuración de Workers en PCs Diferentes

## 📦 Requisitos en Cada PC Worker

### 1. Software Necesario
- **Java 21** (JDK)
- **Gradle** (incluido en el proyecto con `gradlew`)

### 2. Archivos del Proyecto Necesarios

Cada PC Worker necesita una **copia completa del proyecto**, específicamente:

```
mio/
├── app/                          ✅ NECESARIO (código compilado)
├── gradle/                       ✅ NECESARIO (wrapper de Gradle)
├── gradlew                       ✅ NECESARIO (script de ejecución Linux/Mac)
├── gradlew.bat                   ✅ NECESARIO (script de ejecución Windows)
├── settings.gradle               ✅ NECESARIO
├── config/
│   └── config.worker             ✅ NECESARIO (configuración Ice del worker)
└── data/                         ✅ NECESARIO
    ├── lines-241.csv             ✅ Datos del grafo
    ├── stops-241.csv             ✅ Datos del grafo
    ├── linestops-241.csv         ✅ Datos del grafo
    └── datagrams4history.csv     ✅ Archivo a analizar
```

**IMPORTANTE:** Cada worker debe tener los mismos archivos CSV para poder procesar su parte del análisis.

### 3. Configuración de Red

#### En el PC Master:
```bash
# Obtener la IP del Master
ip addr show | grep "inet " | grep -v 127.0.0.1
# O en Windows: ipconfig

# Ejemplo de salida: 192.168.1.100
```

**Asegúrate que el firewall permita conexiones en el puerto 10000**

#### En cada PC Worker:
- Misma red que el Master
- Conectividad al puerto 10000 del Master

---

## 🚀 Proceso de Instalación en un PC Worker

### Opción 1: Clonar el Repositorio (Recomendado)

```bash
# En cada PC Worker
git clone https://github.com/ThomasBrueck/SITM-MIO-Ingesoft-IV.git
cd SITM-MIO-Ingesoft-IV/mio
```

### Opción 2: Copiar Archivos Manualmente

```bash
# En el PC Master (comprimir proyecto)
cd /home/tbrueck/Documents/Ingesoft\ IV/proyecto-final
tar -czf mio-worker.tar.gz mio/

# Transferir a cada PC Worker (USB, SCP, etc.)
scp mio-worker.tar.gz usuario@192.168.1.101:~

# En cada PC Worker (descomprimir)
tar -xzf mio-worker.tar.gz
cd mio/
```

---

## 🔧 Ejecución en PC Workers

### Comando Completo en Worker

```bash
./gradlew runWorker --args='<PUERTO_WORKER> 0.0.0.0 <IP_DEL_MASTER>'
```

### Parámetros Explicados:

1. **`<PUERTO_WORKER>`**: Puerto único para este worker (10001, 10002, etc.)
2. **`0.0.0.0`**: IP del worker (0.0.0.0 = detecta automáticamente)
3. **`<IP_DEL_MASTER>`**: IP del PC donde corre el Master

### Ejemplos Reales:

**Escenario: Master en 192.168.1.100**

```bash
# PC Worker 1 (192.168.1.101)
./gradlew runWorker --args='10001 0.0.0.0 192.168.1.100'

# PC Worker 2 (192.168.1.102)
./gradlew runWorker --args='10002 0.0.0.0 192.168.1.100'

# PC Worker 3 (192.168.1.103)
./gradlew runWorker --args='10003 0.0.0.0 192.168.1.100'
```

---

## 📋 Checklist Antes de Ejecutar

### En el PC Master:
- [ ] Master corriendo con `./gradlew runServer`
- [ ] Firewall permite conexiones en puerto 10000
- [ ] IP del Master conocida (ejemplo: 192.168.1.100)

### En cada PC Worker:
- [ ] Java 21 instalado: `java -version`
- [ ] Proyecto completo copiado
- [ ] Archivos CSV presentes en `data/`
- [ ] Conectividad de red al Master: `ping 192.168.1.100`
- [ ] Puerto del worker libre (10001, 10002, etc.)

---

## 🧪 Prueba de Conectividad

### Desde el PC Worker:

```bash
# Verificar conectividad al Master
ping 192.168.1.100

# Verificar que el puerto 10000 esté accesible
telnet 192.168.1.100 10000
# O con nc:
nc -zv 192.168.1.100 10000
```

---

## 🎯 Ejemplo Completo: Setup de 3 PCs

### **PC 1 - Master (IP: 192.168.1.100)**

```bash
cd /home/user/mio
./gradlew runServer

# Salida:
SERVIDOR ICE ACTIVO
--- ESPERANDO WORKERS PARA ANÁLISIS ---
El servidor esperará 10 segundos...
```

### **PC 2 - Worker 1 (IP: 192.168.1.101)**

```bash
cd ~/mio
./gradlew runWorker --args='10001 0.0.0.0 192.168.1.100'

# Salida:
Worker iniciando en puerto 10001...
IP detectada automáticamente: 192.168.1.101
Registrándose en el Master: 192.168.1.100:10000
Worker registrado exitosamente
Worker activo y esperando tareas...
```

### **PC 3 - Worker 2 (IP: 192.168.1.102)**

```bash
cd ~/mio
./gradlew runWorker --args='10002 0.0.0.0 192.168.1.100'

# Salida:
Worker iniciando en puerto 10002...
IP detectada automáticamente: 192.168.1.102
Registrándose en el Master: 192.168.1.100:10000
Worker registrado exitosamente
Worker activo y esperando tareas...
```

### **PC 4 - Worker 3 (IP: 192.168.1.103)**

```bash
cd ~/mio
./gradlew runWorker --args='10003 0.0.0.0 192.168.1.100'
```

### **Resultado en el Master:**

```
--- INICIANDO ANÁLISIS DE DATAGRAMAS ---
Workers activos: 3
Worker detectado: AnalysisWorker:tcp -h 192.168.1.101 -p 10001
Worker detectado: AnalysisWorker:tcp -h 192.168.1.102 -p 10002
Worker detectado: AnalysisWorker:tcp -h 192.168.1.103 -p 10003

Enviando tarea a Worker 0: líneas 1 a 333333
Enviando tarea a Worker 1: líneas 333334 a 666666
Enviando tarea a Worker 2: líneas 666667 a 1000000
```

---

## 🐛 Solución de Problemas Comunes

### Error: "Connection refused to Master"
**Causa:** Master no accesible desde el Worker

**Solución:**
```bash
# 1. Verificar que el Master esté corriendo
# 2. Verificar firewall en el Master
sudo ufw allow 10000/tcp

# 3. Verificar conectividad
ping <IP_MASTER>
nc -zv <IP_MASTER> 10000
```

### Error: "Address already in use"
**Causa:** Puerto del worker ya está ocupado

**Solución:**
```bash
# Usar otro puerto
./gradlew runWorker --args='10004 0.0.0.0 192.168.1.100'

# O liberar el puerto
lsof -ti:10001 | xargs kill -9
```

### Error: "File not found: data/datagrams4history.csv"
**Causa:** Archivos CSV no están en el worker

**Solución:**
```bash
# Verificar que existan
ls -lh data/

# Si faltan, copiarlos del Master
scp master@192.168.1.100:~/mio/data/*.csv data/
```

### Error: "Worker not detected by Master"
**Causa:** Worker se inició después del período de espera de 10 segundos

**Solución:**
1. Iniciar todos los workers primero
2. Luego iniciar el Master
3. O aumentar el tiempo de espera en `MioServer.java` (línea ~100)

---

## 📊 Configuración Óptima por Número de PCs

### 2 PCs (Master + 1 Worker)
```bash
# Master PC:
./gradlew runServer

# Worker PC:
./gradlew runWorker --args='10001 0.0.0.0 <IP_MASTER>'
```

### 3 PCs (Master + 2 Workers)
```bash
# Master puede ejecutar también un worker local
# PC Master:
./gradlew runServer
# (en otra terminal del mismo PC)
./gradlew runWorker --args='10001'

# PC Worker 1:
./gradlew runWorker --args='10002 0.0.0.0 <IP_MASTER>'
```

### 4+ PCs (Master + 3+ Workers)
- Master dedicado solo a coordinar
- Cada PC adicional ejecuta un worker

---

## 🎓 Resumen Ejecutivo

**Lo que cada PC Worker necesita:**
1. ✅ Copia completa del proyecto `mio/`
2. ✅ Java 21 instalado
3. ✅ Archivos CSV en `data/`
4. ✅ Conectividad de red al Master
5. ✅ Comando: `./gradlew runWorker --args='<puerto> 0.0.0.0 <IP_Master>'`

**El proyecto ya está preparado para distribución:**
- ✅ Auto-detección de IP del worker
- ✅ Registro automático en el Master
- ✅ Procesamiento de rutas relativas de archivos
- ✅ Cada worker carga su propio grafo en memoria

**No se necesita configuración adicional de archivos**, solo:
1. Copiar el proyecto
2. Tener los CSV
3. Ejecutar con la IP del Master

---

🚀 **¡Listo para ejecutar en red distribuida!**
