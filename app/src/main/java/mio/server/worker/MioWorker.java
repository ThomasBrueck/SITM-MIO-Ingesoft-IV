package mio.server.worker;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;
import mio.server.data.GraphBuilder;

import java.io.IOException;

/**
 * Servidor Worker del sistema MIO
 * Carga los datos y espera tareas del Master
 */
public class MioWorker {

    public static void main(String[] args) {
        int status = 0;
        Communicator communicator = null;

        try {
            System.out.println("\nINICIANDO WORKER...");

            // Leer argumentos: puerto y opcionalmente IP del worker y IP del master
            // Uso: ./gradlew runWorker --args="<puerto> <worker_ip> <master_ip>"
            int port = 10001;
            String workerHost = getLocalIPAddress(); // IP de este worker (autodetectada)
            String masterHost = "localhost"; // IP del master (por defecto localhost)
            
            if (args.length > 0) {
                try {
                    port = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    System.err.println("[Worker] Argumento de puerto inválido, usando 10001 por defecto.");
                }
            }
            
            if (args.length > 1) {
                workerHost = args[1]; // IP específica de este worker
            }
            
            if (args.length > 2) {
                masterHost = args[2]; // IP del master
            }

            System.out.println("[Worker] Configuración:");
            System.out.println("  - Puerto: " + port);
            System.out.println("  - IP Worker: " + workerHost);
            System.out.println("  - IP Master: " + masterHost);

            // Configuración ICE dinámica usando InitializationData
            com.zeroc.Ice.InitializationData initData = new com.zeroc.Ice.InitializationData();
            initData.properties = com.zeroc.Ice.Util.createProperties();
            // Escuchar en todas las interfaces de red para aceptar conexiones remotas
            initData.properties.setProperty("WorkerAdapter.Endpoints", "tcp -h " + workerHost + " -p " + port);
            initData.properties.setProperty("WorkerAdapter.AdapterId", "WorkerAdapter" + port);
            initData.properties.setProperty("Ice.ThreadPool.Server.Size", "5");
            initData.properties.setProperty("Ice.ThreadPool.Server.SizeMax", "10");
            initData.properties.setProperty("Ice.Warn.Connections", "1");

            communicator = Util.initialize(new String[]{}, initData);

            // Inicializar Repositorios
            System.out.println("Inicializando repositorios en Worker...");
            mio.server.repository.StopRepository stopRepo = mio.server.repository.RepositoryFactory
                    .createStopRepository("CSV", "data/stops-241.csv");
            mio.server.repository.LineRepository lineRepo = mio.server.repository.RepositoryFactory
                    .createLineRepository("CSV", "data/lines-241.csv");
            mio.server.repository.LineStopRepository lineStopRepo = mio.server.repository.RepositoryFactory
                    .createLineStopRepository("CSV", "data/linestops-241.csv");

            // Cargar datos (cada worker tiene su propia copia del grafo)
            System.out.println("Cargando grafo en memoria del Worker...");
            GraphBuilder graphBuilder = new GraphBuilder(stopRepo, lineRepo, lineStopRepo);
            try {
                graphBuilder.loadData();
            } catch (Exception e) {
                System.err.println("Error cargando datos en Worker: " + e.getMessage());
                return;
            }

            // Crear adaptador con endpoint dinámico
            ObjectAdapter adapter = communicator.createObjectAdapter("WorkerAdapter");

            // Crear servant RouteWorker
            RouteWorkerI worker = new RouteWorkerI(graphBuilder);
            adapter.add(worker, Util.stringToIdentity("RouteWorker"));

            // Registrar servant AnalysisWorker
            AnalysisWorkerI analysisWorker = new AnalysisWorkerI(graphBuilder);
            adapter.add(analysisWorker, Util.stringToIdentity("AnalysisWorker"));

            // Activar
            adapter.activate();

            // Registrar este worker en el master para consultas de rutas
            try {
                // Construir el proxy de este worker usando su IP real y puerto
                String workerProxy = String.format("RouteWorker:default -h %s -p %d", workerHost, port);
                System.out.println("[Worker] Proxy a registrar: " + workerProxy);
                
                // Construir el proxy del master (GraphService) usando la IP del master
                String masterProxy = String.format("GraphService:default -h %s -p 10000", masterHost);
                com.zeroc.Ice.ObjectPrx base = communicator.stringToProxy(masterProxy);
                mioice.GraphServicePrx master = mioice.GraphServicePrx.checkedCast(base);
                if (master != null) {
                    master.registerWorker(workerProxy);
                    System.out.println("[Worker] ✓ Registrado exitosamente en el master para consultas de rutas.");
                } else {
                    System.err.println("[Worker] ✗ No se pudo obtener el proxy del master para registro de rutas.");
                }
            } catch (Exception e) {
                System.err.println("[Worker] ✗ Error registrando en el master para rutas: " + e.getMessage());
                System.err.println("[Worker] Verifique que el master esté activo en " + masterHost + ":10000");
            }

            System.out.println("\n[Worker] ACTIVO y esperando tareas en " + workerHost + ":" + port + "...\n");

            communicator.waitForShutdown();

        } catch (Exception e) {
            System.err.println("Error en Worker: " + e);
            // No cerramos el worker, solo reportamos el error y dejamos el proceso vivo
        }
        // No llamamos a System.exit ni destroy para mantener el worker activo
    }
    
    /**
     * Obtiene la dirección IP local de este equipo (no loopback)
     */
    private static String getLocalIPAddress() {
        try {
            java.net.InetAddress localhost = java.net.InetAddress.getLocalHost();
            java.net.InetAddress[] allMyIps = java.net.InetAddress.getAllByName(localhost.getCanonicalHostName());
            
            for (java.net.InetAddress addr : allMyIps) {
                if (!addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                    return addr.getHostAddress();
                }
            }
            
            // Fallback: intentar con NetworkInterface
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                
                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Worker] Error obteniendo IP local: " + e.getMessage());
        }
        return "0.0.0.0"; // Escuchar en todas las interfaces si no se puede determinar
    }
}
