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
            
            // Inicializar comunicador ICE
            // Usamos un archivo de config diferente o argumentos para el puerto
            communicator = Util.initialize(args, "config/config.worker");
            
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
            // El nombre del adaptador y puerto deben ser configurables para lanzar múltiples workers
            ObjectAdapter adapter = communicator.createObjectAdapter("WorkerAdapter");
            
            // Crear servant
            RouteWorkerI worker = new RouteWorkerI(graphBuilder);
            
            // Registrar servant
            adapter.add(worker, Util.stringToIdentity("RouteWorker"));
            
            // Activar
            adapter.activate();
            
            System.out.println("WORKER ACTIVO y esperando tareas...");
            
            communicator.waitForShutdown();
            
        } catch (Exception e) {
            System.err.println("Error en Worker: " + e);
            status = 1;
        } finally {
            if (communicator != null) {
                communicator.destroy();
            }
        }
        
        System.exit(status);
    }
}
