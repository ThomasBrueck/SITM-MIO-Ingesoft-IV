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
            
            // Parsear argumentos para encontrar el puerto
            String port = "10001"; // Puerto por defecto
            for (int i = 0; i < args.length; i++) {
                if ("--port".equals(args[i]) && i + 1 < args.length) {
                    port = args[i + 1];
                }
            }
            
            System.out.println("Configurando Worker en puerto: " + port);

            // Configuración inicial
            com.zeroc.Ice.InitializationData initData = new com.zeroc.Ice.InitializationData();
            initData.properties = Util.createProperties(args);
            initData.properties.load("config/config.worker");
            // Sobrescribir el puerto
            initData.properties.setProperty("WorkerAdapter.Endpoints", "tcp -p " + port);
            
            // Inicializar comunicador ICE
            communicator = Util.initialize(args, initData);
            
            // Inicializar Repositorios
            System.out.println("Inicializando repositorios en Worker...");
            mio.server.repository.StopRepository stopRepo = mio.server.repository.RepositoryFactory.createStopRepository("CSV", "data/stops-241.csv");
            mio.server.repository.LineRepository lineRepo = mio.server.repository.RepositoryFactory.createLineRepository("CSV", "data/lines-241.csv");
            mio.server.repository.LineStopRepository lineStopRepo = mio.server.repository.RepositoryFactory.createLineStopRepository("CSV", "data/linestops-241.csv");

            // Cargar datos (cada worker tiene su propia copia del grafo)
            System.out.println("Cargando grafo en memoria del Worker...");
            GraphBuilder graphBuilder = new GraphBuilder(stopRepo, lineRepo, lineStopRepo);
            try {
                graphBuilder.loadData();
            } catch (Exception e) {
                System.err.println("Error cargando datos en Worker: " + e.getMessage());
                return;
            }
            
            // Crear adaptador
            ObjectAdapter adapter = communicator.createObjectAdapter("WorkerAdapter");
            
            // Crear servant
            RouteWorkerI worker = new RouteWorkerI(graphBuilder);
            
            // Registrar servant
            adapter.add(worker, Util.stringToIdentity("RouteWorker"));
            
            // Activar
            adapter.activate();
            
            System.out.println("WORKER ACTIVO en puerto " + port);
            
            // REGISTRARSE CON EL MASTER
            try {
                System.out.println("Intentando registrarse con el Master...");
                // Asumimos que el Master está en el puerto 10000 (config.server)
                com.zeroc.Ice.ObjectPrx base = communicator.stringToProxy("GraphService:tcp -h localhost -p 10000");
                mioice.GraphServicePrx master = mioice.GraphServicePrx.checkedCast(base);
                
                if (master == null) {
                    throw new Error("Invalid proxy");
                }
                
                // Construir el proxy de este worker para enviarlo al Master
                String myProxy = "RouteWorker:tcp -h localhost -p " + port;
                master.registerWorker(myProxy);
                System.out.println("REGISTRADO EXITOSAMENTE con el Master: " + myProxy);
                
            } catch (Exception e) {
                System.err.println("ADVERTENCIA: No se pudo registrar con el Master. Asegúrese de que el servidor esté corriendo.");
                System.err.println("Error: " + e.getMessage());
                // No salimos, el worker puede seguir corriendo y el Master podría registrarlo manualmente si tuviera esa función,
                // o simplemente esperamos a que el Master se reinicie (aunque el registro es push).
                // En este diseño, si falla el registro, el Master no usará este worker.
            }
            
            communicator.waitForShutdown();
            
        } catch (Exception e) {
            System.err.println("Error en Worker: " + e);
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
