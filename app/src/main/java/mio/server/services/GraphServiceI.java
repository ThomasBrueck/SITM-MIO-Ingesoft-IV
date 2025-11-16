package mio.server.services;

import Mio.*;
import mio.server.data.GraphBuilder;
import com.zeroc.Ice.Current;

import java.util.*;

/**
 * Implementación del servicio GraphService
 * Proporciona métodos para consultar información del grafo completo
 */
public class GraphServiceI implements GraphService {
    
    private GraphBuilder graphBuilder;
    
    public GraphServiceI(GraphBuilder graphBuilder) {
        this.graphBuilder = graphBuilder;
    }
    
    @Override
    public Stop[] getAllStops(Current current) {
        List<Stop> stops = new ArrayList<>(graphBuilder.getStopsMap().values());
        stops.sort((a, b) -> Integer.compare(a.stopId, b.stopId));
        return stops.toArray(new Stop[0]);
    }
    
    @Override
    public Arc[] getAllArcs(Current current) {
        List<Arc> arcs = new ArrayList<>(graphBuilder.getAllArcs());
        // Ordenar por lineId, luego por orientation, luego por sequence
        arcs.sort((a, b) -> {
            if (a.lineId != b.lineId) {
                return Integer.compare(a.lineId, b.lineId);
            }
            if (a.orientation != b.orientation) {
                return Integer.compare(a.orientation, b.orientation);
            }
            return Integer.compare(a.sequenceNum, b.sequenceNum);
        });
        return arcs.toArray(new Arc[0]);
    }
    
    @Override
    public Arc[] getArcsByRouteAndOrientation(Current current) {
        // Este método retorna los arcos ordenados por ruta y orientación
        return getAllArcs(current);
    }
    
    @Override
    public int[] getGraphStatistics(Current current) {
        int numRoutes = graphBuilder.getLinesMap().size();
        int numStops = graphBuilder.getStopsMap().size();
        int numArcs = graphBuilder.getAllArcs().size();
        
        // Contar arcos por orientación
        int numArcsOrientation0 = 0;
        int numArcsOrientation1 = 0;
        
        for (Arc arc : graphBuilder.getAllArcs()) {
            if (arc.orientation == 0) {
                numArcsOrientation0++;
            } else {
                numArcsOrientation1++;
            }
        }
        
        return new int[] {numRoutes, numStops, numArcs, numArcsOrientation0, numArcsOrientation1};
    }
    
    @Override
    public void printArcsToConsole(Current current) {
        System.out.println("\nIMPRESIÓN SOLICITADA POR CLIENTE");
        graphBuilder.printGraphToConsole();
    }
    
    @Override
    public RouteResult findRoute(int originStopId, int destStopId, Current current) 
            throws StopNotFoundException {
        
        // Validar que las paradas existen antes de buscar
        if (!graphBuilder.getStopsMap().containsKey(originStopId)) {
            StopNotFoundException ex = new StopNotFoundException();
            ex.stopId = originStopId;
            ex.message = "Parada de origen no encontrada: " + originStopId;
            throw ex;
        }
        
        if (!graphBuilder.getStopsMap().containsKey(destStopId)) {
            StopNotFoundException ex = new StopNotFoundException();
            ex.stopId = destStopId;
            ex.message = "Parada de destino no encontrada: " + destStopId;
            throw ex;
        }
        
        // Llamar al algoritmo de búsqueda
        Map<String, Object> searchResult = graphBuilder.findShortestRoute(originStopId, destStopId);
        
        // Convertir el resultado a la estructura RouteResult de ICE
        RouteResult result = new RouteResult();
        result.found = (Boolean) searchResult.get("found");
        result.message = (String) searchResult.get("message");
        result.totalDistance = (Double) searchResult.get("totalDistance");
        result.numTransfers = (Integer) searchResult.get("numTransfers");
        
        @SuppressWarnings("unchecked")
        List<Stop> stops = (List<Stop>) searchResult.get("stops");
        result.stops = stops.toArray(new Stop[0]);
        
        @SuppressWarnings("unchecked")
        List<Arc> arcs = (List<Arc>) searchResult.get("arcs");
        result.arcs = arcs.toArray(new Arc[0]);
        
        // Log en el servidor
        System.out.println("\nBÚSQUEDA DE RUTA SOLICITADA");
        System.out.printf("Origen:  %s (ID: %d)%n", 
                         graphBuilder.getStopsMap().get(originStopId).shortName, originStopId);
        System.out.printf("Destino: %s (ID: %d)%n", 
                         graphBuilder.getStopsMap().get(destStopId).shortName, destStopId);
        if (result.found) {
            System.out.printf("Ruta encontrada: %d paradas, %.2f km, %d transbordos%n", 
                             result.stops.length, result.totalDistance, result.numTransfers);
        } else {
            System.out.println("No se encontró ruta");
        }
        
        return result;
    }
    
    @Override
    public int[] getReachableStops(int originStopId, Current current) 
            throws StopNotFoundException {
        
        // Validar que la parada existe
        if (!graphBuilder.getStopsMap().containsKey(originStopId)) {
            StopNotFoundException ex = new StopNotFoundException();
            ex.stopId = originStopId;
            ex.message = "Parada de origen no encontrada: " + originStopId;
            throw ex;
        }
        
        // Obtener paradas alcanzables
        Set<Integer> reachableSet = graphBuilder.findReachableStops(originStopId);
        
        // Convertir Set a array de int
        int[] reachableArray = new int[reachableSet.size()];
        int i = 0;
        for (Integer stopId : reachableSet) {
            reachableArray[i++] = stopId;
        }
        
        // Log en el servidor
        System.out.println("\nCÁLCULO DE ALCANZABILIDAD SOLICITADO");
        System.out.printf("Origen: %s (ID: %d)%n", 
                         graphBuilder.getStopsMap().get(originStopId).shortName, originStopId);
        System.out.printf("Paradas alcanzables: %d de %d total%n", 
                         reachableSet.size(), graphBuilder.getStopsMap().size());
        
        return reachableArray;
    }
}

