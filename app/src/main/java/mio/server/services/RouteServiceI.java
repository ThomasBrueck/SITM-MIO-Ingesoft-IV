package mio.server.services;

import Mio.*;
import mio.server.data.GraphBuilder;
import com.zeroc.Ice.Current;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementación del servicio RouteService
 * Proporciona métodos para consultar información de rutas y paradas
 */
public class RouteServiceI implements RouteService {
    
    private GraphBuilder graphBuilder;
    
    public RouteServiceI(GraphBuilder graphBuilder) {
        this.graphBuilder = graphBuilder;
    }
    
    @Override
    public Line[] getAllLines(Current current) {
        List<Line> lines = new ArrayList<>(graphBuilder.getLinesMap().values());
        lines.sort((a, b) -> Integer.compare(a.lineId, b.lineId));
        return lines.toArray(new Line[0]);
    }
    
    @Override
    public Line getLineById(int lineId, Current current) throws LineNotFoundException {
        Line line = graphBuilder.getLinesMap().get(lineId);
        if (line == null) {
            LineNotFoundException ex = new LineNotFoundException();
            ex.lineId = lineId;
            ex.message = "Ruta no encontrada con ID: " + lineId;
            throw ex;
        }
        return line;
    }
    
    @Override
    public Stop[] getStopsByLine(int lineId, int orientation, Current current) 
            throws LineNotFoundException, InvalidOrientationException {
        
        // Validar que la línea existe
        if (!graphBuilder.getLinesMap().containsKey(lineId)) {
            LineNotFoundException ex = new LineNotFoundException();
            ex.lineId = lineId;
            ex.message = "Ruta no encontrada con ID: " + lineId;
            throw ex;
        }
        
        // Validar orientación
        if (orientation != 0 && orientation != 1) {
            InvalidOrientationException ex = new InvalidOrientationException();
            ex.orientation = orientation;
            ex.message = "Orientación inválida: " + orientation + ". Debe ser 0 (ida) o 1 (regreso)";
            throw ex;
        }
        
        List<Stop> stops = graphBuilder.getStopsByLine(lineId, orientation);
        return stops.toArray(new Stop[0]);
    }
    
    @Override
    public Arc[] getArcsByLine(int lineId, int orientation, Current current) 
            throws LineNotFoundException, InvalidOrientationException {
        
        // Validar que la línea existe
        if (!graphBuilder.getLinesMap().containsKey(lineId)) {
            LineNotFoundException ex = new LineNotFoundException();
            ex.lineId = lineId;
            ex.message = "Ruta no encontrada con ID: " + lineId;
            throw ex;
        }
        
        // Validar orientación
        if (orientation != 0 && orientation != 1) {
            InvalidOrientationException ex = new InvalidOrientationException();
            ex.orientation = orientation;
            ex.message = "Orientación inválida: " + orientation + ". Debe ser 0 (ida) o 1 (regreso)";
            throw ex;
        }
        
        List<Arc> arcs = graphBuilder.getArcsByLine(lineId, orientation);
        return arcs.toArray(new Arc[0]);
    }
    
    @Override
    public Stop getStopById(int stopId, Current current) throws StopNotFoundException {
        Stop stop = graphBuilder.getStopsMap().get(stopId);
        if (stop == null) {
            StopNotFoundException ex = new StopNotFoundException();
            ex.stopId = stopId;
            ex.message = "Parada no encontrada con ID: " + stopId;
            throw ex;
        }
        return stop;
    }
}
