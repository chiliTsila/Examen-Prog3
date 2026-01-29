package com.jdbctd2.repository;

import com.jdbctd2.model.Table;
import com.jdbctd2.repository.interf.TableRepository;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DataRetriever implements TableRepository {
    // Placeholder for DBConnection - implement based on your setup
    private DBConnection dbConnection;

    public DataRetriever() {
        this.dbConnection = new DBConnection();
    }

    /**
     * Validates if a table is available at the specified date/time for an order
     * If not available, throws RuntimeException with helpful message listing available tables
     */
    private void validateTableForOrder(Order orderToSave) {
        if (orderToSave.getTableId() == null) {
            throw new IllegalArgumentException("Table ID cannot be null");
        }

        if (orderToSave.getInstallationDatetime() == null) {
            throw new IllegalArgumentException("Installation datetime cannot be null");
        }

        LocalDateTime installationTime = orderToSave.getInstallationDatetime();

        // Check if the requested table is available
        if (!isTableAvailableAtDateTime(orderToSave.getTableId(), installationTime)) {
            // Table is not available, find available tables
            List<Table> availableTables = findAvailableTablesAtDateTime(installationTime);

            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("Table ").append(orderToSave.getTableId())
                    .append(" is not available at this time.");

            if (availableTables.isEmpty()) {
                errorMessage.append(" No tables are currently available.");
            } else {
                errorMessage.append(" Available tables: ");
                for (int i = 0; i < availableTables.size(); i++) {
                    if (i > 0) {
                        errorMessage.append(", ");
                    }
                    errorMessage.append(availableTables.get(i).getNumber());
                }
                errorMessage.append(".");
            }

            throw new RuntimeException(errorMessage.toString());
        }
    }

    /**
     * Save order with table validation
     * Validates that the specified table is available at the installation time
     */
    public Order saveOrder(Order orderToSave) {
        if (orderToSave == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        // Validate table availability before saving
        validateTableForOrder(orderToSave);

        // Continue with order saving logic
        // This is where you would insert into database
        // For now, this is a placeholder
        throw new UnsupportedOperationException("Order saving not yet implemented");
    }

    // ============= TableRepository Implementation =============

    @Override
    public Table findTableById(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }

        String tableSql = "select id, number, capacity from restaurant_table where id = ?";

        Connection con = null;
        PreparedStatement tableStmt = null;
        ResultSet tableRs = null;

        try {
            con = dbConnection.getDBConnection();
            tableStmt = con.prepareStatement(tableSql);
            tableStmt.setInt(1, id);
            tableRs = tableStmt.executeQuery();

            if (!tableRs.next()) {
                throw new RuntimeException("Table with id " + id + " not found");
            }

            return mapTableFromResultSet(tableRs);
        } catch (SQLException e) {
            throw new RuntimeException("Error while trying to fetch table ", e);
        } finally {
            dbConnection.attemptCloseDBConnection(tableRs, tableStmt, con);
        }
    }

    @Override
    public List<Table> findAllTables() {
        String tableSql = "select id, number, capacity from restaurant_table order by number";

        Connection con = null;
        PreparedStatement tableStmt = null;
        ResultSet tableRs = null;

        try {
            con = dbConnection.getDBConnection();
            tableStmt = con.prepareStatement(tableSql);
            tableRs = tableStmt.executeQuery();

            List<Table> tables = new ArrayList<>();
            while (tableRs.next()) {
                tables.add(mapTableFromResultSet(tableRs));
            }

            return tables;
        } catch (SQLException e) {
            throw new RuntimeException("Error while trying to fetch tables ", e);
        } finally {
            dbConnection.attemptCloseDBConnection(tableRs, tableStmt, con);
        }
    }

    @Override
    public Table saveTable(Table table) {
        if (table == null) {
            throw new IllegalArgumentException("Table cannot be null");
        }

        if (table.getNumber() == null || table.getNumber() <= 0) {
            throw new IllegalArgumentException("Table number must be positive");
        }

        if (table.getCapacity() == null || table.getCapacity() <= 0) {
            throw new IllegalArgumentException("Table capacity must be positive");
        }

        String saveTableSql = """
            insert into restaurant_table (id, number, capacity)
            values (?, ?, ?)
            on conflict (id) do update
            set number = excluded.number, capacity = excluded.capacity
            returning id
            """;

        Connection con = null;
        PreparedStatement saveTableStmt = null;
        ResultSet saveTableRs = null;

        try {
            con = dbConnection.getDBConnection();
            con.setAutoCommit(false);
            saveTableStmt = con.prepareStatement(saveTableSql);

            if (table.getId() == null) {
                saveTableStmt.setInt(1, getNextSerialValue(con, "restaurant_table", "id"));
            } else {
                saveTableStmt.setInt(1, table.getId());
            }
            saveTableStmt.setInt(2, table.getNumber());
            saveTableStmt.setInt(3, table.getCapacity());

            saveTableRs = saveTableStmt.executeQuery();
            saveTableRs.next();
            int tableId = saveTableRs.getInt(1);

            con.commit();

            return findTableById(tableId);
        } catch (SQLException e) {
            rollbackQuietly(con);
            throw new RuntimeException("Error while saving table ", e);
        } finally {
            restoreAutoCommit(con);
            dbConnection.attemptCloseDBConnection(saveTableRs, saveTableStmt, con);
        }
    }

    @Override
    public List<Table> findAvailableTablesAtDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("dateTime cannot be null");
        }

        String availableTablesSql = """
            select id, number, capacity from restaurant_table rt
            where rt.id not in (
                select distinct id_table from "order"
                where ? < departure_datetime and ? > installation_datetime
            )
            order by rt.number
            """;

        Connection con = null;
        PreparedStatement availableTablesStmt = null;
        ResultSet availableTablesRs = null;

        try {
            con = dbConnection.getDBConnection();
            availableTablesStmt = con.prepareStatement(availableTablesSql);
            availableTablesStmt.setObject(1, dateTime);
            availableTablesStmt.setObject(2, dateTime);
            availableTablesRs = availableTablesStmt.executeQuery();

            List<Table> tables = new ArrayList<>();
            while (availableTablesRs.next()) {
                tables.add(mapTableFromResultSet(availableTablesRs));
            }

            return tables;
        } catch (SQLException e) {
            throw new RuntimeException("Error while fetching available tables ", e);
        } finally {
            dbConnection.attemptCloseDBConnection(availableTablesRs, availableTablesStmt, con);
        }
    }

    @Override
    public boolean isTableAvailableAtDateTime(Integer tableId, LocalDateTime dateTime) {
        if (tableId == null) {
            throw new IllegalArgumentException("tableId cannot be null");
        }
        if (dateTime == null) {
            throw new IllegalArgumentException("dateTime cannot be null");
        }

        String checkAvailabilitySql = """
            select count(*) from "order"
            where id_table = ? and ? < departure_datetime and ? > installation_datetime
            """;

        Connection con = null;
        PreparedStatement checkStmt = null;
        ResultSet checkRs = null;

        try {
            con = dbConnection.getDBConnection();
            checkStmt = con.prepareStatement(checkAvailabilitySql);
            checkStmt.setInt(1, tableId);
            checkStmt.setObject(2, dateTime);
            checkStmt.setObject(3, dateTime);
            checkRs = checkStmt.executeQuery();

            checkRs.next();
            int count = checkRs.getInt(1);

            return count == 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error while checking table availability ", e);
        } finally {
            dbConnection.attemptCloseDBConnection(checkRs, checkStmt, con);
        }
    }

    // ============= Helper Methods =============

    private Table mapTableFromResultSet(ResultSet tableRs) throws SQLException {
        Table table = new Table();
        table.setId(tableRs.getInt("id"));
        table.setNumber(tableRs.getInt("number"));
        table.setCapacity(tableRs.getInt("capacity"));
        return table;
    }

    private int getNextSerialValue(Connection con, String tableName, String columnName) {
        String sequenceName = getSerialSequenceName(con, tableName, columnName);
        String getNextValueSql = String.format("select nextval('%s')", sequenceName);
        updateSequence(con, columnName, sequenceName, tableName);

        Statement getNextValueStmt = null;
        ResultSet getNextValueRs = null;

        try {
            getNextValueStmt = con.createStatement();
            getNextValueRs = getNextValueStmt.executeQuery(getNextValueSql);
            getNextValueRs.next();
            return getNextValueRs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get next value ", e);
        } finally {
            dbConnection.attemptCloseDBConnection(getNextValueRs, getNextValueStmt);
        }
    }

    private String getSerialSequenceName(Connection con, String tableName, String columnName) {
        String getSeqSql = String.format("SELECT pg_get_serial_sequence('%s', '%s')", tableName, columnName);
        Statement getSeqStmt = null;
        ResultSet getSeqRs = null;

        try {
            getSeqStmt = con.createStatement();
            getSeqRs = getSeqStmt.executeQuery(getSeqSql);
            getSeqRs.next();
            return getSeqRs.getString(1);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get sequence name", e);
        } finally {
            dbConnection.attemptCloseDBConnection(getSeqRs, getSeqStmt);
        }
    }

    private void updateSequence(Connection con, String columnName, String sequenceName, String tableName) {
        String updateSeqSql = String.format(
            "select setval('%s', (select coalesce(max(%s),0) from %s))",
            sequenceName, columnName, tableName
        );
        Statement updateSeqStmt = null;

        try {
            updateSeqStmt = con.createStatement();
            updateSeqStmt.executeQuery(updateSeqSql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update sequence ", e);
        } finally {
            dbConnection.attemptCloseDBConnection(updateSeqStmt);
        }
    }

    private void rollbackQuietly(Connection con) {
        try {
            if (con != null && !con.isClosed()) {
                con.rollback();
            }
        } catch (SQLException e) {
            System.err.println("Warning: Rollback failed: " + e.getMessage());
        }
    }

    private void restoreAutoCommit(Connection con) {
        try {
            if (con != null && !con.isClosed()) {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Warning: AutoCommit restore failed: " + e.getMessage());
        }
    }
}

// Placeholder classes - replace with your actual implementations
class Order {
    private Integer id;
    private String reference;
    private Integer tableId;
    private LocalDateTime installationDatetime;
    private LocalDateTime departureDatetime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public Integer getTableId() { return tableId; }
    public void setTableId(Integer tableId) { this.tableId = tableId; }

    public LocalDateTime getInstallationDatetime() { return installationDatetime; }
    public void setInstallationDatetime(LocalDateTime installationDatetime) {
        this.installationDatetime = installationDatetime;
    }

    public LocalDateTime getDepartureDatetime() { return departureDatetime; }
    public void setDepartureDatetime(LocalDateTime departureDatetime) {
        this.departureDatetime = departureDatetime;
    }
}

class DBConnection {
    public Connection getDBConnection() throws SQLException {
        // Implement your database connection logic here
        throw new UnsupportedOperationException("Implement database connection");
    }

    public void attemptCloseDBConnection(ResultSet rs, Statement stmt, Connection con) {
        if (rs != null) try { rs.close(); } catch (SQLException e) { /* ignore */ }
        if (stmt != null) try { stmt.close(); } catch (SQLException e) { /* ignore */ }
        if (con != null) try { con.close(); } catch (SQLException e) { /* ignore */ }
    }

    public void attemptCloseDBConnection(Statement stmt, Connection con) {
        attemptCloseDBConnection(null, stmt, con);
    }

    public void attemptCloseDBConnection(Statement stmt) {
        if (stmt != null) try { stmt.close(); } catch (SQLException e) { /* ignore */ }
    }
}
