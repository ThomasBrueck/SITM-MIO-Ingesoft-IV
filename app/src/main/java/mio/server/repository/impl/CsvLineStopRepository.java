package mio.server.repository.impl;

import mio.server.data.CSVReader;
import mio.server.model.LineStopData;
import mio.server.repository.LineStopRepository;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class CsvLineStopRepository implements LineStopRepository {

    private final String filePath;
    private List<LineStopData> cache;

    public CsvLineStopRepository(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public List<LineStopData> findAll() {
        if (cache != null) return cache;

        try {
            cache = CSVReader.readLineStops(filePath);
            return cache;
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo repositorio de line-stops CSV: " + filePath, e);
        }
    }

    @Override
    public Optional<LineStopData> findById(String id) {
        throw new UnsupportedOperationException("FindById no soportado para LineStop");
    }
}
