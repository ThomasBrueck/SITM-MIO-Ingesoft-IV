package mio.client;

import Mio.*;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

/**
 * Cliente ICE del sistema MIO
 * Se conecta al servidor y proporciona acceso a los servicios remotos
 */
public class MioGraphClient {
    
    private Communicator communicator;
    private RouteServicePrx routeService;
    private GraphServicePrx graphService;
    
    /**
     * Inicializa el cliente y se conecta al servidor
     */
    public void initialize(String[] args) {
        try {
            // Inicializar comunicador ICE
            communicator = Util.initialize(args, "config/config.client");
            
            // Obtener proxies a los servicios
            ObjectPrx routeBase = communicator.stringToProxy("RouteService:default -p 10000");
            routeService = RouteServicePrx.checkedCast(routeBase);
            if (routeService == null) {
                throw new RuntimeException("Proxy RouteService inválido");
            }
            
            ObjectPrx graphBase = communicator.stringToProxy("GraphService:default -p 10000");
            graphService = GraphServicePrx.checkedCast(graphBase);
            if (graphService == null) {
                throw new RuntimeException("Proxy GraphService inválido");
            }
            
            System.out.println("✓ Cliente conectado al servidor ICE");
            
        } catch (Exception e) {
            System.err.println("Error inicializando cliente: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Obtiene todas las rutas disponibles
     */
    public Line[] getAllLines() {
        try {
            return routeService.getAllLines();
        } catch (Exception e) {
            System.err.println("Error obteniendo rutas: " + e.getMessage());
            return new Line[0];
        }
    }
    
    /**
     * Obtiene una ruta por ID
     */
    public Line getLineById(int lineId) throws LineNotFoundException {
        return routeService.getLineById(lineId);
    }
    
    /**
     * Obtiene las paradas de una ruta
     */
    public Stop[] getStopsByLine(int lineId, int orientation) 
            throws LineNotFoundException, InvalidOrientationException {
        return routeService.getStopsByLine(lineId, orientation);
    }
    
    /**
     * Obtiene los arcos de una ruta
     */
    public Arc[] getArcsByLine(int lineId, int orientation) 
            throws LineNotFoundException, InvalidOrientationException {
        return routeService.getArcsByLine(lineId, orientation);
    }
    
    /**
     * Obtiene una parada por ID
     */
    public Stop getStopById(int stopId) throws StopNotFoundException {
        return routeService.getStopById(stopId);
    }
    
    /**
     * Obtiene todas las paradas del sistema
     */
    public Stop[] getAllStops() {
        try {
            return graphService.getAllStops();
        } catch (Exception e) {
            System.err.println("Error obteniendo paradas: " + e.getMessage());
            return new Stop[0];
        }
    }
    
    /**
     * Obtiene todos los arcos del sistema
     */
    public Arc[] getAllArcs() {
        try {
            return graphService.getAllArcs();
        } catch (Exception e) {
            System.err.println("Error obteniendo arcos: " + e.getMessage());
            return new Arc[0];
        }
    }
    
    /**
     * Obtiene estadísticas del grafo
     */
    public int[] getGraphStatistics() {
        try {
            return graphService.getGraphStatistics();
        } catch (Exception e) {
            System.err.println("Error obteniendo estadísticas: " + e.getMessage());
            return new int[0];
        }
    }
    
    /**
     * Solicita al servidor que imprima los arcos en consola
     */
    public void printArcsToConsole() {
        try {
            graphService.printArcsToConsole();
        } catch (Exception e) {
            System.err.println("Error solicitando impresión: " + e.getMessage());
        }
    }
    
    /**
     * Encuentra la ruta más corta entre dos paradas
     */
    public RouteResult findRoute(int originStopId, int destStopId) throws StopNotFoundException {
        try {
            return graphService.findRoute(originStopId, destStopId);
        } catch (StopNotFoundException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Error buscando ruta: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al buscar ruta", e);
        }
    }
    
    /**
     * Obtiene todas las paradas alcanzables desde una parada de origen
     */
    public int[] getReachableStops(int originStopId) throws StopNotFoundException {
        try {
            return graphService.getReachableStops(originStopId);
        } catch (StopNotFoundException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Error obteniendo paradas alcanzables: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error al obtener paradas alcanzables", e);
        }
    }
    
    /**
     * Cierra la conexión
     */
    public void shutdown() {
        if (communicator != null) {
            try {
                communicator.destroy();
                System.out.println("✓ Cliente desconectado");
            } catch (Exception e) {
                System.err.println("Error cerrando comunicador: " + e.getMessage());
            }
        }
    }
    
    /**
     * Verifica si el cliente está conectado
     */
    public boolean isConnected() {
        return communicator != null && routeService != null && graphService != null;
    }
}
