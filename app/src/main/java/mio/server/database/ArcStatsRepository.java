package mio.server.database;

import mioice.ArcStat;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository para persistencia de estadísticas de arcos en PostgreSQL
 * Implementa operaciones CRUD y consultas especializadas
 */
public class ArcStatsRepository {
    
    private final DatabaseManager dbManager;
    
    public ArcStatsRepository() {
        this.dbManager = DatabaseManager.getInstance();
    }
    
    /**
     * Insertar o actualizar estadísticas de un arco (UPSERT)
     * Si el arco ya existe, suma los valores (agregación incremental)
     */
    public void upsertArcStat(ArcStat stat, UUID analysisRunId) throws SQLException {
        String sql = """
            INSERT INTO arc_stats (line_id, orientation, sequence_num, sum_distance, sum_time, count, analysis_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (line_id, orientation, sequence_num) 
            DO UPDATE SET 
                sum_distance = arc_stats.sum_distance + EXCLUDED.sum_distance,
                sum_time = arc_stats.sum_time + EXCLUDED.sum_time,
                count = arc_stats.count + EXCLUDED.count,
                last_updated = CURRENT_TIMESTAMP,
                analysis_run_id = EXCLUDED.analysis_run_id
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, stat.lineId);
            stmt.setInt(2, stat.orientation);
            stmt.setInt(3, stat.sequenceNum);
            stmt.setDouble(4, stat.sumDistance);
            stmt.setLong(5, stat.sumTime);
            stmt.setInt(6, stat.count);
            stmt.setObject(7, analysisRunId);
            
            stmt.executeUpdate();
        }
    }
    
    /**
     * Insertar o actualizar múltiples estadísticas en batch (más eficiente)
     */
    public void upsertArcStatsBatch(ArcStat[] stats, UUID analysisRunId) throws SQLException {
        String sql = """
            INSERT INTO arc_stats (line_id, orientation, sequence_num, sum_distance, sum_time, count, analysis_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (line_id, orientation, sequence_num) 
            DO UPDATE SET 
                sum_distance = arc_stats.sum_distance + EXCLUDED.sum_distance,
                sum_time = arc_stats.sum_time + EXCLUDED.sum_time,
                count = arc_stats.count + EXCLUDED.count,
                last_updated = CURRENT_TIMESTAMP,
                analysis_run_id = EXCLUDED.analysis_run_id
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            int batchCount = 0;
            for (ArcStat stat : stats) {
                stmt.setInt(1, stat.lineId);
                stmt.setInt(2, stat.orientation);
                stmt.setInt(3, stat.sequenceNum);
                stmt.setDouble(4, stat.sumDistance);
                stmt.setLong(5, stat.sumTime);
                stmt.setInt(6, stat.count);
                stmt.setObject(7, analysisRunId);
                stmt.addBatch();
                
                batchCount++;
                
                // Ejecutar en lotes de 500 para evitar problemas de memoria
                if (batchCount % 500 == 0) {
                    stmt.executeBatch();
                    stmt.clearBatch();
                }
            }
            
            // Ejecutar el resto
            if (batchCount % 500 != 0) {
                stmt.executeBatch();
            }
            
            System.out.println("[DB] Insertados/actualizados " + stats.length + " registros en arc_stats");
        }
    }
    
