package mio.server;

import mioice.*;
import mio.server.data.GraphBuilder;
import mio.server.services.RouteServiceI;
import mio.server.services.GraphServiceI;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

import java.io.IOException;

/**
 * Servidor ICE del sistema MIO
 * Carga los datos CSV, construye el grafo y expone los servicios remotos
 */
public class MioServer {
    
    public static void main(String[] args) {
        int status = 0;
        Communicator communicator = null;
        
        try {
            // Imprimir banner
            printBanner();
            
            // Inicializar comunicador ICE
            communicator = Util.initialize(args, "config/config.server");
            
            // Cargar datos y construir grafo
            System.out.println("Inicializando servidor...\n");
            GraphBuilder graphBuilder = new GraphBuilder();
            
            try {
                // Intentar cargar desde recursos
                graphBuilder.loadData(
                    "data/lines-241.csv",
                    "data/stops-241.csv",
                    "data/linestops-241.csv"
                );
            } catch (IOException e) {
                System.err.println("Error cargando archivos CSV: " + e.getMessage());
                status = 1;
                return;
            }
            
            // Imprimir el grafo en consola (cumple con el requerimiento A)
            graphBuilder.printGraphToConsole();
            
            // Crear adaptador de objetos
            ObjectAdapter adapter = communicator.createObjectAdapter("MioAdapter");
            
            // Crear e instalar servants
            RouteService routeService = new RouteServiceI(graphBuilder);
            GraphService graphService = new GraphServiceI(graphBuilder);
            
            adapter.add(routeService, Util.stringToIdentity("RouteService"));
            adapter.add(graphService, Util.stringToIdentity("GraphService"));
            
            // Activar adaptador
            adapter.activate();
            
            System.out.println("SERVIDOR ICE ACTIVO");
            System.out.println("Servicios disponibles:");
            System.out.println("RouteService - Consultas de rutas y paradas              ║");
            System.out.println("GraphService - Consultas del grafo completo              ║");
            
            // Esperar por shutdown
            communicator.waitForShutdown();
            
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
        
        System.exit(status);
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
