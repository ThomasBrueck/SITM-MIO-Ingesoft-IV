package mio.server.repository;

import java.util.List;
import java.util.Optional;

/**
 * Interfaz genérica para repositorios (CRUD básico)
 * @param <T> Tipo de la entidad
 * @param <ID> Tipo del identificador
 */
public interface IRepository<T, ID> {
    List<T> findAll();
    Optional<T> findById(ID id);
    // save, delete, etc. se pueden agregar según necesidad
}
