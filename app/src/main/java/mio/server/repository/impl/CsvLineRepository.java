package mio.server.repository.impl;

import mio.server.data.CSVReader;
import mio.server.model.LineData;
import mio.server.repository.LineRepository;
import mioice.Line;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CsvLineRepository implements LineRepository {

    private final String filePath;
    private List<Line> cache;

    public CsvLineRepository(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public List<Line> findAll() {
        if (cache != null) return cache;

        try {
            List<LineData> dataList = CSVReader.readLines(filePath);
            cache = new ArrayList<>();
            for (LineData ld : dataList) {
                Line line = new Line();
                line.lineId = ld.getLineId();
                line.planVersionId = ld.getPlanVersionId();
                line.shortName = ld.getShortName();
                line.description = ld.getDescription();
                line.activationDate = ld.getActivationDate();
                cache.add(line);
            }
            return cache;
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo repositorio de rutas CSV: " + filePath, e);
        }
    }

    @Override
    public Optional<Line> findById(Integer id) {
        return findAll().stream()
                .filter(l -> l.lineId == id)
                .findFirst();
    }
}
