package mio.server;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import mioice.*;
import mio.server.data.GraphBuilder;
import mio.server.services.RouteServiceI;
import mio.server.services.GraphServiceI;
import mio.server.database.DatabaseManager;
import mio.server.database.ArcStatsRepository;
import mio.server.database.AnalysisRunRepository;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

/**
 * Servidor ICE del sistema MIO
 * Carga los datos CSV, construye el grafo y expone los servicios remotos
 */
public class MioServer {

    // ===== CONFIGURACIÓN DEL EXPERIMENTO (Requerimiento D) =====
    // Cambiar este valor para probar diferentes escalas:
    // - 1_000_000 (1M)
    // - 10_000_000 (10M)
    // - 100_000_000 (100M)
    // - -1 para procesar el archivo completo
    private static final int EXPERIMENT_SIZE = 100_000_000; // 100M líneas para precarga
    // ===========================================================

    public static void main(String[] args) {
    int status = 0;
    Communicator communicator = null;
    UUID analysisRunId = null;
    ArcStatsRepository arcStatsRepo = null;
    AnalysisRunRepository analysisRunRepo = null;

    try {
            // Imprimir banner
            printBanner();

            // Inicializar conexión a base de datos
            System.out.println("╔════════════════════════════════════════════════════════╗");
            System.out.println("║  INICIALIZANDO CONEXIÓN A POSTGRESQL (RAILWAY)        ║");
            System.out.println("╚════════════════════════════════════════════════════════╝");
            try {
                DatabaseManager dbManager = DatabaseManager.getInstance();
                System.out.println("[DB] ✓ DatabaseManager inicializado");
                
                dbManager.testConnection();
                System.out.println("[DB] ✓ Conexión a PostgreSQL exitosa");
                
                arcStatsRepo = new ArcStatsRepository();
                analysisRunRepo = new AnalysisRunRepository();
                System.out.println("[DB] ✓ Repositorios inicializados");
                
                // Verificar si hay datos previos
                if (arcStatsRepo.hasData()) {
                    ArcStatsRepository.GlobalStats globalStats = arcStatsRepo.getGlobalStats();
                    System.out.println("[DB] ℹ Datos existentes: " + globalStats);
                } else {
                    System.out.println("[DB] ℹ Base de datos vacía - registros nuevos se crearán");
                }
                System.out.println("[DB] ✓ Sistema de persistencia ACTIVO\n");
            } catch (Exception e) {
                System.err.println("\n╔════════════════════════════════════════════════════════╗");
                System.err.println("║  ⚠️  ERROR CRÍTICO: NO SE PUDO CONECTAR A POSTGRESQL  ║");
                System.err.println("╚════════════════════════════════════════════════════════╝");
                System.err.println("Tipo de error: " + e.getClass().getSimpleName());
                System.err.println("Mensaje: " + e.getMessage());
                e.printStackTrace();
                System.err.println("\nPor favor verifique:");
                System.err.println("1. Archivo config/database.properties existe y es correcto");
                System.err.println("2. Credenciales de Railway son válidas");
                System.err.println("3. Firewall permite conexión a turntable.proxy.rlwy.net:28619");
                System.err.println("4. Driver JDBC PostgreSQL está en classpath");
                System.err.println("\n⛔ El servidor NO GUARDARÁ DATOS sin conexión a BD");
                System.err.println("⛔ Presione Ctrl+C para detener o espere para continuar sin BD\n");
                
                // Dar 10 segundos para que el usuario lea el error y cancele si quiere
                Thread.sleep(10000);
                
                arcStatsRepo = null;
                analysisRunRepo = null;
            }

            // Inicializar comunicador ICE
            communicator = Util.initialize(args, "config/config.server");

            // Inicializar Repositorios (Patrón Repository)
            System.out.println("Inicializando repositorios...");
            mio.server.repository.StopRepository stopRepo = mio.server.repository.RepositoryFactory
                    .createStopRepository("CSV", "data/stops-241.csv");
            mio.server.repository.LineRepository lineRepo = mio.server.repository.RepositoryFactory
                    .createLineRepository("CSV", "data/lines-241.csv");
            mio.server.repository.LineStopRepository lineStopRepo = mio.server.repository.RepositoryFactory
                    .createLineStopRepository("CSV", "data/linestops-241.csv");

            // Cargar datos y construir grafo
            System.out.println("Inicializando servidor...\n");
            GraphBuilder graphBuilder = new GraphBuilder(stopRepo, lineRepo, lineStopRepo);

            try {
                // Cargar datos usando los repositorios inyectados
                graphBuilder.loadData();
            } catch (Exception e) {
                System.err.println("Error cargando datos: " + e.getMessage());
                status = 1;
                return;
            }

            // Imprimir el grafo en consola (cumple con el requerimiento A)
            graphBuilder.printGraphToConsole();

            // Crear adaptador de objetos
            ObjectAdapter adapter = communicator.createObjectAdapter("MioAdapter");

            // Crear e instalar servants
            RouteService routeService = new RouteServiceI(graphBuilder);
            GraphServiceI graphService = new GraphServiceI(graphBuilder);

            adapter.add(routeService, Util.stringToIdentity("RouteService"));
            adapter.add(graphService, Util.stringToIdentity("GraphService"));

            // Activar adaptador
            adapter.activate();

            System.out.println("SERVIDOR ICE ACTIVO");
            System.out.println("Servicios disponibles:");
            System.out.println("RouteService - Consultas de rutas y paradas              ║");
            System.out.println("GraphService - Consultas del grafo completo              ║");

            // --- LÓGICA DE ANÁLISIS DISTRIBUIDO (Requerimiento D) ---
            System.out.println("\n--- ESPERANDO WORKERS PARA ANÁLISIS ---");
            System.out.println("El servidor esperará hasta 30 segundos para que los workers se registren...");
            
            // Esperar hasta 30 segundos verificando cada 5 segundos
            List<AnalysisWorkerPrx> workers = new java.util.ArrayList<>();
            int maxAttempts = 6; // 6 intentos x 5 segundos = 30 segundos
            int attempt = 0;
            
            while (workers.isEmpty() && attempt < maxAttempts) {
                attempt++;
                Thread.sleep(5000); // Esperar 5 segundos
                
                System.out.println("Verificando workers registrados (intento " + attempt + "/" + maxAttempts + ")...");
                
                List<String> workerProxies = graphService.getWorkerProxies();
                
                for (String proxyStr : workerProxies) {
                    try {
                        // Convertir RouteWorker proxy a AnalysisWorker proxy
                        String analysisProxyStr = proxyStr.replace("RouteWorker", "AnalysisWorker");
                        com.zeroc.Ice.ObjectPrx base = communicator.stringToProxy(analysisProxyStr);
                        AnalysisWorkerPrx worker = AnalysisWorkerPrx.checkedCast(base);
                        if (worker != null) {
                            worker.ice_ping();
                            workers.add(worker);
                            System.out.println("✓ Worker detectado: " + analysisProxyStr);
                        }
                    } catch (Exception e) {
                        // Worker no responde, continuar
                    }
                }
                
                if (!workers.isEmpty()) {
                    System.out.println("✓ " + workers.size() + " worker(s) detectado(s), iniciando análisis...");
                    break;
                }
            }
            
            System.out.println("\n--- INICIANDO ANÁLISIS DE DATAGRAMAS ---");

            if (workers.isEmpty()) {
                System.out.println("⚠️ No se detectaron workers después de 30 segundos.");
                System.out.println("ℹ️ El servidor continuará en modo consulta (sin análisis de datagramas).");
                System.out.println("ℹ️ Puede ejecutar workers en cualquier momento para análisis posteriores.\n");
                
                // Imprimir estado del grafo
                printGraphSummary(graphBuilder);
                
                System.out.println("\n╔════════════════════════════════════════════════════════╗");
                System.out.println("║  SERVIDOR LISTO PARA CONSULTAS                         ║");
                System.out.println("╚════════════════════════════════════════════════════════╝");
                System.out.println("✓ GraphService: Consultas de grafo completo");
                System.out.println("✓ RouteService: Cálculo de rutas óptimas");
                System.out.println("ℹ️ No cierre esta ventana mientras use la interfaz gráfica\n");
                
                communicator.waitForShutdown();
            } else {
                System.out.println("Workers activos: " + workers.size());

                // 2. Definir archivo y tamaño
                String datagramFile = "data/datagrams4history.csv";
                System.out.println("Contando líneas del archivo...");
                int totalLines = countLines(datagramFile);

                // Aplicar límite del experimento
                if (EXPERIMENT_SIZE > 0 && totalLines > EXPERIMENT_SIZE) {
                    totalLines = EXPERIMENT_SIZE;
                    System.out.println(">> MODO EXPERIMENTO: Procesando solo " + String.format("%,d", EXPERIMENT_SIZE)
                            + " líneas <<");
                }
                System.out.println("Total líneas a procesar: " + String.format("%,d", totalLines));

                // ========== VERIFICAR SI YA HAY DATOS EN POSTGRESQL ==========
                boolean needsAnalysis = true;
                if (arcStatsRepo != null) {
                    try {
                        System.out.println("\n╔════════════════════════════════════════════════════════╗");
                        System.out.println("║  VERIFICANDO DATOS EXISTENTES EN POSTGRESQL           ║");
                        System.out.println("╚════════════════════════════════════════════════════════╝");
                        
                        if (arcStatsRepo.hasData()) {
                            ArcStatsRepository.GlobalStats globalStats = arcStatsRepo.getGlobalStats();
                            System.out.println("[DB] ℹ Datos encontrados en PostgreSQL:");
                            System.out.println("[DB]   - " + globalStats.totalArcs + " arcos con velocidades calculadas");
                            System.out.println("[DB]   - " + String.format("%,d", globalStats.totalMeasurements) + " mediciones totales");
                            System.out.println("[DB]   - Velocidad promedio global: " + String.format("%.2f", globalStats.avgSpeed) + " km/h");
                            
                            // Verificar si es suficiente (al menos 2000 arcos con datos)
                            if (globalStats.totalArcs >= 2000) {
                                needsAnalysis = false;
                                System.out.println("[DB] ✓ Datos suficientes encontrados - SALTANDO ANÁLISIS");
                                System.out.println("[DB] ℹ Los workers usarán estos datos precalculados");
                            } else {
                                System.out.println("[DB] ⚠️ Datos insuficientes (" + globalStats.totalArcs + " < 2000 arcos)");
                                System.out.println("[DB] ℹ Se ejecutará análisis completo");
                            }
                        } else {
                            System.out.println("[DB] ℹ No hay datos previos - se ejecutará análisis completo");
                        }
                        System.out.println();
                    } catch (Exception e) {
                        System.err.println("[DB] ⚠️ Error verificando datos: " + e.getMessage());
                        needsAnalysis = true; // Por seguridad, ejecutar análisis si falla la verificación
                    }
                } else {
                    System.out.println("[INFO] Base de datos no disponible - se ejecutará análisis en memoria\n");
                }
                // =============================================================

                if (!needsAnalysis) {
                    // Datos ya existen - saltarse el análisis
                    System.out.println("╔════════════════════════════════════════════════════════╗");
                    System.out.println("║  USANDO DATOS PRECALCULADOS DE POSTGRESQL             ║");
                    System.out.println("╚════════════════════════════════════════════════════════╝");
                    System.out.println("✓ Los datos de velocidades ya están cargados en el grafo");
                    System.out.println("✓ No se requiere procesamiento de datagramas");
                    System.out.println("✓ Sistema listo para consultas\n");
                    
                } else {
                    // Ejecutar análisis completo
                    System.out.println("╔════════════════════════════════════════════════════════╗");
                    System.out.println("║  EJECUTANDO ANÁLISIS DISTRIBUIDO DE DATAGRAMAS        ║");
                    System.out.println("╚════════════════════════════════════════════════════════╝\n");

                // Crear registro de experimento en BD
                if (analysisRunRepo != null) {
                    try {
                        String description = String.format("Experimento %s líneas con %d workers",
                                String.format("%,d", totalLines), workers.size());
                        analysisRunId = analysisRunRepo.createAnalysisRun(totalLines, workers.size(), description);
                        System.out.println("[DB] ✓ Experimento registrado con ID: " + analysisRunId);
                    } catch (Exception e) {
                        System.err.println("[DB] ❌ Error creando registro de análisis: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    System.err.println("[DB] ⚠️ No se creará registro de experimento (BD no disponible)");
                }

                // 3. Distribuir trabajo
                int chunkSize = totalLines / workers.size();
                long startTime = System.currentTimeMillis();

                List<java.util.concurrent.Future<ArcStat[]>> futures = new java.util.ArrayList<>();
                java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors
                        .newFixedThreadPool(workers.size());

                for (int i = 0; i < workers.size(); i++) {
                    final int workerIdx = i;
                    final AnalysisWorkerPrx worker = workers.get(i);
                    final int start = 1 + (i * chunkSize);
                    final int end = (i == workers.size() - 1) ? (totalLines + 1) : (start + chunkSize);

                    futures.add(executor.submit(() -> {
                        System.out.println("Enviando tarea a Worker " + workerIdx + ": líneas " + start + " a " + end);
                        // Enviar solo el nombre del archivo, cada worker lo buscará en su carpeta local data/
                        return worker.analyzeDatagrams(datagramFile, start, end);
                    }));
                }

                // 4. Recolectar resultados
                int totalArcsProcessed = 0;
                int workerNum = 0;
                for (java.util.concurrent.Future<ArcStat[]> future : futures) {
                    try {
                        workerNum++;
                        ArcStat[] stats = future.get();
                        updateGraphStats(graphBuilder, stats);
                        totalArcsProcessed += stats.length;
                        
                        // Persistir estadísticas en PostgreSQL
                        if (arcStatsRepo != null && analysisRunId != null) {
                            try {
                                System.out.println("[DB] ⏳ Guardando " + stats.length + " estadísticas del Worker " + workerNum + "...");
                                arcStatsRepo.upsertArcStatsBatch(stats, analysisRunId);
                                System.out.println("[DB] ✓ Datos del Worker " + workerNum + " guardados exitosamente");
                            } catch (Exception e) {
                                System.err.println("[DB] ❌ Error persistiendo estadísticas del Worker " + workerNum + ": " + e.getMessage());
                                e.printStackTrace();
                            }
                        } else {
                            System.err.println("[DB] ⚠️ Persistencia deshabilitada - datos del Worker " + workerNum + " NO se guardan");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                long endTime = System.currentTimeMillis();
                long processingTimeMs = endTime - startTime;
                
                System.out.println("\n=== ANÁLISIS COMPLETADO ===");
                System.out.println("Número de workers (nodos): " + workers.size());
                System.out.println("Líneas procesadas: " + String.format("%,d", totalLines));
                System.out.println("Tiempo total: " + processingTimeMs + " ms");
                System.out.println("Velocidad de procesamiento: "
                        + String.format("%.2f", (totalLines / (processingTimeMs / 1000.0))) + " eventos/seg");
                System.out.println("================================");
                
                // Actualizar registro de experimento en BD
                if (analysisRunRepo != null && analysisRunId != null) {
                    try {
                        analysisRunRepo.updateAnalysisRun(analysisRunId, processingTimeMs, totalArcsProcessed, "completed");
                        System.out.println("[DB] ✓ Experimento completado en BD: " + analysisRunId);
                        System.out.println("[DB] ✓ Todos los datos se guardaron exitosamente en PostgreSQL");
                    } catch (Exception e) {
                        System.err.println("[DB] ❌ Error actualizando registro de análisis: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    System.err.println("[DB] ⚠️ Los datos NO se guardaron en PostgreSQL (BD no disponible)");
                }

                executor.shutdown();
                
                } // Fin del else (needsAnalysis)
                
                // Imprimir resumen del grafo actualizado
                printGraphSummary(graphBuilder);
                
                System.out.println("\n╔════════════════════════════════════════════════════════╗");
                System.out.println("║  SERVIDOR LISTO PARA CONSULTAS                         ║");
                System.out.println("╚════════════════════════════════════════════════════════╝");
                System.out.println("✓ GraphService: Consultas de grafo completo");
                System.out.println("✓ RouteService: Cálculo de rutas óptimas");
                System.out.println("ℹ No cierre esta ventana mientras use la interfaz gráfica\n");
                
                communicator.waitForShutdown();
            }

        } catch (Exception e) {
            System.err.println("Error en el servidor: " + e);
            e.printStackTrace();
            status = 1;
        } finally {
            if (communicator != null) {
                try {
                    communicator.destroy();
                } catch (Exception e) {
                    System.err.println("Error destruyendo communicator: " + e);
                    status = 1;
                }
            }
        }

    // System.exit(status); // El servidor permanece activo para consultas
    }

    private static int countLines(String filename) throws IOException {
        try (java.io.BufferedInputStream is = new java.io.BufferedInputStream(new java.io.FileInputStream(filename))) {
            byte[] c = new byte[1024];
            int count = 0;
            int readChars = 0;
            boolean empty = true;
            while ((readChars = is.read(c)) != -1) {
                empty = false;
                for (int i = 0; i < readChars; ++i) {
                    if (c[i] == '\n') {
                        count++;
                    }
                }
            }
            return (count == 0 && !empty) ? 1 : count;
        }
    }

    private static void updateGraphStats(GraphBuilder graphBuilder, ArcStat[] stats) {
        int totalArcsWithData = 0;
        double weightedSumSpeed = 0;
        int totalMeasurements = 0;

        // Crear un índice rápido para buscar arcos por (lineId, orientation, sequenceNum)
        Map<String, mioice.Arc> arcIndex = new HashMap<>();
        for (mioice.Arc arc : graphBuilder.getAllArcs()) {
            String key = arc.lineId + ":" + arc.orientation + ":" + arc.sequenceNum;
            arcIndex.put(key, arc);
        }

        System.out.println("\n--- Velocidades promedio por arco ---");
        for (ArcStat s : stats) {
            totalMeasurements += s.count;
            if (s.sumTime > 0 && s.count > 0) {
                double avgSpeedKmh = (s.sumDistance / (s.sumTime / 3600000.0));
                weightedSumSpeed += (avgSpeedKmh * s.count);
                totalArcsWithData++;
                System.out.println(String.format(
                    "Línea %d, Orientación %d, Secuencia %d: %.2f km/h (%d mediciones)",
                    s.lineId, s.orientation, s.sequenceNum, avgSpeedKmh, s.count));
                // Actualizar el campo avgSpeed en el grafo
                String key = s.lineId + ":" + s.orientation + ":" + s.sequenceNum;
                mioice.Arc arc = arcIndex.get(key);
                if (arc != null) {
                    arc.avgSpeed = avgSpeedKmh;
                }
            }
        }

        if (totalMeasurements > 0) {
            double avgSpeedGlobal = weightedSumSpeed / totalMeasurements;
            System.out.println("\n--- Resumen del análisis ---");
            System.out.println("Arcos analizados: " + totalArcsWithData);
            System.out.println("Total de mediciones: " + totalMeasurements);
            System.out.println("Velocidad promedio global: " + String.format("%.2f", avgSpeedGlobal) + " km/h");
        }
    }

    /**
     * Imprime resumen del estado del grafo
     */
    private static void printGraphSummary(GraphBuilder graphBuilder) {
        int totalArcs = graphBuilder.getAllArcs().size();
        int arcsWithSpeed = 0;
        
        for (mioice.Arc arc : graphBuilder.getAllArcs()) {
            if (arc.avgSpeed > 0) {
                arcsWithSpeed++;
            }
        }
        
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║  ESTADO DEL GRAFO                                      ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println("Total de arcos: " + totalArcs);
        System.out.println("Arcos con velocidad calculada: " + arcsWithSpeed);
        System.out.println("Cobertura: " + String.format("%.1f%%", (arcsWithSpeed * 100.0 / totalArcs)));
    }

    private static void printBanner() {
        System.out.println("\n");
        System.out.println("SISTEMA DE GRAFOS SITM-MIO");
        System.out.println("Universidad ICESI");
        System.out.println("Ingeniería de Software IV");
        System.out.println("Servidor ICE - Análisis de Rutas");
        System.out.println();
    }
}
