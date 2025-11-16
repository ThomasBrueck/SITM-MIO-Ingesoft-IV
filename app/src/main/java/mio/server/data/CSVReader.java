package mio.server.data;

import mio.server.model.LineData;
import mio.server.model.StopData;
import mio.server.model.LineStopData;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector de archivos CSV del sistema MIO
 * Lee los archivos lines-241.csv, stops-241.csv y linestops-241.csv
 */
public class CSVReader {
    
    /**
     * Lee el archivo lines-241.csv y retorna una lista de rutas
     */
    public static List<LineData> readLines(String filePath) throws IOException {
        List<LineData> lines = new ArrayList<>();
        
        try (BufferedReader br = openFile(filePath)) {
            String line;
            boolean firstLine = true;
            
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Saltar encabezado
                }
                
                String[] values = parseCsvLine(line);
                if (values.length >= 5) {
                    try {
                        LineData lineData = new LineData(
                            Integer.parseInt(values[0].trim()),
                            Integer.parseInt(values[1].trim()),
                            values[2].trim(),
                            values[3].trim(),
                            values.length > 5 ? values[5].trim() : values[4].trim()
                        );
                        lines.add(lineData);
                    } catch (NumberFormatException e) {
                        System.err.println("Error parseando línea: " + line);
                    }
                }
            }
        }
        
        System.out.println("✓ Leídas " + lines.size() + " rutas");
        return lines;
    }
    
    /**
     * Lee el archivo stops-241.csv y retorna una lista de paradas
     */
    public static List<StopData> readStops(String filePath) throws IOException {
        List<StopData> stops = new ArrayList<>();
        
        try (BufferedReader br = openFile(filePath)) {
            String line;
            boolean firstLine = true;
            
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Saltar encabezado
                }
                
                String[] values = parseCsvLine(line);
                if (values.length >= 8) {
                    try {
                        StopData stopData = new StopData(
                            Integer.parseInt(values[0].trim()),
                            Integer.parseInt(values[1].trim()),
                            values[2].trim(),
                            values[3].trim(),
                            Long.parseLong(values[4].trim()),
                            Long.parseLong(values[5].trim()),
                            Double.parseDouble(values[6].trim()),
                            Double.parseDouble(values[7].trim())
                        );
                        stops.add(stopData);
                    } catch (NumberFormatException e) {
                        System.err.println("Error parseando parada: " + line);
                    }
                }
            }
        }
        
        System.out.println("✓ Leídas " + stops.size() + " paradas");
        return stops;
    }
    
    /**
     * Lee el archivo linestops-241.csv y retorna una lista de relaciones línea-parada
     */
    public static List<LineStopData> readLineStops(String filePath) throws IOException {
        List<LineStopData> lineStops = new ArrayList<>();
        
        try (BufferedReader br = openFile(filePath)) {
            String line;
            boolean firstLine = true;
            
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Saltar encabezado
                }
                
                String[] values = parseCsvLine(line);
                if (values.length >= 8) {
                    try {
                        LineStopData lineStopData = new LineStopData(
                            Integer.parseInt(values[0].trim()),
                            Integer.parseInt(values[1].trim()),
                            Integer.parseInt(values[2].trim()),
                            Integer.parseInt(values[3].trim()),
                            Integer.parseInt(values[4].trim()),
                            Integer.parseInt(values[5].trim()),
                            Integer.parseInt(values[6].trim()),
                            values.length > 8 ? Integer.parseInt(values[8].trim()) : Integer.parseInt(values[7].trim())
                        );
                        lineStops.add(lineStopData);
                    } catch (NumberFormatException e) {
                        System.err.println("Error parseando linestop: " + line);
                    }
                }
            }
        }
        
        System.out.println("✓ Leídas " + lineStops.size() + " relaciones línea-parada");
        return lineStops;
    }
    
    /**
     * Abre un archivo desde el sistema de archivos o desde recursos
     */
    private static BufferedReader openFile(String filePath) throws IOException {
        // Intentar abrir como recurso primero
        InputStream is = CSVReader.class.getClassLoader().getResourceAsStream(filePath);
        if (is != null) {
            return new BufferedReader(new InputStreamReader(is));
        }
        
        return new BufferedReader(new FileReader(filePath));
    }
    
    /**
     * Parsea una línea CSV teniendo en cuenta comillas
     */
    private static String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        
        result.add(current.toString());
        return result.toArray(new String[0]);
    }
}
