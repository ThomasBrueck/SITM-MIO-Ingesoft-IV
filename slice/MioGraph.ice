// MioGraph.ice
// Definición Slice para el sistema de grafos SITM-MIO
// Universidad ICESI - Ingeniería de Software IV

module mioice {
    
    
    /**
     * Representa una parada del sistema MIO
     */
    struct Stop {
        int stopId;              // ID único de la parada
        int planVersionId;       // Versión del plan
        string shortName;        // Nombre corto (ej: K109C421)
        string longName;         // Nombre largo descriptivo
        long gpsX;              // Coordenada GPS X (formato entero)
        long gpsY;              // Coordenada GPS Y (formato entero)
        double decimalLong;     // Longitud en decimal
        double decimalLat;      // Latitud en decimal
    }
    
    /**
     * Representa una ruta del sistema MIO
     */
    struct Line {
        int lineId;             // ID único de la ruta
        int planVersionId;      // Versión del plan
        string shortName;       // Nombre corto (ej: T31, P10B)
        string description;     // Descripción completa de la ruta
        string activationDate;  // Fecha de activación
    }
    
    /**
     * Representa un arco entre dos paradas consecutivas
     */
    struct Arc {
        int lineId;             // ID de la ruta a la que pertenece
        string lineName;        // Nombre de la ruta
        int orientation;        // 0=ida, 1=regreso
        int sequenceNum;        // Número de secuencia del arco en la ruta
        Stop fromStop;          // Parada origen
        Stop toStop;            // Parada destino
        double distance;        // Distancia estimada (km)
        double avgSpeed;        // Velocidad promedio (km/h) - inicialmente 0
    }
    
    
    sequence<Stop> StopList;
    sequence<Line> LineList;
    sequence<Arc> ArcList;
    sequence<int> IntList;
    
    /**
     * Representa una ruta calculada entre dos paradas
     */
    struct RouteResult {
        bool found;             // Si se encontró una ruta
        StopList stops;         // Lista de paradas en orden
        ArcList arcs;           // Lista de arcos que conectan las paradas
        double totalDistance;   // Distancia total en km
        int numTransfers;       // Número de transbordos (cambios de línea)
        string message;         // Mensaje informativo (error o info)
    }
    
    
    dictionary<int, Stop> StopMap;
    dictionary<int, Line> LineMap;
    
    
    exception LineNotFoundException {
        int lineId;
        string message;
    }
    
    exception StopNotFoundException {
        int stopId;
        string message;
    }
    
    exception InvalidOrientationException {
        int orientation;
        string message;
    }
    
    
    /**
     * Servicio para consultar información de rutas
     */
    interface RouteService {
        /**
         * Obtiene todas las rutas disponibles
         */
        LineList getAllLines();
        
        /**
         * Obtiene una ruta específica por ID
         */
        Line getLineById(int lineId) throws LineNotFoundException;
        
        /**
         * Obtiene todas las paradas de una ruta específica
         * @param lineId ID de la ruta
         * @param orientation 0=ida, 1=regreso
         */
        StopList getStopsByLine(int lineId, int orientation) 
            throws LineNotFoundException, InvalidOrientationException;
        
        /**
         * Obtiene los arcos de una ruta específica
         * @param lineId ID de la ruta
         * @param orientation 0=ida, 1=regreso
         */
        ArcList getArcsByLine(int lineId, int orientation)
            throws LineNotFoundException, InvalidOrientationException;
        
        /**
         * Obtiene información de una parada específica
         */
        Stop getStopById(int stopId) throws StopNotFoundException;
    }
    
    /**
     * Servicio para consultar información del grafo completo
     */
    interface GraphService {
        /**
         * Obtiene todas las paradas del sistema
         */
        StopList getAllStops();
        
        /**
         * Obtiene todos los arcos del sistema
         */
        ArcList getAllArcs();
        
        /**
         * Obtiene todos los arcos agrupados por ruta y orientación
         * Retorna un mapa de arcos organizados
         */
        ArcList getArcsByRouteAndOrientation();
        
        /**
         * Obtiene estadísticas del grafo
         * Retorna: [numRoutes, numStops, numArcs, numArcsOrientation0, numArcsOrientation1]
         */
        IntList getGraphStatistics();
        
        /**
         * Imprime en consola del servidor el listado completo de arcos
         * organizados por ruta y orientación
         */
        void printArcsToConsole();
        
        /**
         * Encuentra la ruta más corta entre dos paradas
         * @param originStopId ID de la parada de origen
         * @param destStopId ID de la parada de destino
         * @return RouteResult con la ruta encontrada o información de error
         */
        RouteResult findRoute(int originStopId, int destStopId)
            throws StopNotFoundException;
        
        /**
         * Obtiene todas las paradas alcanzables desde una parada de origen
         * @param originStopId ID de la parada de origen
         * @return IntList con los IDs de las paradas alcanzables
         */
        IntList getReachableStops(int originStopId)
            throws StopNotFoundException;
    }
}
