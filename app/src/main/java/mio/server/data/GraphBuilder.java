package mio.server.data;

import mioice.*;
import mio.server.model.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Constructor del grafo de rutas del sistema MIO
 * Lee los archivos CSV y construye las estructuras de datos y grafos
 */
public class GraphBuilder {

    private Map<Integer, Stop> stopsMap;
    private Map<Integer, Line> linesMap;
    private List<Arc> allArcs;
    private Map<String, List<Arc>> arcsByLineAndOrientation;

    public GraphBuilder() {
        this.stopsMap = new HashMap<>();
        this.linesMap = new HashMap<>();
        this.allArcs = new ArrayList<>();
        this.arcsByLineAndOrientation = new HashMap<>();
    }

    /**
     * Carga los datos desde los archivos CSV
     */
    public void loadData(String linesPath, String stopsPath, String lineStopsPath) throws IOException {
        System.out.println("SISTEMA DE GRAFOS SITM-MIO - Cargando datos...");

        // 1. Leer paradas
        List<StopData> stops = CSVReader.readStops(stopsPath);
        for (StopData sd : stops) {
            Stop stop = new Stop();
            stop.stopId = sd.getStopId();
            stop.planVersionId = sd.getPlanVersionId();
            stop.shortName = sd.getShortName();
            stop.longName = sd.getLongName();
            stop.gpsX = sd.getGpsX();
            stop.gpsY = sd.getGpsY();
            stop.decimalLong = sd.getDecimalLong();
            stop.decimalLat = sd.getDecimalLat();
            stopsMap.put(stop.stopId, stop);
        }

        // 2. Leer rutas
        List<LineData> lines = CSVReader.readLines(linesPath);
        for (LineData ld : lines) {
            Line line = new Line();
            line.lineId = ld.getLineId();
            line.planVersionId = ld.getPlanVersionId();
            line.shortName = ld.getShortName();
            line.description = ld.getDescription();
            line.activationDate = ld.getActivationDate();
            linesMap.put(line.lineId, line);
        }

        // 3. Leer relaciones y construir arcos
        List<LineStopData> lineStops = CSVReader.readLineStops(lineStopsPath);
        buildArcs(lineStops);

        System.out.println("Datos cargados exitosamente:");
        System.out.println("Rutas: " + String.format("%-51d", linesMap.size()));
        System.out.println("Paradas: " + String.format("%-49d", stopsMap.size()));
        System.out.println("Arcos totales: " + String.format("%-44d", allArcs.size()));
    }

    /**
     * Construye los arcos a partir de las relaciones línea-parada
     */
    private void buildArcs(List<LineStopData> lineStops) {
        // Agrupar por línea, variante y orientación para respetar la topología real
        Map<String, List<LineStopData>> grouped = lineStops.stream()
                .collect(Collectors
                        .groupingBy(ls -> ls.getLineId() + "_" + ls.getLineVariant() + "_" + ls.getOrientation()));

        // Para cada grupo, crear arcos entre paradas consecutivas
        for (Map.Entry<String, List<LineStopData>> entry : grouped.entrySet()) {
            List<LineStopData> stops = entry.getValue();

            // Ordenar por secuencia
            stops.sort(Comparator.comparingInt(LineStopData::getStopSequence));

            // Crear arcos entre paradas consecutivas
            for (int i = 0; i < stops.size() - 1; i++) {
                LineStopData current = stops.get(i);
                LineStopData next = stops.get(i + 1);

                Stop fromStop = stopsMap.get(current.getStopId());
                Stop toStop = stopsMap.get(next.getStopId());

                if (fromStop != null && toStop != null) {
                    Line line = linesMap.get(current.getLineId());

                    Arc arc = new Arc();
                    arc.lineId = current.getLineId();
                    arc.lineName = line != null ? line.shortName : "?";
                    arc.orientation = current.getOrientation();
                    arc.sequenceNum = current.getStopSequence();
                    arc.fromStop = fromStop;
                    arc.toStop = toStop;
                    arc.distance = calculateDistance(fromStop, toStop);
                    arc.avgSpeed = 0.0;

                    allArcs.add(arc);

                    // Agrupar por línea y orientación
                    String key = arc.lineId + "_" + arc.orientation;
                    arcsByLineAndOrientation.computeIfAbsent(key, k -> new ArrayList<>()).add(arc);
                }
            }
        }
    }

