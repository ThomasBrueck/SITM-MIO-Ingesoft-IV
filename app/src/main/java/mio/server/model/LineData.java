package mio.server.model;

/**
 * Modelo de datos para una ruta del sistema MIO
 * Corresponde a una fila del archivo lines.csv
 */
public class LineData {
    private int lineId;
    private int planVersionId;
    private String shortName;
    private String description;
    private String activationDate;
    
    public LineData() {}
    
    public LineData(int lineId, int planVersionId, String shortName, 
                    String description, String activationDate) {
        this.lineId = lineId;
        this.planVersionId = planVersionId;
        this.shortName = shortName;
        this.description = description;
        this.activationDate = activationDate;
    }
    
    // Getters y Setters
    public int getLineId() { return lineId; }
    public void setLineId(int lineId) { this.lineId = lineId; }
    
    public int getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(int planVersionId) { this.planVersionId = planVersionId; }
    
    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getActivationDate() { return activationDate; }
    public void setActivationDate(String activationDate) { this.activationDate = activationDate; }
    
    @Override
    public String toString() {
        return String.format("Line[%d, %s, %s]", lineId, shortName, description);
    }
}
