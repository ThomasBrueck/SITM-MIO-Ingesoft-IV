package mio.server.util;

import mioice.*;
import java.util.*;

/**
 * Utilidad para algoritmos de búsqueda de rutas (BFS)
 * Extraída de GraphBuilder para eliminar duplicación de código
 */
public class PathFinder {

    /**
     * Encuentra la ruta más corta entre dos paradas usando BFS
     */
    public static Map<String, Object> findShortestRoute(
            int originStopId, 
            int destStopId, 
            Map<Integer, Stop> stopsMap, 
            List<Arc> allArcs) {
            
        Map<String, Object> result = new HashMap<>();

        // Validar que las paradas existen
        if (!stopsMap.containsKey(originStopId)) {
            result.put("found", false);
            result.put("message", "Parada de origen no encontrada: " + originStopId);
            return result;
        }

        if (!stopsMap.containsKey(destStopId)) {
            result.put("found", false);
            result.put("message", "Parada de destino no encontrada: " + destStopId);
            return result;
        }

        // Caso especial: origen = destino
        if (originStopId == destStopId) {
            result.put("found", true);
            result.put("stops", new ArrayList<>(Arrays.asList(stopsMap.get(originStopId))));
            result.put("arcs", new ArrayList<>());
            result.put("totalDistance", 0.0);
            result.put("numTransfers", 0);
            result.put("message", "Origen y destino son la misma parada");
            return result;
        }

        // BFS para encontrar el camino más corto
        Queue<Integer> queue = new LinkedList<>();
        Map<Integer, Integer> parent = new HashMap<>(); // Para reconstruir el camino
        Map<Integer, Arc> arcToParent = new HashMap<>(); // Arco usado para llegar a cada nodo
        Set<Integer> visited = new HashSet<>();

        queue.add(originStopId);
        visited.add(originStopId);
        parent.put(originStopId, null);

        boolean found = false;

        // BFS
        while (!queue.isEmpty() && !found) {
            int currentStopId = queue.poll();

            // Buscar todos los arcos que salen de esta parada
            // NOTA: Esto podría optimizarse con un mapa de adyacencia precalculado
            for (Arc arc : allArcs) {
                if (arc.fromStop.stopId == currentStopId) {
                    int nextStopId = arc.toStop.stopId;

                    if (!visited.contains(nextStopId)) {
                        visited.add(nextStopId);
                        parent.put(nextStopId, currentStopId);
                        arcToParent.put(nextStopId, arc);
                        queue.add(nextStopId);

                        // ¿Llegamos al destino?
                        if (nextStopId == destStopId) {
                            found = true;
                            break;
                        }
                    }
                }
            }
        }

        // Construir resultado
        if (!found) {
            result.put("found", false);
            result.put("message", "No se encontró ruta entre las paradas " + originStopId + " y " + destStopId);
            result.put("stops", new ArrayList<>());
            result.put("arcs", new ArrayList<>());
            result.put("totalDistance", 0.0);
            result.put("numTransfers", 0);
            return result;
        }

        // Reconstruir el camino desde destino hasta origen
        List<Integer> pathStopIds = new ArrayList<>();
        List<Arc> pathArcs = new ArrayList<>();
        int current = destStopId;

        while (current != originStopId) {
            pathStopIds.add(0, current);
            Arc arc = arcToParent.get(current);
            if (arc != null) {
                pathArcs.add(0, arc);
            }
            current = parent.get(current);
        }
        pathStopIds.add(0, originStopId);

        // Convertir IDs a objetos Stop
        List<Stop> pathStops = new ArrayList<>();
        for (int stopId : pathStopIds) {
            pathStops.add(stopsMap.get(stopId));
        }

        // Calcular distancia total
        double totalDistance = 0.0;
        for (Arc arc : pathArcs) {
            totalDistance += arc.distance;
        }

        // Contar transbordos (cambios de línea)
        int numTransfers = 0;
        for (int i = 1; i < pathArcs.size(); i++) {
            if (pathArcs.get(i).lineId != pathArcs.get(i - 1).lineId) {
                numTransfers++;
            }
        }

        result.put("found", true);
        result.put("stops", pathStops);
        result.put("arcs", pathArcs);
        result.put("totalDistance", totalDistance);
        result.put("numTransfers", numTransfers);
        result.put("message", String.format("Ruta encontrada: %d paradas, %.2f km, %d transbordos",
                pathStops.size(), totalDistance, numTransfers));

        return result;
    }

    /**
     * Encuentra todas las paradas alcanzables desde una parada de origen
     */
    public static Set<Integer> findReachableStops(
            int originStopId, 
            Map<Integer, Stop> stopsMap, 
            List<Arc> allArcs) {
            
        Set<Integer> reachable = new HashSet<>();

        // Validar que la parada existe
        if (!stopsMap.containsKey(originStopId)) {
            return reachable; // Retornar conjunto vacío
        }

        // BFS para explorar todas las paradas alcanzables
        Queue<Integer> queue = new LinkedList<>();
        queue.add(originStopId);
        reachable.add(originStopId);

        while (!queue.isEmpty()) {
            int currentStopId = queue.poll();

            // Buscar todos los arcos que salen de esta parada
            for (Arc arc : allArcs) {
                if (arc.fromStop.stopId == currentStopId) {
                    int nextStopId = arc.toStop.stopId;

                    if (!reachable.contains(nextStopId)) {
                        reachable.add(nextStopId);
                        queue.add(nextStopId);
                    }
                }
            }
        }

        return reachable;
    }
}