    /**
     * Calcula la distancia entre dos paradas usando la fórmula de Haversine
     */
    private double calculateDistance(Stop from, Stop to) {
        double lat1 = from.decimalLat;
        double lon1 = from.decimalLong;
        double lat2 = to.decimalLat;
        double lon2 = to.decimalLong;

        final double R = 6371.0; // Radio de la Tierra en km

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * Imprime el grafo completo en consola
     */
    public void printGraphToConsole() {
        System.out.println("LISTADO COMPLETO DE ARCOS POR RUTA Y ORIENTACIÓN");

        // Ordenar líneas por ID
        List<Integer> lineIds = new ArrayList<>(linesMap.keySet());
        Collections.sort(lineIds);

        int totalArcsOrientation0 = 0;
        int totalArcsOrientation1 = 0;

        for (Integer lineId : lineIds) {
            Line line = linesMap.get(lineId);

            System.out.println("RUTA: " + line.shortName + " - " + truncate(line.description, 65));
            System.out.println("ID: " + lineId);

            // Orientación 0 (IDA)
            String key0 = lineId + "_0";
            if (arcsByLineAndOrientation.containsKey(key0)) {
                System.out.println("\n  ➤ ORIENTACIÓN 0 (IDA):");
                List<Arc> arcs = arcsByLineAndOrientation.get(key0);
                arcs.sort(Comparator.comparingInt(a -> a.sequenceNum));

                for (Arc arc : arcs) {
                    System.out.printf("    [%3d] %-15s (%6d) → %-15s (%6d)  [%.3f km]%n",
                            arc.sequenceNum,
                            truncate(arc.fromStop.shortName, 15), arc.fromStop.stopId,
                            truncate(arc.toStop.shortName, 15), arc.toStop.stopId,
                            arc.distance);
                }
                totalArcsOrientation0 += arcs.size();
            }

            // Orientación 1 (REGRESO)
            String key1 = lineId + "_1";
            if (arcsByLineAndOrientation.containsKey(key1)) {
                System.out.println("\n  ORIENTACIÓN 1 (REGRESO):");
                List<Arc> arcs = arcsByLineAndOrientation.get(key1);
                arcs.sort(Comparator.comparingInt(a -> a.sequenceNum));

                for (Arc arc : arcs) {
                    System.out.printf("    [%3d] %-15s (%6d) → %-15s (%6d)  [%.3f km]%n",
                            arc.sequenceNum,
                            truncate(arc.fromStop.shortName, 15), arc.fromStop.stopId,
                            truncate(arc.toStop.shortName, 15), arc.toStop.stopId,
                            arc.distance);
                }
                totalArcsOrientation1 += arcs.size();
            }

            System.out.println();
        }

        System.out.println("ESTADÍSTICAS DEL GRAFO");
        System.out.println("Total de rutas: " + String.format("%-57d", linesMap.size()));
        System.out.println("Total de paradas: " + String.format("%-55d", stopsMap.size()));
        System.out.println("Total de arcos: " + String.format("%-57d", allArcs.size()));
        System.out.println("Arcos orientación 0 (IDA): " + String.format("%-43d", totalArcsOrientation0));
        System.out.println("Arcos orientación 1 (REGRESO): " + String.format("%-39d", totalArcsOrientation1) + "\n");
    }

    /**
     * Trunca un string a la longitud especificada
     */
    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return String.format("%-" + maxLength + "s", str);
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    // Getters
    public Map<Integer, Stop> getStopsMap() {
        return stopsMap;
    }

    public Map<Integer, Line> getLinesMap() {
        return linesMap;
    }

    public List<Arc> getAllArcs() {
        return allArcs;
    }

    public Map<String, List<Arc>> getArcsByLineAndOrientation() {
        return arcsByLineAndOrientation;
    }

    /**
     * Obtiene las paradas de una línea específica
     */
    public List<Stop> getStopsByLine(int lineId, int orientation) {
        String key = lineId + "_" + orientation;
        List<Arc> arcs = arcsByLineAndOrientation.get(key);

        if (arcs == null || arcs.isEmpty()) {
            return new ArrayList<>();
        }

        List<Stop> stops = new ArrayList<>();
        arcs.sort(Comparator.comparingInt(a -> a.sequenceNum));

        // Agregar primera parada
        stops.add(arcs.get(0).fromStop);

        // Agregar paradas destino de cada arco
        for (Arc arc : arcs) {
            stops.add(arc.toStop);
        }

        return stops;
    }

    /**
     * Obtiene los arcos de una línea específica
     */
    public List<Arc> getArcsByLine(int lineId, int orientation) {
        String key = lineId + "_" + orientation;
        List<Arc> arcs = arcsByLineAndOrientation.get(key);

        if (arcs == null) {
            return new ArrayList<>();
        }

        List<Arc> result = new ArrayList<>(arcs);
        result.sort(Comparator.comparingInt(a -> a.sequenceNum));
        return result;
    }

    /**
     * Encuentra la ruta más corta entre dos paradas usando BFS
     * 
     * @param originStopId ID de la parada de origen
     * @param destStopId   ID de la parada de destino
     * @return Mapa con información de la ruta encontrada
     */
    public Map<String, Object> findShortestRoute(int originStopId, int destStopId) {
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
     * usando BFS (Breadth-First Search)
     * 
     * @param originStopId ID de la parada de origen
     * @return Set con los IDs de todas las paradas alcanzables (incluyendo la
     *         propia parada de origen)
     */
    public Set<Integer> findReachableStops(int originStopId) {
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
