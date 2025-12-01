package mio.server.database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository para tracking de experimentos de análisis
 * Permite registrar y consultar diferentes ejecuciones del sistema
 */
public class AnalysisRunRepository {
    
    private final DatabaseManager dbManager;
    
    public AnalysisRunRepository() {
        this.dbManager = DatabaseManager.getInstance();
    }
    
    /**
     * Crear un nuevo registro de análisis
     * @return UUID del análisis creado
     */
    public UUID createAnalysisRun(long totalLinesProcessed, int numWorkers, String description) throws SQLException {
        String sql = """
            INSERT INTO analysis_runs (
                start_time, 
                total_lines_processed, 
                num_workers, 
                experiment_size, 
                status
            )
            VALUES (CURRENT_TIMESTAMP, ?, ?, ?, 'RUNNING')
            RETURNING run_id
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, totalLinesProcessed);
            stmt.setInt(2, numWorkers);
            
            // Determinar tamaño del experimento
            String experimentSize;
            if (totalLinesProcessed >= 100_000_000) {
                experimentSize = "100M";
            } else if (totalLinesProcessed >= 10_000_000) {
                experimentSize = "10M";
            } else if (totalLinesProcessed >= 1_000_000) {
                experimentSize = "1M";
            } else {
                experimentSize = "CUSTOM";
            }
            stmt.setString(3, experimentSize);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                UUID runId = (UUID) rs.getObject("run_id");
                System.out.println("[DB] Creado analysis_run: " + runId + " (" + experimentSize + ")");
                return runId;
            }
            throw new SQLException("Failed to create analysis run");
        }
    }
    
    /**
     * Actualizar análisis con resultados finales
     */
    public void updateAnalysisRun(UUID runId, long durationMs, int arcsAnalyzed, 
                                  String status) throws SQLException {
        String sql = """
            UPDATE analysis_runs 
            SET duration_ms = ?,
                arcs_analyzed = ?,
                status = ?,
                end_time = CURRENT_TIMESTAMP
            WHERE run_id = ?
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, durationMs);
            stmt.setInt(2, arcsAnalyzed);
            stmt.setString(3, status.toUpperCase());
            stmt.setObject(4, runId);
            
            int updated = stmt.executeUpdate();
            
