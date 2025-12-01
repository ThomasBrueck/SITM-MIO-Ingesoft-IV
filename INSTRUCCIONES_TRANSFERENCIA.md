# ✅ Transferencia de la Carpeta mio/ a Otros PCs

## Respuesta Corta

**SÍ, solo necesitas copiar la carpeta `mio/` completa.** 

La carpeta contiene **TODO** lo necesario para ejecutar workers:
- ✅ Código fuente compilable
- ✅ Gradle wrapper (no necesitas instalar Gradle)
- ✅ Archivos de configuración
- ✅ Archivos de datos CSV
- ✅ Scripts de ejecución

---

## 📦 Contenido de la Carpeta mio/

```
mio/
├── gradlew              ✅ Script para Linux/Mac
├── gradlew.bat          ✅ Script para Windows
├── settings.gradle      ✅ Configuración de Gradle
├── gradle/              ✅ Gradle wrapper (incluido)
├── app/                 ✅ Código fuente
│   └── src/
│       └── main/
│           └── resources/
│               └── data/
│                   ├── lines-241.csv        ✅ Datos del grafo
│                   ├── stops-241.csv        ✅ Datos del grafo
│                   └── linestops-241.csv    ✅ Datos del grafo
├── data/
│   └── datagrams4history.csv  ✅ Archivo de datagramas a procesar
├── config/
│   ├── config.server          ✅ Configuración del Master
│   ├── config.worker          ✅ Configuración de Workers
│   └── database.properties    ✅ Configuración PostgreSQL
└── slice/
    └── MioGraph.ice           ✅ Definiciones Ice
```

**IMPORTANTE:** Los archivos CSV están en dos ubicaciones:
- `app/src/main/resources/data/` → Se copian al JAR al compilar
- `data/` → Para el archivo de datagramas

---

## 🚀 Métodos de Transferencia

### Método 1: Comprimir y Transferir (Recomendado)

```bash
# En el PC Master:
cd /home/tbrueck/Documents/Ingesoft\ IV/proyecto-final
tar -czf mio.tar.gz mio/

# Ver tamaño del archivo
ls -lh mio.tar.gz

# Transferir por USB, red, o SCP
# Ejemplo con SCP:
scp mio.tar.gz usuario@192.168.1.101:~

# En cada PC Worker:
tar -xzf mio.tar.gz
cd mio/
```

### Método 2: Clonar desde GitHub (Más Fácil)

```bash
# En cada PC Worker:
git clone https://github.com/ThomasBrueck/SITM-MIO-Ingesoft-IV.git
cd SITM-MIO-Ingesoft-IV/mio/
```

### Método 3: Sincronización con rsync

```bash
# Desde el PC Master a cada Worker:
rsync -avz --progress mio/ usuario@192.168.1.101:~/mio/
rsync -avz --progress mio/ usuario@192.168.1.102:~/mio/
```

---

## ⚙️ Configuración en el PC Worker

### Paso 1: Copiar la Carpeta

```bash
# Opción A: Descomprimir
tar -xzf mio.tar.gz

# Opción B: Clonar repositorio
git clone <url-repositorio>
```

### Paso 2: Verificar Java

```bash
java -version
# Debe mostrar: openjdk version "21.x.x"
```

### Paso 3: Verificar Archivos

```bash
cd mio/

# Verificar ejecutables
ls -lh gradlew gradlew.bat

# Verificar datos del grafo (dentro del código fuente)
ls -lh app/src/main/resources/data/*.csv

# Verificar datagramas
ls -lh data/datagrams4history.csv

# Dar permisos de ejecución (Linux/Mac)
chmod +x gradlew
```

### Paso 4: Ejecutar Worker

```bash
# Sintaxis:
./gradlew runWorker --args='<puerto> 0.0.0.0 <IP_Master>'

# Ejemplo:
./gradlew runWorker --args='10001 0.0.0.0 192.168.1.100'
```

---

## 🔍 Verificación Rápida

### Checklist en cada PC Worker:

