package mio.server.repository.impl;

import mio.server.data.CSVReader;
import mio.server.model.StopData;
import mio.server.repository.StopRepository;
import mioice.Stop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CsvStopRepository implements StopRepository {

    private final String filePath;
    private List<Stop> cache;

    public CsvStopRepository(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public List<Stop> findAll() {
        if (cache != null) return cache;

        try {
            List<StopData> dataList = CSVReader.readStops(filePath);
            cache = new ArrayList<>();
            for (StopData sd : dataList) {
                Stop stop = new Stop();
                stop.stopId = sd.getStopId();
                stop.planVersionId = sd.getPlanVersionId();
                stop.shortName = sd.getShortName();
                stop.longName = sd.getLongName();
                stop.gpsX = sd.getGpsX();
                stop.gpsY = sd.getGpsY();
                stop.decimalLong = sd.getDecimalLong();
                stop.decimalLat = sd.getDecimalLat();
                cache.add(stop);
            }
            return cache;
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo repositorio de paradas CSV: " + filePath, e);
        }
    }

    @Override
    public Optional<Stop> findById(Integer id) {
        return findAll().stream()
                .filter(s -> s.stopId == id)
                .findFirst();
    }
}
