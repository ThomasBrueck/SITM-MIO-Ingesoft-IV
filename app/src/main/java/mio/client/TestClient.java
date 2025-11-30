package mio.client;

import mioice.*;
import java.util.Arrays;

public class TestClient {
    public static void main(String[] args) {
        System.out.println("Iniciando TestClient...");
        MioGraphClient client = new MioGraphClient();
        try {
            client.initialize(args);
            
            if (!client.isConnected()) {
                System.err.println("No se pudo conectar al servidor.");
                System.exit(1);
            }
            
            System.out.println("Conectado. Obteniendo estadísticas...");
            int[] stats = client.getGraphStatistics();
            System.out.println("Estadísticas: " + Arrays.toString(stats));
            
            if (stats[1] > 0) {
                System.out.println("Obteniendo paradas...");
                Stop[] stops = client.getAllStops();
                if (stops.length >= 2) {
                    int origin = stops[0].stopId;
                    int dest = stops[10].stopId; // Pick some distance
                    System.out.println("Buscando ruta de " + origin + " a " + dest);
                    
                    RouteResult result = client.findRoute(origin, dest);
                    System.out.println("Ruta encontrada: " + result.found);
                    System.out.println("Distancia: " + result.totalDistance);
                    System.out.println("Paradas: " + result.stops.length);
                }
            }
            
            client.shutdown();
            System.out.println("Test completado exitosamente.");
            System.exit(0);
            
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
