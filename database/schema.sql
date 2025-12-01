-- ============================================================================
-- Schema de Base de Datos - Sistema SITM-MIO
-- Universidad ICESI - Ingeniería de Software IV
-- ============================================================================

-- Crear extensiones necesarias
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================================
-- Tabla: lines (Líneas/Rutas del sistema)
-- ============================================================================
CREATE TABLE IF NOT EXISTS lines (
    line_id INT PRIMARY KEY,
    plan_version_id INT NOT NULL,
    short_name VARCHAR(50) NOT NULL,
    description TEXT,
    activation_date VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_lines_short_name ON lines(short_name);

-- ============================================================================
-- Tabla: stops (Paradas del sistema)
-- ============================================================================
CREATE TABLE IF NOT EXISTS stops (
    stop_id INT PRIMARY KEY,
    plan_version_id INT NOT NULL,
    short_name VARCHAR(50) NOT NULL,
    long_name VARCHAR(255),
    gps_x BIGINT,
    gps_y BIGINT,
    decimal_lat DOUBLE PRECISION NOT NULL,
    decimal_lon DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stops_short_name ON stops(short_name);
CREATE INDEX IF NOT EXISTS idx_stops_location ON stops(decimal_lat, decimal_lon);

-- ============================================================================
-- Tabla: arc_stats (Estadísticas de velocidades por arco)
-- Esta es la tabla PRINCIPAL para almacenar los cálculos
-- ============================================================================
CREATE TABLE IF NOT EXISTS arc_stats (
    line_id INT NOT NULL,
    orientation INT NOT NULL CHECK (orientation IN (0, 1)),
    sequence_num INT NOT NULL,
    sum_distance DOUBLE PRECISION NOT NULL DEFAULT 0,
    sum_time BIGINT NOT NULL DEFAULT 0,
    count INT NOT NULL DEFAULT 0,
    avg_speed DOUBLE PRECISION GENERATED ALWAYS AS 
        (CASE 
            WHEN sum_time > 0 THEN sum_distance / (sum_time / 3600000.0) 
            ELSE 0 
        END) STORED,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    analysis_run_id UUID,
    PRIMARY KEY (line_id, orientation, sequence_num)
);

-- Índices para consultas rápidas
CREATE INDEX IF NOT EXISTS idx_arc_stats_line ON arc_stats(line_id, orientation);
CREATE INDEX IF NOT EXISTS idx_arc_stats_updated ON arc_stats(last_updated);
CREATE INDEX IF NOT EXISTS idx_arc_stats_speed ON arc_stats(avg_speed) WHERE avg_speed > 0;
CREATE INDEX IF NOT EXISTS idx_arc_stats_run ON arc_stats(analysis_run_id);

-- Foreign key constraint (DESHABILITADO)
-- Nota: Los datagramas históricos pueden contener líneas que ya no están activas
-- en lines-241.csv, por lo que la constraint es demasiado restrictiva
ALTER TABLE arc_stats 
DROP CONSTRAINT IF EXISTS fk_arc_stats_line;

-- ALTER TABLE arc_stats 
-- ADD CONSTRAINT fk_arc_stats_line 
-- FOREIGN KEY (line_id) REFERENCES lines(line_id) 
-- ON DELETE CASCADE;

-- ============================================================================
-- Tabla: analysis_runs (Registro de análisis ejecutados)
-- Para tracking de experimentos
-- ============================================================================
CREATE TABLE IF NOT EXISTS analysis_runs (
    run_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    total_lines_processed BIGINT,
    num_workers INT,
    duration_ms BIGINT,
    throughput_events_per_sec DOUBLE PRECISION,
    arcs_analyzed INT,
    total_measurements INT,
    avg_speed_global DOUBLE PRECISION,
    experiment_size VARCHAR(20), -- '1M', '10M', '100M'
    status VARCHAR(20) DEFAULT 'RUNNING', -- 'RUNNING', 'COMPLETED', 'FAILED'
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_analysis_runs_start ON analysis_runs(start_time);
CREATE INDEX IF NOT EXISTS idx_analysis_runs_status ON analysis_runs(status);
CREATE INDEX IF NOT EXISTS idx_analysis_runs_size ON analysis_runs(experiment_size);

-- ============================================================================
-- Tabla: arcs_metadata (Metadatos de arcos - opcional, para JOINs)
-- ============================================================================
CREATE TABLE IF NOT EXISTS arcs_metadata (
    line_id INT NOT NULL,
    orientation INT NOT NULL,
    sequence_num INT NOT NULL,
    from_stop_id INT NOT NULL,
    to_stop_id INT NOT NULL,
    distance DOUBLE PRECISION NOT NULL,
    line_name VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (line_id, orientation, sequence_num),
    FOREIGN KEY (line_id) REFERENCES lines(line_id),
    FOREIGN KEY (from_stop_id) REFERENCES stops(stop_id),
    FOREIGN KEY (to_stop_id) REFERENCES stops(stop_id)
);

CREATE INDEX IF NOT EXISTS idx_arcs_from_stop ON arcs_metadata(from_stop_id);
CREATE INDEX IF NOT EXISTS idx_arcs_to_stop ON arcs_metadata(to_stop_id);

-- ============================================================================
-- Vista: arc_details (Para consultas con información completa)
-- ============================================================================
CREATE OR REPLACE VIEW arc_details AS
SELECT 
    a.line_id,
    a.orientation,
    a.sequence_num,
    a.sum_distance,
    a.sum_time,
    a.count,
    a.avg_speed,
    a.last_updated,
    l.short_name as line_name,
    l.description as line_description,
    am.from_stop_id,
    am.to_stop_id,
    am.distance as arc_distance,
    s1.short_name as from_stop_name,
    s1.long_name as from_stop_long_name,
    s1.decimal_lat as from_lat,
    s1.decimal_lon as from_lon,
    s2.short_name as to_stop_name,
    s2.long_name as to_stop_long_name,
    s2.decimal_lat as to_lat,
    s2.decimal_lon as to_lon
FROM arc_stats a
JOIN lines l ON a.line_id = l.line_id
LEFT JOIN arcs_metadata am ON 
    a.line_id = am.line_id AND 
    a.orientation = am.orientation AND 
    a.sequence_num = am.sequence_num
LEFT JOIN stops s1 ON am.from_stop_id = s1.stop_id
LEFT JOIN stops s2 ON am.to_stop_id = s2.stop_id;

-- ============================================================================
-- Funciones útiles
-- ============================================================================

-- Función para limpiar estadísticas antiguas
CREATE OR REPLACE FUNCTION clean_old_stats(days_old INT DEFAULT 30)
RETURNS INT AS $$
DECLARE
    deleted_count INT;
BEGIN
    DELETE FROM arc_stats 
    WHERE last_updated < NOW() - (days_old || ' days')::INTERVAL;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Función para obtener estadísticas globales
CREATE OR REPLACE FUNCTION get_global_stats()
RETURNS TABLE (
    total_arcs BIGINT,
    arcs_with_data BIGINT,
    total_measurements BIGINT,
    avg_speed DOUBLE PRECISION,
    min_speed DOUBLE PRECISION,
    max_speed DOUBLE PRECISION,
    last_analysis TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        COUNT(*)::BIGINT as total_arcs,
        COUNT(*) FILTER (WHERE count > 0)::BIGINT as arcs_with_data,
        SUM(count)::BIGINT as total_measurements,
        AVG(avg_speed) as avg_speed,
        MIN(avg_speed) FILTER (WHERE avg_speed > 0) as min_speed,
        MAX(avg_speed) as max_speed,
        MAX(last_updated) as last_analysis
    FROM arc_stats;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- Comentarios en tablas (documentación)
-- ============================================================================

COMMENT ON TABLE arc_stats IS 'Estadísticas de velocidades promedio por arco, calculadas a partir del análisis de datagramas históricos';
COMMENT ON COLUMN arc_stats.sum_distance IS 'Suma acumulada de distancias recorridas en este arco (km)';
COMMENT ON COLUMN arc_stats.sum_time IS 'Suma acumulada de tiempos de recorrido en este arco (milisegundos)';
COMMENT ON COLUMN arc_stats.count IS 'Número de mediciones válidas (velocidad <= 120 km/h)';
COMMENT ON COLUMN arc_stats.avg_speed IS 'Velocidad promedio calculada automáticamente (km/h)';
COMMENT ON COLUMN arc_stats.analysis_run_id IS 'UUID del análisis que generó o actualizó estos datos';

COMMENT ON TABLE analysis_runs IS 'Registro de todos los análisis distribuidos ejecutados';
COMMENT ON COLUMN analysis_runs.throughput_events_per_sec IS 'Velocidad de procesamiento en eventos por segundo';
COMMENT ON COLUMN analysis_runs.experiment_size IS 'Tamaño del experimento: 1M, 10M, 100M líneas';

-- ============================================================================
-- Datos de ejemplo (para testing - comentar en producción)
-- ============================================================================

-- Insertar una línea de ejemplo
-- INSERT INTO lines (line_id, plan_version_id, short_name, description, activation_date)
-- VALUES (131, 241, 'T31', 'Ruta Troncal 31', '2018-01-01')
-- ON CONFLICT (line_id) DO NOTHING;

-- ============================================================================
-- Grants y permisos (ajustar según necesidad)
-- ============================================================================

-- Si usas un usuario diferente de postgres:
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO tu_usuario;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO tu_usuario;
-- GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO tu_usuario;

-- ============================================================================
-- Fin del schema
-- ============================================================================

-- Verificar la creación de las tablas
SELECT 
    schemaname,
    tablename,
    tableowner
FROM pg_tables 
WHERE schemaname = 'public' 
AND tablename IN ('lines', 'stops', 'arc_stats', 'analysis_runs', 'arcs_metadata')
ORDER BY tablename;

-- Verificar índices
SELECT 
    schemaname,
    tablename,
    indexname
FROM pg_indexes 
WHERE schemaname = 'public' 
AND tablename IN ('arc_stats', 'analysis_runs')
ORDER BY tablename, indexname;
