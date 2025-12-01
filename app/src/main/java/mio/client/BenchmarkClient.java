package mio.client;

import mioice.*;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cliente de Benchmark para pruebas de carga
 * Simula múltiples usuarios concurrentes realizando consultas de rutas
 */
public class BenchmarkClient {

    private static final int DEFAULT_THREADS = 50;
    private static final int DEFAULT_DURATION_SECONDS = 60;

    public static void main(String[] args) {
        String masterIp = "localhost";
        int numThreads = DEFAULT_THREADS;
        int durationSeconds = DEFAULT_DURATION_SECONDS;

        if (args.length > 0) masterIp = args[0];
        if (args.length > 1) numThreads = Integer.parseInt(args[1]);
        if (args.length > 2) durationSeconds = Integer.parseInt(args[2]);

        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║  BENCHMARK SITM-MIO                                    ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println("Master IP: " + masterIp);
        System.out.println("Hilos (Usuarios concurrentes): " + numThreads);
        System.out.println("Duración: " + durationSeconds + " segundos");
        System.out.println("----------------------------------------------------------");

        Communicator communicator = null;
        try {
            // Inicializar comunicador
            // Usamos un timeout de conexión más alto para evitar fallos bajo carga
            String[] iceArgs = {
                "--Ice.Default.Locator=",
                "--Ice.ThreadPool.Client.Size=" + (numThreads + 2),
                "--Ice.ThreadPool.Client.SizeMax=" + (numThreads * 2),
                "--Ice.Override.ConnectTimeout=5000",
                "--Ice.Override.Timeout=10000" 
            };
            
            communicator = Util.initialize(iceArgs);

            // Conectar al GraphService del Master
            String proxyStr = String.format("GraphService:default -h %s -p 10000", masterIp);
            ObjectPrx base = communicator.stringToProxy(proxyStr);
            GraphServicePrx graphService = GraphServicePrx.checkedCast(base);

            if (graphService == null) {
                throw new RuntimeException("No se pudo conectar al GraphService en " + masterIp);
            }
            System.out.println("✓ Conectado al Master");

            // Obtener paradas para generar consultas válidas
            System.out.println("Obteniendo lista de paradas...");
            Stop[] stops = graphService.getAllStops();
            if (stops.length < 2) {
                throw new RuntimeException("Insuficientes paradas en el sistema para realizar pruebas");
            }
            System.out.println("✓ " + stops.length + " paradas disponibles para consultas");

            // Preparar métricas
            AtomicInteger totalQueries = new AtomicInteger(0);
            AtomicInteger successfulQueries = new AtomicInteger(0);
            AtomicInteger failedQueries = new AtomicInteger(0);
            AtomicLong totalLatencyMs = new AtomicLong(0);

            // Iniciar Pool de Hilos
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            long endTime = System.currentTimeMillis() + (durationSeconds * 1000);
            long startTime = System.currentTimeMillis();

            System.out.println("\n>>> INICIANDO CARGA (" + numThreads + " hilos) <<<");

            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    Random rand = new Random();
                    while (System.currentTimeMillis() < endTime) {
                        try {
                            // Seleccionar origen y destino aleatorios
                            Stop origin = stops[rand.nextInt(stops.length)];
                            Stop dest = stops[rand.nextInt(stops.length)];
                            
                            if (origin.stopId == dest.stopId) continue;

                            long reqStart = System.currentTimeMillis();
                            
                            // Ejecutar consulta
                            graphService.findRoute(origin.stopId, dest.stopId);
                            
                            long reqTime = System.currentTimeMillis() - reqStart;
                            
                            totalQueries.incrementAndGet();
                            successfulQueries.incrementAndGet();
                            totalLatencyMs.addAndGet(reqTime);

                        } catch (Exception e) {
                            totalQueries.incrementAndGet();
                            failedQueries.incrementAndGet();
                            // System.err.print("x"); // Opcional: imprimir error puntual
                        }
                    }
                });
            }

            // Esperar a que terminen los hilos
            executor.shutdown();
            executor.awaitTermination(durationSeconds + 5, TimeUnit.SECONDS);

            long actualEndTime = System.currentTimeMillis();
            long totalTimeMs = actualEndTime - startTime;
            double totalTimeSec = totalTimeMs / 1000.0;

            // Calcular resultados
            int total = totalQueries.get();
            int success = successfulQueries.get();
            int failed = failedQueries.get();
            double throughput = total / totalTimeSec;
            double avgLatency = (success > 0) ? (double) totalLatencyMs.get() / success : 0;

            System.out.println("\n╔════════════════════════════════════════════════════════╗");
            System.out.println("║  RESULTADOS DEL BENCHMARK                              ║");
            System.out.println("╚════════════════════════════════════════════════════════╝");
            System.out.println(String.format("Tiempo Total:           %.2f s", totalTimeSec));
            System.out.println(String.format("Total Consultas:        %,d", total));
            System.out.println(String.format("  - Exitosas:           %,d", success));
            System.out.println(String.format("  - Fallidas:           %,d", failed));
            System.out.println("----------------------------------------------------------");
            System.out.println(String.format("Rendimiento (TPS):      %.2f consultas/seg", throughput));
            System.out.println(String.format("Latencia Promedio:      %.2f ms", avgLatency));
            System.out.println(String.format("Concurrencia:           %d hilos", numThreads));
            System.out.println("==========================================================");

        } catch (Exception e) {
            System.err.println("Error fatal en benchmark: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (communicator != null) {
                communicator.destroy();
            }
        }
    }
}
