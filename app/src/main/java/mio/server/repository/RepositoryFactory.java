package mio.server.repository;

import mio.server.repository.impl.CsvLineRepository;
import mio.server.repository.impl.CsvLineStopRepository;
import mio.server.repository.impl.CsvStopRepository;

/**
 * Factory para crear instancias de repositorios.
 * Permite cambiar fácilmente entre implementaciones (CSV, JDBC, etc.)
 */
public class RepositoryFactory {

    public static StopRepository createStopRepository(String type, String source) {
        if ("CSV".equalsIgnoreCase(type)) {
            return new CsvStopRepository(source);
        }
        throw new IllegalArgumentException("Tipo de repositorio no soportado: " + type);
    }

    public static LineRepository createLineRepository(String type, String source) {
        if ("CSV".equalsIgnoreCase(type)) {
            return new CsvLineRepository(source);
        }
        throw new IllegalArgumentException("Tipo de repositorio no soportado: " + type);
    }

    public static LineStopRepository createLineStopRepository(String type, String source) {
        if ("CSV".equalsIgnoreCase(type)) {
            return new CsvLineStopRepository(source);
        }
        throw new IllegalArgumentException("Tipo de repositorio no soportado: " + type);
    }
}
