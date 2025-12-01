package mio.server.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Gestor de conexiones a PostgreSQL usando HikariCP
 * Implementa patrón Singleton para pool de conexiones
 */
public class DatabaseManager {
    
    private static DatabaseManager instance;
    private final HikariDataSource dataSource;
    
    private DatabaseManager() {
        Properties props = loadProperties();
        HikariConfig config = new HikariConfig();
        
        // Configuración de conexión
        config.setJdbcUrl(props.getProperty("db.url"));
        config.setUsername(props.getProperty("db.user"));
        config.setPassword(props.getProperty("db.password"));
        
        // Configuración de pool
        config.setMaximumPoolSize(Integer.parseInt(props.getProperty("db.pool.maximumPoolSize", "10")));
        config.setMinimumIdle(Integer.parseInt(props.getProperty("db.pool.minimumIdle", "5")));
        config.setConnectionTimeout(Long.parseLong(props.getProperty("db.pool.connectionTimeout", "30000")));
        config.setIdleTimeout(Long.parseLong(props.getProperty("db.pool.idleTimeout", "600000")));
        config.setMaxLifetime(Long.parseLong(props.getProperty("db.pool.maxLifetime", "1800000")));
        
        // Configuración de performance
        config.setAutoCommit(Boolean.parseBoolean(props.getProperty("db.autoCommit", "false")));
        config.addDataSourceProperty("cachePrepStmts", props.getProperty("db.cachePrepStmts", "true"));
        config.addDataSourceProperty("prepStmtCacheSize", props.getProperty("db.prepStmtCacheSize", "250"));
        config.addDataSourceProperty("prepStmtCacheSqlLimit", props.getProperty("db.prepStmtCacheSqlLimit", "2048"));
        
        // Pool name
        config.setPoolName("MIO-HikariPool");
        
        // Leak detection (útil para debugging)
        config.setLeakDetectionThreshold(60000); // 60 segundos
        
        this.dataSource = new HikariDataSource(config);
        
        System.out.println("[DB] Pool de conexiones inicializado correctamente");
        System.out.println("[DB] URL: " + maskPassword(props.getProperty("db.url")));
        System.out.println("[DB] Max Pool Size: " + config.getMaximumPoolSize());
    }
    
    /**
     * Obtener instancia singleton
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }
    
    /**
     * Obtener conexión del pool
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    /**
     * Cerrar pool de conexiones
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[DB] Pool de conexiones cerrado");
        }
    }
    
    /**
     * Verificar conectividad
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(5); // timeout de 5 segundos
        } catch (SQLException e) {
            System.err.println("[DB] Error verificando conexión: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Obtener estadísticas del pool
     */
    public void printPoolStats() {
        System.out.println("\n[DB] === Estadísticas del Pool ===");
        System.out.println("Conexiones activas: " + dataSource.getHikariPoolMXBean().getActiveConnections());
        System.out.println("Conexiones idle: " + dataSource.getHikariPoolMXBean().getIdleConnections());
        System.out.println("Conexiones totales: " + dataSource.getHikariPoolMXBean().getTotalConnections());
        System.out.println("Threads esperando: " + dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
        System.out.println("===================================\n");
    }
    
    /**
     * Cargar propiedades de configuración
     */
    private Properties loadProperties() {
        Properties props = new Properties();
        
        // Intentar cargar desde archivo
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("../../../config/database.properties")) {
            if (input != null) {
                props.load(input);
                return props;
            }
        } catch (IOException e) {
            // Ignorar, intentar ruta alternativa
        }
        
        // Ruta alternativa
        try (InputStream input = DatabaseManager.class
                .getResourceAsStream("/database.properties")) {
            if (input != null) {
                props.load(input);
                return props;
            }
        } catch (IOException e) {
            // Ignorar, intentar archivo externo
        }
        
        // Intentar cargar desde archivo externo
        try {
            props.load(new java.io.FileInputStream("config/database.properties"));
            return props;
        } catch (IOException e) {
            System.err.println("[DB] No se pudo cargar database.properties, usando valores por defecto");
        }
        
        // Valores por defecto (Railway)
        props.setProperty("db.url", "jdbc:postgresql://junction.proxy.rlwy.net:35186/railway");
        props.setProperty("db.user", "postgres");
        props.setProperty("db.password", "aoDzBIiEQXYRBTBtuAHWFPzXmjPvvTRo");
        props.setProperty("db.pool.maximumPoolSize", "10");
        props.setProperty("db.pool.minimumIdle", "5");
        
        return props;
    }
    
    /**
     * Enmascarar password en logs
     */
    private String maskPassword(String url) {
        if (url == null) return "null";
        // jdbc:postgresql://user:pass@host:port/db -> jdbc:postgresql://user:****@host:port/db
        return url.replaceAll("://([^:]+):([^@]+)@", "://$1:****@");
    }
}