            if (updated > 0) {
                System.out.println("[DB] Actualizado analysis_run: " + runId + " -> " + status);
            }
        }
    }
    
    /**
     * Obtener los últimos experimentos
     */
    public List<AnalysisRunInfo> getRecentRuns(int limit) throws SQLException {
        String sql = """
            SELECT run_id, total_lines_processed, num_workers, experiment_size, status,
                   start_time, end_time, duration_ms, arcs_analyzed
            FROM analysis_runs
            ORDER BY start_time DESC
            LIMIT ?
            """;
        
        List<AnalysisRunInfo> runs = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, limit);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                runs.add(mapResultSetToAnalysisRunInfo(rs));
            }
        }
        
        return runs;
    }
    
    /**
     * Obtener información de un análisis específico
     */
    public AnalysisRunInfo getAnalysisRun(UUID runId) throws SQLException {
        String sql = """
            SELECT run_id, total_lines_processed, num_workers, experiment_size, status,
                   start_time, end_time, duration_ms, arcs_analyzed
            FROM analysis_runs
            WHERE run_id = ?
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, runId);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToAnalysisRunInfo(rs);
            }
            return null;
        }
    }
    
    /**
     * Obtener experimentos por tamaño de dataset
     */
    public List<AnalysisRunInfo> getRunsByDatagramCount(long totalLinesProcessed) throws SQLException {
        String sql = """
            SELECT run_id, total_lines_processed, num_workers, experiment_size, status,
                   start_time, end_time, duration_ms, arcs_analyzed
            FROM analysis_runs
            WHERE total_lines_processed = ?
            ORDER BY start_time DESC
            """;
        
        List<AnalysisRunInfo> runs = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, totalLinesProcessed);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                runs.add(mapResultSetToAnalysisRunInfo(rs));
            }
        }
        
        return runs;
    }
    
    /**
     * Obtener estadísticas de experimentos por número de workers
     */
    public List<WorkerPerformance> getPerformanceByWorkers() throws SQLException {
        String sql = """
            SELECT 
                num_workers,
                COUNT(*) as run_count,
                AVG(duration_ms) as avg_time_ms,
                MIN(duration_ms) as min_time_ms,
                MAX(duration_ms) as max_time_ms,
                AVG(total_lines_processed::decimal / (duration_ms / 1000.0)) as avg_throughput
            FROM analysis_runs
            WHERE status = 'COMPLETED' AND duration_ms > 0
            GROUP BY num_workers
            ORDER BY num_workers
            """;
        
        List<WorkerPerformance> performances = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                performances.add(new WorkerPerformance(
                    rs.getInt("num_workers"),
                    rs.getInt("run_count"),
                    rs.getLong("avg_time_ms"),
                    rs.getLong("min_time_ms"),
                    rs.getLong("max_time_ms"),
                    rs.getDouble("avg_throughput")
                ));
            }
        }
        
        return performances;
    }
    
    /**
     * Mapear ResultSet a AnalysisRunInfo
     */
    private AnalysisRunInfo mapResultSetToAnalysisRunInfo(ResultSet rs) throws SQLException {
        return new AnalysisRunInfo(
            (UUID) rs.getObject("run_id"),
            rs.getLong("total_lines_processed"),
            rs.getInt("num_workers"),
            rs.getString("experiment_size"),
            rs.getString("status"),
            rs.getTimestamp("start_time"),
            rs.getTimestamp("end_time"),
            rs.getLong("duration_ms"),
            rs.getInt("arcs_analyzed")
        );
    }
    
    /**
     * Clase para información de experimento
     */
    public static class AnalysisRunInfo {
        public final UUID runId;
        public final long totalLinesProcessed;
        public final int numWorkers;
        public final String experimentSize;
        public final String status;
        public final Timestamp startTime;
        public final Timestamp endTime;
        public final long durationMs;
        public final int arcsAnalyzed;
        
        public AnalysisRunInfo(UUID runId, long totalLinesProcessed, int numWorkers, String experimentSize,
                              String status, Timestamp startTime, Timestamp endTime,
                              long durationMs, int arcsAnalyzed) {
            this.runId = runId;
            this.totalLinesProcessed = totalLinesProcessed;
            this.numWorkers = numWorkers;
            this.experimentSize = experimentSize;
            this.status = status;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMs = durationMs;
            this.arcsAnalyzed = arcsAnalyzed;
        }
        
        public double getThroughput() {
            if (durationMs > 0) {
                return (totalLinesProcessed * 1000.0) / durationMs;
            }
            return 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Run[%s] %s: %d datagramas, %d workers, %d arcos, %.2f seg, %.2f datagramas/seg",
                runId.toString().substring(0, 8),
                status,
                totalLinesProcessed,
                numWorkers,
                arcsAnalyzed,
                durationMs / 1000.0,
                getThroughput()
            );
        }
    }
    
    /**
     * Clase para estadísticas de performance por workers
     */
    public static class WorkerPerformance {
        public final int numWorkers;
        public final int runCount;
        public final long avgTimeMs;
        public final long minTimeMs;
        public final long maxTimeMs;
        public final double avgThroughput;
        
        public WorkerPerformance(int numWorkers, int runCount, long avgTimeMs,
                                long minTimeMs, long maxTimeMs, double avgThroughput) {
            this.numWorkers = numWorkers;
            this.runCount = runCount;
            this.avgTimeMs = avgTimeMs;
            this.minTimeMs = minTimeMs;
            this.maxTimeMs = maxTimeMs;
            this.avgThroughput = avgThroughput;
        }
        
        @Override
        public String toString() {
            return String.format(
                "%d workers: %d runs, avg=%.2fs (min=%.2fs, max=%.2fs), throughput=%.2f/s",
                numWorkers, runCount,
                avgTimeMs / 1000.0,
                minTimeMs / 1000.0,
                maxTimeMs / 1000.0,
                avgThroughput
            );
        }
    }
}
