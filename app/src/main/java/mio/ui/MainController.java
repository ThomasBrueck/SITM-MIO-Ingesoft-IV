package mio.ui;

import mioice.*;
import mio.client.MioGraphClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.util.*;

/**
 * Controlador principal de la interfaz JavaFX - Búsqueda de rutas
 */
public class MainController {
    
    @FXML private ComboBox<String> originComboBox;
    @FXML private ComboBox<String> destComboBox;
    @FXML private Button findRouteButton;
    @FXML private Button clearButton;
    @FXML private Button statsButton;
    @FXML private Label routeInfoLabel;
    @FXML private Label stopsCountLabel;
    @FXML private Label distanceLabel;
    @FXML private Label transfersLabel;
    @FXML private Label statusLabel;
    @FXML private Label connectionLabel;
    @FXML private ListView<String> arcsListView;
    @FXML private WebView mapWebView;
    
    private MioGraphClient client;
    private WebEngine webEngine;
    private Stop[] allStops;
    private Map<String, Integer> stopNameToId = new HashMap<>();
    private int selectedOriginId = -1;
    private int selectedDestId = -1;
    
    /**
     * Inicializa el controlador
     */
    @FXML
    public void initialize() {
        // Inicializar WebView
        webEngine = mapWebView.getEngine();
        webEngine.setJavaScriptEnabled(true);
        
        // Cargar el mapa HTML
        String mapUrl = getClass().getResource("/web/map.html").toExternalForm();
        webEngine.load(mapUrl);
        
        // Esperar a que el mapa cargue
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                updateStatus("Mapa cargado correctamente");
            }
        });
        
        // Inicializar cliente ICE
        initializeClient();
    }
    
    /**
     * Inicializa el cliente ICE y carga las paradas
     */
    private void initializeClient() {
        new Thread(() -> {
            try {
                client = new MioGraphClient();
                client.initialize(new String[0]);
                
                // Cargar todas las paradas del sistema
                allStops = client.getAllStops();
                
                Platform.runLater(() -> {
                    loadStops();
                    connectionLabel.setText("● Conectado al servidor");
                    updateStatus("Conectado - " + allStops.length + " paradas disponibles");
                    System.out.println("✓ Cliente conectado al servidor ICE");
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    connectionLabel.setText("● Desconectado");
                    connectionLabel.setStyle("-fx-text-fill: #F44336;");
                    updateStatus("Error: No se pudo conectar al servidor");
                    showError("Error de Conexión", 
                             "No se pudo conectar al servidor ICE. Asegúrese de que esté ejecutándose.");
                });
                e.printStackTrace();
            }
        }).start();
    }
    
    /**
     * Carga las paradas en los ComboBox
     */
    private void loadStops() {
        ObservableList<String> stopNames = FXCollections.observableArrayList();
        
        // Ordenar paradas alfabéticamente por nombre completo
        Arrays.sort(allStops, Comparator.comparing(s -> s.longName));
        
        for (Stop stop : allStops) {
            // Usar solo el nombre completo sin ID ni código corto
            String displayName = stop.longName;
            stopNames.add(displayName);
            stopNameToId.put(displayName, stop.stopId);
        }
        
        originComboBox.setItems(stopNames);
        destComboBox.setItems(FXCollections.observableArrayList());
        destComboBox.setDisable(true); // Deshabilitado hasta seleccionar origen
    }
    
    /**
     * Maneja la selección de origen
     */
    @FXML
    private void onOriginSelected() {
        String selection = originComboBox.getSelectionModel().getSelectedItem();
        if (selection != null) {
            selectedOriginId = stopNameToId.get(selection);
            
            // Limpiar destino si ya estaba seleccionado
            destComboBox.getSelectionModel().clearSelection();
            selectedDestId = -1;
            findRouteButton.setDisable(true);
            
            // Filtrar destinos alcanzables
            updateStatus("Calculando paradas alcanzables...");
            new Thread(() -> {
                try {
                    int[] reachableIds = client.getReachableStops(selectedOriginId);
                    Set<Integer> reachableSet = new HashSet<>();
                    for (int id : reachableIds) {
                        reachableSet.add(id);
                    }
                    
                    Platform.runLater(() -> {
                        filterDestinationStops(reachableSet);
                        updateStatus(String.format("Origen seleccionado - %d destinos disponibles", reachableSet.size()));
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        showError("Error", "Error al calcular paradas alcanzables: " + e.getMessage());
                        updateStatus("Error calculando alcanzabilidad");
                    });
                    e.printStackTrace();
                }
            }).start();
        }
    }
    
    /**
     * Filtra el ComboBox de destino para mostrar solo paradas alcanzables
     */
    private void filterDestinationStops(Set<Integer> reachableStopIds) {
        ObservableList<String> reachableStopNames = FXCollections.observableArrayList();
        
        for (Stop stop : allStops) {
            if (reachableStopIds.contains(stop.stopId) && stop.stopId != selectedOriginId) {
                // Usar solo el nombre completo sin ID ni código corto
                String displayName = stop.longName;
                reachableStopNames.add(displayName);
            }
        }
        
        destComboBox.setItems(reachableStopNames);
        destComboBox.setDisable(false);
    }
    
    /**
     * Maneja la selección de destino
     */
    @FXML
    private void onDestSelected() {
        String selection = destComboBox.getSelectionModel().getSelectedItem();
        if (selection != null) {
            selectedDestId = stopNameToId.get(selection);
            checkIfReadyToSearch();
        }
    }
    
    /**
     * Verifica si se puede habilitar el botón de búsqueda
     */
    private void checkIfReadyToSearch() {
        boolean ready = selectedOriginId > 0 && selectedDestId > 0;
        findRouteButton.setDisable(!ready);
        
        if (ready && selectedOriginId == selectedDestId) {
            updateStatus("Advertencia: Origen y destino son la misma parada");
        }
    }
    
    /**
     * Busca la ruta entre origen y destino
     */
    @FXML
    private void onFindRoute() {
        if (selectedOriginId < 0 || selectedDestId < 0) {
            showWarning("Selección Incompleta", "Debe seleccionar tanto origen como destino");
            return;
        }
        
        updateStatus("Buscando ruta...");
        findRouteButton.setDisable(true);
        
        new Thread(() -> {
            try {
                // Llamar al servicio de búsqueda de ruta
                RouteResult result = client.findRoute(selectedOriginId, selectedDestId);
                
                Platform.runLater(() -> {
                    if (result.found) {
                        displayRoute(result);
                        updateStatus(result.message);
                    } else {
                        showWarning("Ruta No Encontrada", result.message);
                        updateStatus("No se encontró ruta");
                    }
                    findRouteButton.setDisable(false);
                });
                
            } catch (StopNotFoundException e) {
                Platform.runLater(() -> {
                    showError("Error", e.message);
                    updateStatus("Error al buscar ruta");
                    findRouteButton.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Error", "Error al comunicarse con el servidor: " + e.getMessage());
                    updateStatus("Error de comunicación");
                    findRouteButton.setDisable(false);
                });
                e.printStackTrace();
            }
        }).start();
    }
    
    /**
     * Muestra la ruta encontrada
     */
    private void displayRoute(RouteResult result) {
        // Actualizar información de la ruta
        String originName = getStopName(result.stops[0].stopId);
        String destName = getStopName(result.stops[result.stops.length - 1].stopId);
        routeInfoLabel.setText(originName + " → " + destName);
        stopsCountLabel.setText(String.valueOf(result.stops.length));
        distanceLabel.setText(String.format("%.2f km", result.totalDistance));
        transfersLabel.setText(String.valueOf(result.numTransfers));
        
        // Actualizar lista de arcos con nombres completos
        ObservableList<String> arcsList = FXCollections.observableArrayList();
        for (int i = 0; i < result.arcs.length; i++) {
            Arc arc = result.arcs[i];
            String arcText = String.format("[%d] %s → %s (%.3f km) - Ruta %s", 
                                          i + 1,
                                          arc.fromStop.longName,
                                          arc.toStop.longName,
                                          arc.distance,
                                          arc.lineName);
            arcsList.add(arcText);
        }
        arcsListView.setItems(arcsList);
        
        // Dibujar en el mapa
        drawOnMap(result.stops, result.arcs);
    }
    
    /**
     * Obtiene el nombre completo de una parada por ID
     */
    private String getStopName(int stopId) {
        for (Stop stop : allStops) {
            if (stop.stopId == stopId) {
                return stop.longName;
            }
        }
        return "Parada #" + stopId;
    }
    
    /**
     * Dibuja la ruta en el mapa usando JavaScript
     */
    private void drawOnMap(Stop[] stops, Arc[] arcs) {
        if (webEngine == null) return;
        
        try {
            // Construir JSON de paradas
            StringBuilder stopsJS = new StringBuilder("[");
            for (int i = 0; i < stops.length; i++) {
                if (i > 0) stopsJS.append(",");
                stopsJS.append(String.format(
                    "{stopId:%d, lat:%.6f, lng:%.6f, shortName:'%s', longName:'%s'}",
                    stops[i].stopId,
                    stops[i].decimalLat,
                    stops[i].decimalLong,
                    escapeJS(stops[i].shortName),
                    escapeJS(stops[i].longName)
                ));
            }
            stopsJS.append("]");
            
            // Construir JSON de arcos
            StringBuilder arcsJS = new StringBuilder("[");
            for (int i = 0; i < arcs.length; i++) {
                if (i > 0) arcsJS.append(",");
                arcsJS.append(String.format(
                    "{fromLat:%.6f, fromLng:%.6f, toLat:%.6f, toLng:%.6f, distance:%.3f, sequence:%d, lineName:'%s'}",
                    arcs[i].fromStop.decimalLat,
                    arcs[i].fromStop.decimalLong,
                    arcs[i].toStop.decimalLat,
                    arcs[i].toStop.decimalLong,
                    arcs[i].distance,
                    i + 1,
                    escapeJS(arcs[i].lineName)
                ));
            }
            arcsJS.append("]");
            
            // Ejecutar JavaScript
            String js = String.format("drawRoute({stops:%s, arcs:%s});", stopsJS, arcsJS);
            webEngine.executeScript(js);
            
        } catch (Exception e) {
            System.err.println("Error al dibujar en el mapa: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Escapa caracteres especiales para JavaScript
     */
    private String escapeJS(String str) {
        if (str == null) return "";
        return str.replace("'", "\\'")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r");
    }
    
    /**
     * Limpia el mapa y los datos
     */
    @FXML
    private void onClear() {
        routeInfoLabel.setText("No seleccionado");
        stopsCountLabel.setText("0");
        distanceLabel.setText("0.00 km");
        transfersLabel.setText("0");
        arcsListView.getItems().clear();
        
        originComboBox.getSelectionModel().clearSelection();
        destComboBox.getSelectionModel().clearSelection();
        destComboBox.setItems(FXCollections.observableArrayList());
        destComboBox.setDisable(true);
        selectedOriginId = -1;
        selectedDestId = -1;
        findRouteButton.setDisable(true);
        
        // Limpiar mapa
        if (webEngine != null) {
            try {
                webEngine.executeScript("clearMap();");
            } catch (Exception e) {
                System.err.println("Error al limpiar mapa: " + e.getMessage());
            }
        }
        
        updateStatus("Listo para nueva búsqueda");
    }
    
    /**
     * Muestra las estadísticas del grafo
     */
    @FXML
    private void onShowStats() {
        new Thread(() -> {
            try {
                int[] stats = client.getGraphStatistics();
                
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Estadísticas del Grafo");
                    alert.setHeaderText("Sistema SITM-MIO");
                    alert.setContentText(String.format(
                        "Total de Rutas: %d\n" +
                        "Total de Paradas: %d\n" +
                        "Total de Arcos: %d\n" +
                        "Arcos Orientación 0 (Ida): %d\n" +
                        "Arcos Orientación 1 (Regreso): %d",
                        stats[0], stats[1], stats[2], stats[3], stats[4]
                    ));
                    alert.showAndWait();
                });
                
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Error", "No se pudieron obtener las estadísticas");
                });
            }
        }).start();
    }
    
    /**
     * Actualiza el label de estado
     */
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
    
    /**
     * Muestra un diálogo de error
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Muestra un diálogo de advertencia
     */
    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Cierra la conexión del cliente al salir
     */
    public void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }
}
