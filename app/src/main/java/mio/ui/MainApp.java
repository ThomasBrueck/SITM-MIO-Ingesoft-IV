package mio.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Aplicación principal JavaFX del sistema MIO
 */
public class MainApp extends Application {
    
    private MainController controller;
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Cargar FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        
        // Configurar escena
        Scene scene = new Scene(root, 1400, 800);
        
        // Configurar stage
        primaryStage.setTitle("Sistema de Grafos SITM-MIO - Universidad ICESI");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1200);
        primaryStage.setMinHeight(700);
        
        // Manejar cierre
        primaryStage.setOnCloseRequest(event -> {
            if (controller != null) {
                controller.shutdown();
            }
        });
        
        primaryStage.show();
        
        System.out.println("\n");
        System.out.println("INTERFAZ GRÁFICA INICIADA");
        System.out.println("Sistema de Grafos SITM-MIO");
    }
    
    @Override
    public void stop() {
        System.out.println("Cerrando aplicación...");
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
