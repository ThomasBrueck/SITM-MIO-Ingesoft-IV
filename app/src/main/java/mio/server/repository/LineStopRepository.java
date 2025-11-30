package mio.server.repository;

import mio.server.model.LineStopData;

/**
 * Repositorio para datos de relación Línea-Parada (LineStop)
 * Nota: Retorna el modelo de datos interno porque los Arcos se construyen dinámicamente
 */
public interface LineStopRepository extends IRepository<LineStopData, String> {
    // ID es String compuesto o no aplica findById simple
}
