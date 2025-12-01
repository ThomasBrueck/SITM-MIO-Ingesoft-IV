package mio.server.worker;

import mioice.*;
import mio.server.data.GraphBuilder;
import com.zeroc.Ice.Current;

import java.util.*;

/**
 * Implementación del Worker que calcula rutas
 * Recibe la tarea del Master y ejecuta el algoritmo BFS
 */
public class RouteWorkerI implements RouteWorker {
    
    private GraphBuilder graphBuilder;
    
    public RouteWorkerI(GraphBuilder graphBuilder) {
        this.graphBuilder = graphBuilder;
    }
    
    @Override
    public RouteResult findRoute(int originStopId, int destStopId, Current current) 
            throws StopNotFoundException {
        
        // System.out.println("Worker recibiendo tarea: " + originStopId + " -> " + destStopId);
        
        // Validar que las paradas existen
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
        
        // Llamar al algoritmo de búsqueda (Delegado a PathFinder)
        Map<String, Object> searchResult = mio.server.util.PathFinder.findShortestRoute(
            originStopId, 
            destStopId, 
            graphBuilder.getStopsMap(), 
            graphBuilder.getAllArcs()
        );
        
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
        
        return result;
    }
}
