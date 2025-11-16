package mio.server.model;

/**
 * Modelo de datos para una parada del sistema MIO
 * Corresponde a una fila del archivo stops.csv
 */
public class StopData {
    private int stopId;
    private int planVersionId;
    private String shortName;
    private String longName;
    private long gpsX;
    private long gpsY;
    private double decimalLong;
    private double decimalLat;
    
    public StopData() {}
    
    public StopData(int stopId, int planVersionId, String shortName, String longName,
                    long gpsX, long gpsY, double decimalLong, double decimalLat) {
        this.stopId = stopId;
        this.planVersionId = planVersionId;
        this.shortName = shortName;
        this.longName = longName;
        this.gpsX = gpsX;
        this.gpsY = gpsY;
        this.decimalLong = decimalLong;
        this.decimalLat = decimalLat;
    }
    
    // Getters y Setters
    public int getStopId() { return stopId; }
    public void setStopId(int stopId) { this.stopId = stopId; }
    
    public int getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(int planVersionId) { this.planVersionId = planVersionId; }
    
    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }
    
    public String getLongName() { return longName; }
    public void setLongName(String longName) { this.longName = longName; }
    
    public long getGpsX() { return gpsX; }
    public void setGpsX(long gpsX) { this.gpsX = gpsX; }
    
    public long getGpsY() { return gpsY; }
    public void setGpsY(long gpsY) { this.gpsY = gpsY; }
    
    public double getDecimalLong() { return decimalLong; }
    public void setDecimalLong(double decimalLong) { this.decimalLong = decimalLong; }
    
    public double getDecimalLat() { return decimalLat; }
    public void setDecimalLat(double decimalLat) { this.decimalLat = decimalLat; }
    
    @Override
    public String toString() {
        return String.format("Stop[%d, %s, %s, (%.6f, %.6f)]",
                stopId, shortName, longName, decimalLat, decimalLong);
    }
}
