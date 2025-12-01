package mio.server.services;

import mioice.*;
import mio.server.data.GraphBuilder;
import com.zeroc.Ice.Current;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementación del servicio GraphService (MASTER)
 * Delega el cálculo de rutas a los Workers registrados
 */
public class GraphServiceI implements GraphService {
    
    private GraphBuilder graphBuilder;
    private List<RouteWorkerPrx> workers;
    private AtomicInteger nextWorkerIndex;
    
    public GraphServiceI(GraphBuilder graphBuilder) {
        this.graphBuilder = graphBuilder;
        this.workers = new ArrayList<>();
        this.nextWorkerIndex = new AtomicInteger(0);
    }
    
    public void addWorker(RouteWorkerPrx worker) {
        synchronized(workers) {
            workers.add(worker);
        }
        System.out.println("MASTER: Worker agregado manualmente -> " + worker);
    }
    
    @Override
    public void registerWorker(String proxy, Current current) {
        try {
            com.zeroc.Ice.ObjectPrx base = current.adapter.getCommunicator().stringToProxy(proxy);
            RouteWorkerPrx worker = RouteWorkerPrx.checkedCast(base);
            if (worker != null) {
                addWorker(worker);
            }
        } catch (Exception e) {
            System.err.println("MASTER: Error registrando worker " + proxy + ": " + e.getMessage());
        }
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
        arcs.sort((a, b) -> {
            if (a.lineId != b.lineId) return Integer.compare(a.lineId, b.lineId);
            if (a.orientation != b.orientation) return Integer.compare(a.orientation, b.orientation);
            return Integer.compare(a.sequenceNum, b.sequenceNum);
        });
        return arcs.toArray(new Arc[0]);
    }
    
    @Override
    public Arc[] getArcsByRouteAndOrientation(Current current) {
        return getAllArcs(current);
    }
    
    @Override
    public int[] getGraphStatistics(Current current) {
        int numRoutes = graphBuilder.getLinesMap().size();
        int numStops = graphBuilder.getStopsMap().size();
        int numArcs = graphBuilder.getAllArcs().size();
        
        int numArcsOrientation0 = 0;
        int numArcsOrientation1 = 0;
        
        for (Arc arc : graphBuilder.getAllArcs()) {
            if (arc.orientation == 0) numArcsOrientation0++;
            else numArcsOrientation1++;
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
        
        // System.out.println("\nMASTER: Solicitud de ruta recibida (" + originStopId + " -> " + destStopId + ")");
        
        // Obtener un worker disponible (Round Robin)
        RouteWorkerPrx worker = getNextWorker();
        
        if (worker == null) {
            // System.err.println("MASTER: No hay workers disponibles. Ejecutando localmente (Fallback)...");
            // Fallback: Ejecutar localmente si no hay workers
            return executeLocally(originStopId, destStopId);
        }
        
        try {
            // System.out.println("MASTER: Delegando tarea a Worker...");
            return worker.findRoute(originStopId, destStopId);
        } catch (com.zeroc.Ice.ConnectionRefusedException | com.zeroc.Ice.TimeoutException e) {
            System.err.println("MASTER: Error de conexión con Worker: " + e.getMessage());
            // System.out.println("MASTER: Worker no disponible. Reintentando localmente...");
            return executeLocally(originStopId, destStopId);
        } catch (Exception e) {
            System.err.println("MASTER: Error inesperado en Worker: " + e.getMessage());
            e.printStackTrace();
            throw e; // Relanzar otras excepciones (ej: StopNotFoundException si viniera del worker)
        }
    }
    
    private RouteWorkerPrx getNextWorker() {
        synchronized(workers) {
            if (workers.isEmpty()) return null;
            int index = nextWorkerIndex.getAndIncrement() % workers.size();
            return workers.get(index);
        }
    }
    
    private RouteResult executeLocally(int originStopId, int destStopId) throws StopNotFoundException {
        // Lógica original para fallback
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
        
        Map<String, Object> searchResult = graphBuilder.findShortestRoute(originStopId, destStopId);
        
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
    
    @Override
    public int[] getReachableStops(int originStopId, Current current) 
            throws StopNotFoundException {
        
        // Esta operación es ligera, la mantenemos en el Master por ahora
        // O podríamos delegarla también si quisiéramos
        
        if (!graphBuilder.getStopsMap().containsKey(originStopId)) {
            StopNotFoundException ex = new StopNotFoundException();
            ex.stopId = originStopId;
            ex.message = "Parada de origen no encontrada: " + originStopId;
            throw ex;
        }
        
        Set<Integer> reachableSet = graphBuilder.findReachableStops(originStopId);
        
        int[] reachableArray = new int[reachableSet.size()];
        int i = 0;
        for (Integer stopId : reachableSet) {
            reachableArray[i++] = stopId;
        }
        
        return reachableArray;
    }
}

