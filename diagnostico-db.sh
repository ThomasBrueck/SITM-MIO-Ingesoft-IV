#!/bin/bash

echo "=========================================="
echo "  DIAGNÓSTICO DE PERSISTENCIA PostgreSQL"
echo "=========================================="
echo ""

PGPASSWORD=aoDzBIiEQXYRBTBtuAHWFPzXmjPvvTRo
PGHOST=turntable.proxy.rlwy.net
PGPORT=28619
PGUSER=postgres
PGDATABASE=railway

echo "[1] Verificando conexión a PostgreSQL..."
if psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -c "SELECT version();" > /dev/null 2>&1; then
    echo "✅ Conexión exitosa"
else
    echo "❌ Error de conexión a PostgreSQL"
    echo "Verifica las credenciales en config/database.properties"
    exit 1
fi
echo ""

echo "[2] Verificando existencia de tablas..."
TABLES=$(psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN ('arc_stats', 'analysis_runs');" 2>/dev/null | tr -d ' ')

if [ "$TABLES" = "2" ]; then
    echo "✅ Tablas arc_stats y analysis_runs existen"
else
    echo "❌ Tablas NO encontradas. Ejecutando schema.sql..."
    psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -f database/schema.sql > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo "✅ Tablas creadas exitosamente"
    else
        echo "❌ Error creando tablas"
        exit 1
    fi
fi
echo ""

echo "[3] Verificando datos en arc_stats..."
ARC_COUNT=$(psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -t -c "SELECT COUNT(*) FROM arc_stats;" 2>/dev/null | tr -d ' ')
echo "   Total de arcos en BD: $ARC_COUNT"

if [ "$ARC_COUNT" = "0" ]; then
    echo "⚠️  No hay datos. El análisis no guardó en la BD."
else
    echo "✅ Datos encontrados"
    echo ""
    echo "   Estadísticas:"
    psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -c "
        SELECT 
            COUNT(*) as arcos_totales,
            SUM(count) as mediciones_totales,
            ROUND(AVG(avg_speed)::numeric, 2) as velocidad_promedio
        FROM arc_stats 
        WHERE avg_speed > 0;
    " 2>/dev/null
fi
echo ""

echo "[4] Verificando experimentos registrados..."
RUN_COUNT=$(psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -t -c "SELECT COUNT(*) FROM analysis_runs;" 2>/dev/null | tr -d ' ')
echo "   Total de experimentos: $RUN_COUNT"

if [ "$RUN_COUNT" = "0" ]; then
    echo "⚠️  No hay experimentos registrados"
else
    echo "✅ Experimentos encontrados"
    echo ""
    echo "   Últimos 3 experimentos:"
    psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -c "
        SELECT 
            LEFT(run_id::text, 8) as run_id,
            datagram_count,
            num_workers,
            ROUND((processing_time_ms / 1000.0)::numeric, 2) as time_sec,
            arcs_processed,
            status,
            start_time
        FROM analysis_runs 
        ORDER BY start_time DESC 
        LIMIT 3;
    " 2>/dev/null
fi
echo ""

echo "=========================================="
echo "  DIAGNÓSTICO COMPLETADO"
echo "=========================================="
