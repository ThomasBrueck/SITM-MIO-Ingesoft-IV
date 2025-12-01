package mio.server.data;

import mioice.*;
import mio.server.model.*;
import mio.server.repository.*;
import mio.server.database.ArcStatsRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Constructor del grafo de rutas del sistema MIO
 * Lee los archivos CSV y construye las estructuras de datos y grafos
 */
public class GraphBuilder {

    private StopRepository stopRepository;
    private LineRepository lineRepository;
    private LineStopRepository lineStopRepository;

    private Map<Integer, Stop> stopsMap;
    private Map<Integer, Line> linesMap;
    private List<Arc> allArcs;
    private Map<String, List<Arc>> arcsByLineAndOrientation;

    public GraphBuilder(StopRepository stopRepository, LineRepository lineRepository, LineStopRepository lineStopRepository) {
        this.stopRepository = stopRepository;
        this.lineRepository = lineRepository;
        this.lineStopRepository = lineStopRepository;
        
        this.stopsMap = new HashMap<>();
        this.linesMap = new HashMap<>();
        this.allArcs = new ArrayList<>();
        this.arcsByLineAndOrientation = new HashMap<>();
    }
    
    // Constructor vacío para compatibilidad temporal o tests
    public GraphBuilder() {
        this(null, null, null);
    }

    /**
     * Carga los datos usando los repositorios configurados
     */
    public void loadData() {
        System.out.println("SISTEMA DE GRAFOS SITM-MIO - Cargando datos desde Repositorios...");

        if (stopRepository == null || lineRepository == null || lineStopRepository == null) {
            throw new IllegalStateException("Repositorios no inicializados en GraphBuilder");
        }

        // 1. Leer paradas desde repositorio
        List<Stop> stops = stopRepository.findAll();
        for (Stop stop : stops) {
            stopsMap.put(stop.stopId, stop);
        }

        // 2. Leer rutas desde repositorio
        List<Line> lines = lineRepository.findAll();
        for (Line line : lines) {
            linesMap.put(line.lineId, line);
        }

        // 3. Leer relaciones y construir arcos desde repositorio
        List<LineStopData> lineStops = lineStopRepository.findAll();
        buildArcs(lineStops);

        // 4. Cargar velocidades calculadas previamente desde PostgreSQL (si existen)
        loadArcSpeedsFromDatabase();

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
    /**
     * Encuentra la ruta más corta entre dos paradas usando BFS
     * (Delegado a PathFinder)
     */
    public Map<String, Object> findShortestRoute(int originStopId, int destStopId) {
        return mio.server.util.PathFinder.findShortestRoute(
            originStopId, 
            destStopId, 
            stopsMap, 
            allArcs
        );
    }
    
    /**
     * Encuentra todas las paradas alcanzables desde una parada de origen
     * (Delegado a PathFinder)
     */
    public Set<Integer> findReachableStops(int originStopId) {
        return mio.server.util.PathFinder.findReachableStops(
            originStopId, 
            stopsMap, 
            allArcs
        );
    }
    
    /**
     * Carga las velocidades promedio desde PostgreSQL para arcos existentes
     * Permite evitar recálculo desde datagramas en ejecuciones posteriores
     */
    private void loadArcSpeedsFromDatabase() {
        try {
            ArcStatsRepository arcStatsRepo = new ArcStatsRepository();
            
            // Verificar si existen datos
            if (!arcStatsRepo.hasData()) {
                System.out.println("[DB] No hay velocidades previas, se calcularan en este análisis");
                return;
            }
            
            // Cargar todas las estadísticas
            List<ArcStat> dbStats = arcStatsRepo.getAllArcStats();
            int loadedCount = 0;
            
            // Crear índice de arcos para búsqueda rápida
            Map<String, Arc> arcIndex = new HashMap<>();
            for (Arc arc : allArcs) {
                String key = arc.lineId + ":" + arc.orientation + ":" + arc.sequenceNum;
                arcIndex.put(key, arc);
            }
            
            // Aplicar velocidades desde BD
            for (ArcStat stat : dbStats) {
                String key = stat.lineId + ":" + stat.orientation + ":" + stat.sequenceNum;
                Arc arc = arcIndex.get(key);
                
                if (arc != null && stat.sumTime > 0 && stat.count > 0) {
                    // Calcular velocidad promedio
                    double avgSpeedKmh = (stat.sumDistance / (stat.sumTime / 3600000.0));
                    arc.avgSpeed = avgSpeedKmh;
                    loadedCount++;
                }
            }
            
            if (loadedCount > 0) {
                System.out.println("[DB] Cargadas " + loadedCount + " velocidades desde PostgreSQL");
                ArcStatsRepository.GlobalStats globalStats = arcStatsRepo.getGlobalStats();
                System.out.println("[DB] " + globalStats);
            }
            
        } catch (Exception e) {
            System.err.println("[DB] Error cargando velocidades desde base de datos: " + e.getMessage());
            System.err.println("[DB] Se procederá sin datos previos");
        }
    }
}
