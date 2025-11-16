package mio.server.model;

/**
 * Modelo de datos para la relación entre líneas y paradas
 * Corresponde a una fila del archivo linestops.csv
 */
public class LineStopData {
    private int lineStopId;
    private int stopSequence;
    private int orientation;  // 0 = ida, 1 = regreso
    private int lineId;
    private int stopId;
    private int planVersionId;
    private int lineVariant;
    private int lineVariantType;
    
    public LineStopData() {}
    
    public LineStopData(int lineStopId, int stopSequence, int orientation,
                        int lineId, int stopId, int planVersionId,
                        int lineVariant, int lineVariantType) {
        this.lineStopId = lineStopId;
        this.stopSequence = stopSequence;
        this.orientation = orientation;
        this.lineId = lineId;
        this.stopId = stopId;
        this.planVersionId = planVersionId;
        this.lineVariant = lineVariant;
        this.lineVariantType = lineVariantType;
    }
    
    // Getters y Setters
    public int getLineStopId() { return lineStopId; }
    public void setLineStopId(int lineStopId) { this.lineStopId = lineStopId; }
    
    public int getStopSequence() { return stopSequence; }
    public void setStopSequence(int stopSequence) { this.stopSequence = stopSequence; }
    
    public int getOrientation() { return orientation; }
    public void setOrientation(int orientation) { this.orientation = orientation; }
    
    public int getLineId() { return lineId; }
    public void setLineId(int lineId) { this.lineId = lineId; }
    
    public int getStopId() { return stopId; }
    public void setStopId(int stopId) { this.stopId = stopId; }
    
    public int getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(int planVersionId) { this.planVersionId = planVersionId; }
    
    public int getLineVariant() { return lineVariant; }
    public void setLineVariant(int lineVariant) { this.lineVariant = lineVariant; }
    
    public int getLineVariantType() { return lineVariantType; }
    public void setLineVariantType(int lineVariantType) { this.lineVariantType = lineVariantType; }
    
    @Override
    public String toString() {
        return String.format("LineStop[Line=%d, Stop=%d, Seq=%d, Orient=%d]",
                lineId, stopId, stopSequence, orientation);
    }
}