```bash
# 1. ¿Java 21 instalado?
java -version

# 2. ¿Carpeta mio/ completa?
cd mio && ls
# Debe mostrar: gradlew, app/, data/, config/, etc.

# 3. ¿Archivos CSV presentes?
ls app/src/main/resources/data/*.csv
ls data/datagrams4history.csv

# 4. ¿Permisos de ejecución?
ls -l gradlew  # Debe tener 'x'

# 5. ¿Conectividad al Master?
ping <IP_Master>
```

---

## 🎯 Ejemplo Real: Setup de 3 Workers

### PC Master (192.168.1.100)

```bash
# 1. Comprimir proyecto
cd /home/tbrueck/Documents/Ingesoft\ IV/proyecto-final
tar -czf mio-workers.tar.gz mio/

# 2. Copiar a USB o transferir por red
cp mio-workers.tar.gz /media/usb/

# 3. Iniciar Master
cd mio/
./gradlew runServer
```

### PC Worker 1 (192.168.1.101)

```bash
# 1. Copiar desde USB
cp /media/usb/mio-workers.tar.gz ~
cd ~
tar -xzf mio-workers.tar.gz
cd mio/

# 2. Verificar Java
java -version  # ✅ 21.x.x

# 3. Ejecutar
chmod +x gradlew
./gradlew runWorker --args='10001 0.0.0.0 192.168.1.100'
```

### PC Worker 2 (192.168.1.102)

```bash
# Mismo proceso que Worker 1
./gradlew runWorker --args='10002 0.0.0.0 192.168.1.100'
```

### PC Worker 3 (192.168.1.103)

```bash
# Mismo proceso que Worker 1
./gradlew runWorker --args='10003 0.0.0.0 192.168.1.100'
```

---

## 📊 Tamaño Estimado

```bash
# Carpeta completa (con código fuente y datos)
du -sh mio/
# Aproximadamente: 50-100 MB

# Solo archivos necesarios (sin .git, sin build/)
tar -czf mio-clean.tar.gz \
  --exclude='.git' \
  --exclude='.gradle' \
  --exclude='app/build' \
  mio/
# Aproximadamente: 20-30 MB comprimido
```

---

## ❓ Preguntas Frecuentes

### ¿Necesito instalar Gradle en cada PC?
**NO.** El proyecto incluye `gradlew` (Gradle Wrapper) que descarga automáticamente la versión correcta de Gradle.

### ¿Necesito compilar el proyecto en cada Worker?
**NO directamente**, pero Gradle lo hará automáticamente la primera vez que ejecutes `./gradlew runWorker`.

### ¿Los workers necesitan conexión a Internet?
**Solo la primera vez** para que Gradle descargue dependencias. Después pueden funcionar offline.

### ¿Puedo usar Windows en algunos PCs y Linux en otros?
**SÍ.** El proyecto funciona en ambos:
- **Linux/Mac:** `./gradlew runWorker --args='...'`
- **Windows:** `gradlew.bat runWorker --args="..."`

### ¿Todos los workers deben tener el mismo archivo datagrams4history.csv?
**SÍ.** Aunque cada worker procesa solo una parte, el archivo completo debe estar presente en cada PC.

---

## ✅ Resumen Final

**Para ejecutar workers en otros PCs solo necesitas:**

1. 📁 **Copiar la carpeta `mio/` completa**
2. ☕ **Tener Java 21 instalado en cada PC**
3. 🌐 **Conocer la IP del Master**
4. 🚀 **Ejecutar:** `./gradlew runWorker --args='<puerto> 0.0.0.0 <IP_Master>'`

**No necesitas:**
- ❌ Instalar Gradle manualmente
- ❌ Configurar archivos adicionales
- ❌ Instalar bases de datos (solo el Master se conecta a PostgreSQL)
- ❌ Copiar archivos adicionales fuera de `mio/`

---

**¡Es así de simple! La carpeta `mio/` es completamente auto-contenida y portátil.** 🎯
