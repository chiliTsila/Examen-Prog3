package com.jdbctd2.repository.interf;

import com.jdbctd2.model.Table;
import java.time.LocalDateTime;
import java.util.List;

public interface TableRepository {
    Table findTableById(Integer id);
    List<Table> findAllTables();
    Table saveTable(Table table);
    List<Table> findAvailableTablesAtDateTime(LocalDateTime dateTime);
    boolean isTableAvailableAtDateTime(Integer tableId, LocalDateTime dateTime);
}
