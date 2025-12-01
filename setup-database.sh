#!/bin/bash

# Script para Precarga de Datos en PostgreSQL
# Ejecuta análisis de datagramas en diferentes escalas y persiste en BD

echo "=============================================="
echo "   PRECARGA DE DATOS - SISTEMA SITM-MIO"
echo "=============================================="
echo ""

# Colores para output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Verificar Java
echo -e "${BLUE}[1/6] Verificando Java...${NC}"
if ! java -version 2>&1 | grep -q "21"; then
    echo -e "${YELLOW}WARNING: Se requiere Java 21${NC}"
    java -version
fi
echo ""

# Verificar PostgreSQL
echo -e "${BLUE}[2/6] Verificando conexión a PostgreSQL...${NC}"
PGPASSWORD=aoDzBIiEQXYRBTBtuAHWFPzXmjPvvTRo psql \
  -h turntable.proxy.rlwy.net \
  -U postgres \
  -p 28619 \
  -d railway \
  -c "SELECT 'Conexión exitosa' as status;" 2>/dev/null

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Conexión a PostgreSQL exitosa${NC}"
else
    echo -e "${YELLOW}⚠ No se pudo verificar conexión. Continuando...${NC}"
fi
echo ""

# Ejecutar schema SQL si las tablas no existen
echo -e "${BLUE}[3/6] Verificando tablas en PostgreSQL...${NC}"
TABLE_COUNT=$(PGPASSWORD=aoDzBIiEQXYRBTBtuAHWFPzXmjPvvTRo psql \
  -h turntable.proxy.rlwy.net \
  -U postgres \
  -p 28619 \
  -d railway \
  -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'arc_stats';" 2>/dev/null | tr -d ' ')

if [ "$TABLE_COUNT" = "0" ]; then
    echo "Creando tablas..."
    PGPASSWORD=aoDzBIiEQXYRBTBtuAHWFPzXmjPvvTRo psql \
      -h turntable.proxy.rlwy.net \
      -U postgres \
      -p 28619 \
      -d railway \
      -f database/schema.sql > /dev/null 2>&1
    echo -e "${GREEN}✓ Tablas creadas${NC}"
else
    echo -e "${GREEN}✓ Tablas ya existen${NC}"
fi
echo ""

# Compilar proyecto
echo -e "${BLUE}[4/6] Compilando proyecto...${NC}"
./gradlew build -x test > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Compilación exitosa${NC}"
else
    echo -e "${YELLOW}⚠ Error en compilación. Revisar logs.${NC}"
fi
echo ""

# Instrucciones para ejecución manual
echo -e "${BLUE}[5/6] Preparando ejecución del análisis...${NC}"
echo ""
echo "=============================================="
echo "   INSTRUCCIONES DE EJECUCIÓN"
echo "=============================================="
echo ""
echo -e "${YELLOW}IMPORTANTE: El análisis requiere ejecutar Master + Workers${NC}"
echo ""
echo "Opción 1: Ejecución Local (3 workers en mismo PC)"
echo "---------------------------------------------------"
echo "Terminal 1 (Master):"
echo "  ./gradlew runServer"
echo ""
echo "Terminal 2 (Worker 1):"
echo "  ./gradlew runWorker --args='10001'"
echo ""
echo "Terminal 3 (Worker 2):"
echo "  ./gradlew runWorker --args='10002'"
echo ""
echo "Terminal 4 (Worker 3):"
echo "  ./gradlew runWorker --args='10003'"
echo ""
echo "---------------------------------------------------"
echo ""
echo "Opción 2: Ejecución Distribuida (workers en otros PCs)"
echo "---------------------------------------------------"
echo "En PC Master:"
echo "  1. Obtener IP: ip addr show | grep 'inet ' | grep -v 127.0.0.1"
echo "  2. ./gradlew runServer"
echo ""
echo "En cada PC Worker:"
echo "  ./gradlew runWorker --args='<puerto> 0.0.0.0 <IP_Master>'"
echo "  Ejemplo: ./gradlew runWorker --args='10001 0.0.0.0 192.168.1.100'"
echo ""
echo "=============================================="
echo ""
echo -e "${BLUE}[6/6] Configuración actual del experimento:${NC}"
echo ""
grep "EXPERIMENT_SIZE" app/src/main/java/mio/server/MioServer.java | grep "private static"
echo ""
echo -e "${GREEN}Para cambiar el tamaño del experimento:${NC}"
echo "  Editar: app/src/main/java/mio/server/MioServer.java"
echo "  Línea: private static final int EXPERIMENT_SIZE = ..."
echo "  Opciones: 1_000_000 (1M) | 10_000_000 (10M) | 100_000_000 (100M)"
echo ""
echo "=============================================="
echo -e "${GREEN}✓ Setup completo. Listo para ejecutar análisis.${NC}"
echo "=============================================="
echo ""
echo "Los resultados se guardarán automáticamente en PostgreSQL:"
echo "  - Tabla 'arc_stats': Velocidades promedio por arco"
echo "  - Tabla 'analysis_runs': Métricas de cada experimento"
echo ""
