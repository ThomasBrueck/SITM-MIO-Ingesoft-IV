package mio.server.worker;

import mioice.*;
import mio.server.data.GraphBuilder;
import com.zeroc.Ice.Current;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

/**
 * Worker encargado de analizar datagramas y calcular velocidades promedio por
 * arco
 */
public class AnalysisWorkerI implements AnalysisWorker {

    private GraphBuilder graphBuilder;

    public AnalysisWorkerI(GraphBuilder graphBuilder) {
        this.graphBuilder = graphBuilder;
    }

    @Override
    public ArcStat[] analyzeDatagrams(String filePath, int startLine, int endLine, Current current) {
        // Construir ruta absoluta desde el directorio de trabajo del worker
        // El master envía solo "data/datagrams4history.csv", cada worker lo busca en su carpeta local
        String absolutePath;
        if (filePath.startsWith("/") || filePath.contains(":")) {
            // Ruta absoluta o Windows (C:)
            absolutePath = filePath;
        } else {
            // Ruta relativa - usar directorio de trabajo del worker
            absolutePath = System.getProperty("user.dir") + "/" + filePath;
        }
        
        System.out.println("[Worker] Analizando archivo: " + absolutePath);
        System.out.println("[Worker] Líneas: " + (endLine == -1 ? "hasta el final" : (startLine + "-" + endLine)));

        Map<String, ArcStat> statsMap = new HashMap<>();
        Map<Integer, LastArcInfo> lastArcByBus = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(absolutePath))) {
            String line;
            int currentLineNum = 0;
            int processedLines = 0;
            long lastReportTime = System.currentTimeMillis();

            while ((line = reader.readLine()) != null) {
                currentLineNum++;

                if (currentLineNum < startLine)
                    continue;
                if (endLine != -1 && currentLineNum >= endLine)
                    break;
                processedLines++;
                if (processedLines % 100000 == 0) {
                    long now = System.currentTimeMillis();
                    double secondsElapsed = (now - lastReportTime) / 1000.0;
                    double linesPerSecond = 100000 / secondsElapsed;
                    System.out.println(String.format("[Worker] Progreso: %,d líneas procesadas (%.0f líneas/seg)",
                            processedLines, linesPerSecond));
                    lastReportTime = now;
                }
                try {
                    Datagram d = parseDatagram(line);
                    if (d == null)
                        continue;
                    Arc currentArc = findMatchingArc(d.lineId, d.lat, d.lon);
                    if (currentArc == null)
                        continue;
                    LastArcInfo last = lastArcByBus.get(d.busId);
                    String currentArcKey = currentArc.lineId + "_" + currentArc.orientation + "_" + currentArc.sequenceNum;
                    if (last != null && last.lineId == d.lineId && last.arcKey.equals(currentArcKey)) {
                        last.lastDatagram = d;
                        continue;
                    }
                    if (last != null && last.lineId == d.lineId && !last.arcKey.equals(currentArcKey)) {
                        String[] lastParts = last.arcKey.split("_");
                        int lastSeq = Integer.parseInt(lastParts[2]);
                        int currSeq = currentArc.sequenceNum;
                        if (Math.abs(currSeq - lastSeq) == 1) {
                            long timeMs = d.timestamp - last.lastDatagram.timestamp;
                            if (timeMs > 0) {
                                Arc prevArc = findArcByKey(currentArc.lineId, currentArc.orientation, lastSeq);
                                if (prevArc != null) {
                                    double velocidad = prevArc.distance / (timeMs / 3600000.0); // km/h
                                    if (velocidad <= 120.0) {
                                        String key = prevArc.lineId + "_" + prevArc.orientation + "_" + prevArc.sequenceNum;
                                        ArcStat stat = statsMap.computeIfAbsent(key, k -> {
                                            ArcStat s = new ArcStat();
                                            s.lineId = prevArc.lineId;
                                            s.orientation = prevArc.orientation;
                                            s.sequenceNum = prevArc.sequenceNum;
                                            s.sumDistance = 0;
                                            s.sumTime = 0;
                                            s.count = 0;
                                            return s;
                                        });
                                        stat.sumDistance += prevArc.distance;
                                        stat.sumTime += timeMs;
                                        stat.count++;
                                        // Print resumido solo para mediciones aceptadas
                                        System.out.println(String.format("Bus %d | Arco %d-%d | Dist: %.2f km | Tiempo: %.2f min | Vel: %.2f km/h", d.busId, prevArc.lineId, prevArc.sequenceNum, prevArc.distance, timeMs/60000.0, velocidad));
                                    } else {
                                        // Print solo para filtrados
                                        System.out.println(String.format("Bus %d | Arco %d-%d | Velocidad %.2f km/h DESCARTADA (>120 km/h)", d.busId, prevArc.lineId, prevArc.sequenceNum, velocidad));
                                    }
                                }
                            }
                        }
                    }
                    lastArcByBus.put(d.busId, new LastArcInfo(d.lineId, currentArcKey, d));
                } catch (Exception e) {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    System.out.println("[Worker] Análisis completado. Arcos procesados: " + statsMap.size());
        return statsMap.values().toArray(new ArcStat[0]);
    }

    // Buscar arco por lineId, orientation y sequenceNum
    private Arc findArcByKey(int lineId, int orientation, int sequenceNum) {
        List<Arc> arcs = graphBuilder.getArcsByLine(lineId, orientation);
        if (arcs == null) return null;
        for (Arc arc : arcs) {
            if (arc.sequenceNum == sequenceNum) return arc;
        }
        return null;
    }

    // Clase auxiliar para guardar el último arco y datagrama por bus
    private static class LastArcInfo {
        int lineId;
        String arcKey;
        Datagram lastDatagram;
        LastArcInfo(int lineId, String arcKey, Datagram lastDatagram) {
            this.lineId = lineId;
            this.arcKey = arcKey;
            this.lastDatagram = lastDatagram;
        }
    }

    private Arc findMatchingArc(int lineId, double lat, double lon) {
        List<Arc> candidates = new ArrayList<>();

        // Try both orientations since orientation is not in CSV
        List<Arc> arcs0 = graphBuilder.getArcsByLine(lineId, 0);
        if (arcs0 != null)
            candidates.addAll(arcs0);
        List<Arc> arcs1 = graphBuilder.getArcsByLine(lineId, 1);
        if (arcs1 != null)
            candidates.addAll(arcs1);

        if (candidates.isEmpty())
            return null;

        Arc bestArc = null;
        double minDist = Double.MAX_VALUE;

        for (Arc arc : candidates) {
            double d = calculateDistance(lat, lon, arc.fromStop.decimalLat, arc.fromStop.decimalLong);
            if (d < minDist) {
                minDist = d;
                bestArc = arc;
            }
        }

        // Threshold: 0.5 km
        if (minDist > 0.5)
            return null;

        return bestArc;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private Datagram parseDatagram(String line) {
        // Format:
        // 0,31-MAY-18,504016,6277,34483433,-765233667,497,131,9921,5445120768,2018-05-31
        // 00:00:21,837
        // Idx: 0 1 2 3 4 5 6 7 8 9 10 11
        // Col 9 es el timestamp en MS, Col 10 es la fecha formateada

        String[] parts = line.split(",");
        if (parts.length < 10)
            return null;

        Datagram d = new Datagram();
        try {
            d.busId = Integer.parseInt(parts[2]);
            d.lat = Double.parseDouble(parts[4]) / 10000000.0;
            d.lon = Double.parseDouble(parts[5]) / 10000000.0;
            d.lineId = Integer.parseInt(parts[7]);

            // Usar el timestamp en milisegundos (columna 9)
            d.timestamp = Long.parseLong(parts[9]);

        } catch (Exception e) {
            return null;
        }
        return d;
    }

    private static class Datagram {
        int busId;
        long timestamp;
        double lat;
        double lon;
        int lineId;
    }
}