    /**
     * Obtener velocidad promedio de un arco específico
     */
    public Double getArcSpeed(int lineId, int orientation, int sequenceNum) throws SQLException {
        String sql = """
            SELECT avg_speed 
            FROM arc_stats 
            WHERE line_id = ? AND orientation = ? AND sequence_num = ?
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, lineId);
            stmt.setInt(2, orientation);
            stmt.setInt(3, sequenceNum);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("avg_speed");
            }
            return null;
        }
    }
    
    /**
     * Obtener todas las estadísticas de una línea
     */
    public List<ArcStat> getLineStats(int lineId, int orientation) throws SQLException {
        String sql = """
            SELECT line_id, orientation, sequence_num, sum_distance, sum_time, count, avg_speed
            FROM arc_stats 
            WHERE line_id = ? AND orientation = ? 
            ORDER BY sequence_num
            """;
        
        List<ArcStat> stats = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, lineId);
            stmt.setInt(2, orientation);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                stats.add(mapResultSetToArcStat(rs));
            }
        }
        
        return stats;
    }
    
    /**
     * Obtener todas las estadísticas de arcos
     */
    public List<ArcStat> getAllArcStats() throws SQLException {
        String sql = """
            SELECT line_id, orientation, sequence_num, sum_distance, sum_time, count, avg_speed
            FROM arc_stats 
            ORDER BY line_id, orientation, sequence_num
            """;
        
        List<ArcStat> stats = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                stats.add(mapResultSetToArcStat(rs));
            }
        }
        
        return stats;
    }
    
    /**
     * Verificar si existen estadísticas en la base de datos
     */
    public boolean hasData() throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM arc_stats";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt("count") > 0;
            }
            return false;
        }
    }
    
    /**
     * Obtener estadísticas globales
     */
    public GlobalStats getGlobalStats() throws SQLException {
        String sql = """
            SELECT 
                COUNT(*) as total_arcs,
                COUNT(*) FILTER (WHERE count > 0) as arcs_with_data,
                SUM(count) as total_measurements,
                AVG(avg_speed) as avg_speed,
                MIN(avg_speed) FILTER (WHERE avg_speed > 0) as min_speed,
                MAX(avg_speed) as max_speed
            FROM arc_stats
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return new GlobalStats(
                    rs.getInt("total_arcs"),
                    rs.getInt("arcs_with_data"),
                    rs.getLong("total_measurements"),
                    rs.getDouble("avg_speed"),
                    rs.getDouble("min_speed"),
                    rs.getDouble("max_speed")
                );
            }
            return null;
        }
    }
    
    /**
     * Limpiar todas las estadísticas (útil para re-análisis)
     */
    public int clearAllStats() throws SQLException {
        String sql = "DELETE FROM arc_stats";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            int deleted = stmt.executeUpdate();
            conn.commit();
            System.out.println("[DB] Eliminados " + deleted + " registros de arc_stats");
            return deleted;
        }
    }
    
    /**
     * Mapear ResultSet a ArcStat
     */
    private ArcStat mapResultSetToArcStat(ResultSet rs) throws SQLException {
        ArcStat stat = new ArcStat();
        stat.lineId = rs.getInt("line_id");
        stat.orientation = rs.getInt("orientation");
        stat.sequenceNum = rs.getInt("sequence_num");
        stat.sumDistance = rs.getDouble("sum_distance");
        stat.sumTime = rs.getLong("sum_time");
        stat.count = rs.getInt("count");
        return stat;
    }
    
    /**
     * Clase para estadísticas globales
     */
    public static class GlobalStats {
        public final int totalArcs;
        public final int arcsWithData;
        public final long totalMeasurements;
        public final double avgSpeed;
        public final double minSpeed;
        public final double maxSpeed;
        
        public GlobalStats(int totalArcs, int arcsWithData, long totalMeasurements,
                          double avgSpeed, double minSpeed, double maxSpeed) {
            this.totalArcs = totalArcs;
            this.arcsWithData = arcsWithData;
            this.totalMeasurements = totalMeasurements;
            this.avgSpeed = avgSpeed;
            this.minSpeed = minSpeed;
            this.maxSpeed = maxSpeed;
        }
        
        @Override
        public String toString() {
            return String.format(
                "GlobalStats{arcos=%d, con datos=%d, mediciones=%d, velocidad promedio=%.2f km/h, min=%.2f, max=%.2f}",
                totalArcs, arcsWithData, totalMeasurements, avgSpeed, minSpeed, maxSpeed
            );
        }
    }
}
