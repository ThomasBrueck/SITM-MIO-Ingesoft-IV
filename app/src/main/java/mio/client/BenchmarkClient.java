package mio.client;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Util;
import mioice.GraphServicePrx;
import mioice.RouteResult;
import mioice.Stop;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BenchmarkClient {
    public static void main(String[] args) {
        int status = 0;
        Communicator communicator = null;

        try {
            // Args: <datagramFile> <outputFile>
            if (args.length < 2) {
                System.err.println("Usage: java BenchmarkClient <datagramFile> <outputFile>");
                System.exit(1);
            }

            String datagramFile = args[0];
            String outputFile = args[1];

            communicator = Util.initialize(args, "config/config.client");

            // Conectar al Master
            System.out.println("Conectando al Master...");
            com.zeroc.Ice.ObjectPrx base = communicator.stringToProxy("GraphService:tcp -h localhost -p 10000");
            GraphServicePrx master = GraphServicePrx.checkedCast(base);

            if (master == null) {
                throw new Error("Invalid proxy");
            }

            // Obtener todas las paradas para elegir destinos aleatorios
            System.out.println("Obteniendo lista de paradas...");
            Stop[] allStops = master.getAllStops();
            List<Integer> stopIds = new ArrayList<>();
            for (Stop s : allStops) {
                stopIds.add(s.stopId);
            }
            System.out.println("Total paradas disponibles: " + stopIds.size());

            if (stopIds.isEmpty()) {
                System.err.println("No hay paradas en el sistema.");
                System.exit(1);
            }

            // Leer archivo de datagramas y preparar requests
            System.out.println("Leyendo archivo de datos: " + datagramFile);
            List<Integer> origins = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(datagramFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    try {
                        String[] parts = line.split(",");
                        if (parts.length > 2) {
                            int originId = Integer.parseInt(parts[2].trim());
                            if (originId > 0) {
                                origins.add(originId);
                            }
                        }
                    } catch (Exception e) {
                        // Ignorar líneas mal formadas
                    }
                }
            }
            System.out.println("Total requests cargados: " + origins.size());

            // Ejecutar Benchmark con Concurrencia
            System.out.println("Iniciando Benchmark Concurrente...");
            int numThreads = 50; // Simular 50 usuarios concurrentes
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(numThreads);
            java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger errorCount = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger completedCount = new java.util.concurrent.atomic.AtomicInteger(0);
            
            Random rand = new Random();
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < origins.size(); i++) {
                final int origin = origins.get(i);
                final int dest = stopIds.get(rand.nextInt(stopIds.size()));
                
                executor.submit(() -> {
                    try {
                        master.findRoute(origin, dest);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        int current = completedCount.incrementAndGet();
                        if (current % 100 == 0) {
                            System.out.print(".");
                        }
                    }
                });
            }
            
            executor.shutdown();
            try {
                // Esperar a que terminen todas las tareas (timeout generoso)
                executor.awaitTermination(1, java.util.concurrent.TimeUnit.HOURS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            System.out.println();

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            double avgTime = (double) totalTime / origins.size(); // Tiempo promedio total (wall clock / requests)
            // Throughput = Requests / Seconds
            double throughput = (double) origins.size() / (totalTime / 1000.0);

            System.out.println("========================================");
            System.out.println("RESULTADOS DEL BENCHMARK (CONCURRENTE)");
            System.out.println("========================================");
            System.out.println("Hilos Concurrentes: " + numThreads);
            System.out.println("Total Requests: " + origins.size());
            System.out.println("Exitosos: " + successCount.get());
            System.out.println("Fallidos: " + errorCount.get());
            System.out.println("Tiempo Total: " + totalTime + " ms");
            System.out.println("Throughput: " + String.format("%.2f", throughput) + " req/sec");
            System.out.println("========================================");

            // Guardar resultados en CSV
            // Guardar resultados en CSV
            try (PrintWriter pw = new PrintWriter(new FileWriter(outputFile, true))) {
                // Format: Timestamp, TotalRequests, TotalTimeMs, Throughput, Threads
                pw.println(System.currentTimeMillis() + "," + origins.size() + "," + totalTime + "," + throughput + "," + numThreads);
            }

        } catch (Exception e) {
            System.err.println("Error en BenchmarkClient: " + e);
            e.printStackTrace();
            status = 1;
        } finally {
            if (communicator != null) {
                communicator.destroy();
            }
        }
        System.exit(status);
    }
}
