package com.jdbctd2.repository;

import com.jdbctd2.config.DBConnection;
import com.jdbctd2.model.*;
import com.jdbctd2.repository.interf.TableRepository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DataRetriever implements TableRepository {
    private DBConnection dbConnection;

    public DataRetriever() {
        this.dbConnection = new DBConnection();
    }

    public DataRetriever(DBConnection dbConnection) {
        this.dbConnection = dbConnection;
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
                    if (i > 0) errorMessage.append(", ");
                    errorMessage.append(availableTables.get(i).getNumber())
                            .append(" (capacity: ").append(availableTables.get(i).getCapacity()).append(")");
                }
            }

            throw new RuntimeException(errorMessage.toString());
        }
    }

    public void saveOrder(Order orderToSave) {
        Connection con = null;
        try {
            con = dbConnection.getDBConnection();
            con.setAutoCommit(false);

            // Validate the order before saving
            validateTableForOrder(orderToSave);

            // Insert order into database
            String insertOrderSql = "INSERT INTO \"order\" (reference, creation_datetime, id_table, installation_datetime, departure_datetime) " +
                    "VALUES (?, ?, ?, ?, ?)";
            PreparedStatement insertStmt = con.prepareStatement(insertOrderSql);

            insertStmt.setString(1, orderToSave.getReference());
            insertStmt.setTimestamp(2, orderToSave.getCreationDatetime() != null ? 
                    Timestamp.from(orderToSave.getCreationDatetime()) : new Timestamp(System.currentTimeMillis()));
            insertStmt.setInt(3, orderToSave.getTableId());
            insertStmt.setTimestamp(4, orderToSave.getInstallationDatetime() != null ? 
                    Timestamp.valueOf(orderToSave.getInstallationDatetime()) : null);
            insertStmt.setTimestamp(5, orderToSave.getDepartureDatetime() != null ? 
                    Timestamp.valueOf(orderToSave.getDepartureDatetime()) : null);

            insertStmt.executeUpdate();
            dbConnection.attemptCloseDBConnection(insertStmt);

            con.commit();
        } catch (SQLException | RuntimeException e) {
            rollbackQuietly(con);
            throw new RuntimeException("Failed to save order: " + e.getMessage(), e);
        } finally {
            restoreAutoCommit(con);
            dbConnection.attemptCloseDBConnection(con);
        }
    }

    @Override
    public Table findTableById(Integer tableId) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT * FROM restaurant_table WHERE id = ?";
            stmt = con.prepareStatement(sql);
            stmt.setInt(1, tableId);
            rs = stmt.executeQuery();

            if (rs.next()) {
                return mapTableFromResultSet(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find table by ID: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
        }
    }

    @Override
    public List<Table> findAllTables() {
        Connection con = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT * FROM restaurant_table ORDER BY number";
            stmt = con.createStatement();
            rs = stmt.executeQuery(sql);

            List<Table> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(mapTableFromResultSet(rs));
            }
            return tables;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve all tables: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
        }
    }

    @Override
    public Table saveTable(Table tableToSave) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet generatedKeys = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "INSERT INTO restaurant_table (number, capacity) VALUES (?, ?)";
            stmt = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, tableToSave.getNumber());
            stmt.setInt(2, tableToSave.getCapacity());
            stmt.executeUpdate();

            generatedKeys = stmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                tableToSave.setId((int) generatedKeys.getLong(1));
            }

            return tableToSave;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save table: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(generatedKeys, stmt, con);
        }
    }

    @Override
    public List<Table> findAvailableTablesAtDateTime(LocalDateTime dateTime) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT * FROM restaurant_table WHERE id NOT IN " +
                    "(SELECT id_table FROM \"order\" " +
                    "WHERE id_table is not null and ? < departure_datetime and ? > installation_datetime) " +
                    "ORDER BY number";
            stmt = con.prepareStatement(sql);
            stmt.setTimestamp(1, Timestamp.valueOf(dateTime));
            stmt.setTimestamp(2, Timestamp.valueOf(dateTime));
            rs = stmt.executeQuery();

            List<Table> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(mapTableFromResultSet(rs));
            }
            return tables;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find available tables: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
        }
    }

    @Override
    public boolean isTableAvailableAtDateTime(Integer tableId, LocalDateTime dateTime) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT COUNT(*) as conflict_count FROM \"order\" " +
                    "WHERE id_table = ? and ? < departure_datetime and ? > installation_datetime";
            stmt = con.prepareStatement(sql);
            stmt.setInt(1, tableId);
            stmt.setTimestamp(2, Timestamp.valueOf(dateTime));
            stmt.setTimestamp(3, Timestamp.valueOf(dateTime));
            rs = stmt.executeQuery();

            if (rs.next()) {
                int conflictCount = rs.getInt("conflict_count");
                return conflictCount == 0;
            }
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check table availability: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
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

    // ============= Dish Methods =============

    public void saveDish(Dish dishToSave) {
        Connection con = null;
        PreparedStatement stmt = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "INSERT INTO dish (name, description, price, category, is_available) VALUES (?, ?, ?, ?, ?)";
            stmt = con.prepareStatement(sql);
            stmt.setString(1, dishToSave.getName());
            stmt.setString(2, dishToSave.getDescription());
            stmt.setBigDecimal(3, dishToSave.getPrice());
            stmt.setString(4, dishToSave.getCategory());
            stmt.setBoolean(5, dishToSave.getIsAvailable() != null ? dishToSave.getIsAvailable() : true);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save dish: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(stmt, con);
        }
    }

    public Dish findDishById(Integer dishId) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT * FROM dish WHERE id = ?";
            stmt = con.prepareStatement(sql);
            stmt.setInt(1, dishId);
            rs = stmt.executeQuery();

            if (rs.next()) {
                return mapDishFromResultSet(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find dish by ID: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
        }
    }

    public List<Dish> findAllDishes() {
        Connection con = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT * FROM dish ORDER BY name";
            stmt = con.createStatement();
            rs = stmt.executeQuery(sql);

            List<Dish> dishes = new ArrayList<>();
            while (rs.next()) {
                dishes.add(mapDishFromResultSet(rs));
            }
            return dishes;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve all dishes: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
        }
    }

    public List<Dish> findDishesByCategory(String category) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT * FROM dish WHERE category = ? AND is_available = true ORDER BY name";
            stmt = con.prepareStatement(sql);
            stmt.setString(1, category);
            rs = stmt.executeQuery();

            List<Dish> dishes = new ArrayList<>();
            while (rs.next()) {
                dishes.add(mapDishFromResultSet(rs));
            }
            return dishes;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find dishes by category: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
        }
    }

    // ============= Ingredient Methods =============

    public void saveIngredient(Ingredient ingredientToSave) {
        Connection con = null;
        PreparedStatement stmt = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "INSERT INTO ingredient (name, description, quantity_in_stock, unit) VALUES (?, ?, ?, ?)";
            stmt = con.prepareStatement(sql);
            stmt.setString(1, ingredientToSave.getName());
            stmt.setString(2, ingredientToSave.getDescription());
            stmt.setBigDecimal(3, ingredientToSave.getQuantityInStock());
            stmt.setString(4, ingredientToSave.getUnit());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save ingredient: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(stmt, con);
        }
    }

    public Ingredient findIngredientById(Integer ingredientId) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT * FROM ingredient WHERE id = ?";
            stmt = con.prepareStatement(sql);
            stmt.setInt(1, ingredientId);
            rs = stmt.executeQuery();

            if (rs.next()) {
                return mapIngredientFromResultSet(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find ingredient by ID: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
        }
    }

    public List<Ingredient> findAllIngredients() {
        Connection con = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT * FROM ingredient ORDER BY name";
            stmt = con.createStatement();
            rs = stmt.executeQuery(sql);

            List<Ingredient> ingredients = new ArrayList<>();
            while (rs.next()) {
                ingredients.add(mapIngredientFromResultSet(rs));
            }
            return ingredients;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve all ingredients: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
        }
    }

    // ============= OrderItem Methods =============

    public void saveOrderItem(OrderItem itemToSave) {
        Connection con = null;
        PreparedStatement stmt = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "INSERT INTO order_item (id_order, id_dish, quantity, unit_price, notes) VALUES (?, ?, ?, ?, ?)";
            stmt = con.prepareStatement(sql);
            stmt.setInt(1, itemToSave.getOrderId());
            stmt.setInt(2, itemToSave.getDishId());
            stmt.setInt(3, itemToSave.getQuantity());
            stmt.setBigDecimal(4, itemToSave.getUnitPrice());
            stmt.setString(5, itemToSave.getNotes());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save order item: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(stmt, con);
        }
    }

    public List<OrderItem> findOrderItemsByOrderId(Integer orderId) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT * FROM order_item WHERE id_order = ? ORDER BY created_at";
            stmt = con.prepareStatement(sql);
            stmt.setInt(1, orderId);
            rs = stmt.executeQuery();

            List<OrderItem> items = new ArrayList<>();
            while (rs.next()) {
                items.add(mapOrderItemFromResultSet(rs));
            }
            return items;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find order items: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
        }
    }

    // ============= Customer Methods =============

    public void saveCustomer(Customer customerToSave) {
        Connection con = null;
        PreparedStatement stmt = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "INSERT INTO customer (first_name, last_name, email, phone) VALUES (?, ?, ?, ?)";
            stmt = con.prepareStatement(sql);
            stmt.setString(1, customerToSave.getFirstName());
            stmt.setString(2, customerToSave.getLastName());
            stmt.setString(3, customerToSave.getEmail());
            stmt.setString(4, customerToSave.getPhone());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save customer: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(stmt, con);
        }
    }

    public Customer findCustomerById(Integer customerId) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT * FROM customer WHERE id = ?";
            stmt = con.prepareStatement(sql);
            stmt.setInt(1, customerId);
            rs = stmt.executeQuery();

            if (rs.next()) {
                return mapCustomerFromResultSet(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find customer by ID: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
        }
    }

    public List<Customer> findAllCustomers() {
        Connection con = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT * FROM customer ORDER BY last_name, first_name";
            stmt = con.createStatement();
            rs = stmt.executeQuery(sql);

            List<Customer> customers = new ArrayList<>();
            while (rs.next()) {
                customers.add(mapCustomerFromResultSet(rs));
            }
            return customers;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve all customers: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
        }
    }

    // ============= Staff Methods =============

    public void saveStaff(Staff staffToSave) {
        Connection con = null;
        PreparedStatement stmt = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "INSERT INTO staff (first_name, last_name, email, phone, position, hire_date, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)";
            stmt = con.prepareStatement(sql);
            stmt.setString(1, staffToSave.getFirstName());
            stmt.setString(2, staffToSave.getLastName());
            stmt.setString(3, staffToSave.getEmail());
            stmt.setString(4, staffToSave.getPhone());
            stmt.setString(5, staffToSave.getPosition());
            stmt.setDate(6, staffToSave.getHireDate() != null ? java.sql.Date.valueOf(staffToSave.getHireDate()) : null);
            stmt.setBoolean(7, staffToSave.getIsActive() != null ? staffToSave.getIsActive() : true);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save staff: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(stmt, con);
        }
    }

    public Staff findStaffById(Integer staffId) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT * FROM staff WHERE id = ?";
            stmt = con.prepareStatement(sql);
            stmt.setInt(1, staffId);
            rs = stmt.executeQuery();

            if (rs.next()) {
                return mapStaffFromResultSet(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find staff by ID: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
        }
    }

    public List<Staff> findAllStaff() {
        Connection con = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT * FROM staff WHERE is_active = true ORDER BY last_name, first_name";
            stmt = con.createStatement();
            rs = stmt.executeQuery(sql);

            List<Staff> staffList = new ArrayList<>();
            while (rs.next()) {
                staffList.add(mapStaffFromResultSet(rs));
            }
            return staffList;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve all staff: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
        }
    }

    // ============= Payment Methods =============

    public void savePayment(Payment paymentToSave) {
        Connection con = null;
        PreparedStatement stmt = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "INSERT INTO payment (id_order, amount, payment_method, status) VALUES (?, ?, ?, ?)";
            stmt = con.prepareStatement(sql);
            stmt.setInt(1, paymentToSave.getOrderId());
            stmt.setBigDecimal(2, paymentToSave.getAmount());
            stmt.setString(3, paymentToSave.getPaymentMethod());
            stmt.setString(4, paymentToSave.getStatus() != null ? paymentToSave.getStatus() : "completed");
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save payment: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(stmt, con);
        }
    }

    public Payment findPaymentById(Integer paymentId) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT * FROM payment WHERE id = ?";
            stmt = con.prepareStatement(sql);
            stmt.setInt(1, paymentId);
            rs = stmt.executeQuery();

            if (rs.next()) {
                return mapPaymentFromResultSet(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find payment by ID: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
        }
    }

    public List<Payment> findPaymentsByOrderId(Integer orderId) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            con = dbConnection.getDBConnection();
            String sql = "SELECT * FROM payment WHERE id_order = ? ORDER BY payment_date DESC";
            stmt = con.prepareStatement(sql);
            stmt.setInt(1, orderId);
            rs = stmt.executeQuery();

            List<Payment> payments = new ArrayList<>();
            while (rs.next()) {
                payments.add(mapPaymentFromResultSet(rs));
            }
            return payments;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find payments by order: " + e.getMessage(), e);
        } finally {
            dbConnection.attemptCloseDBConnection(rs, stmt, con);
        }
    }

    // ============= Helper Mapping Methods =============

    private Dish mapDishFromResultSet(ResultSet rs) throws SQLException {
        Dish dish = new Dish();
        dish.setId(rs.getInt("id"));
        dish.setName(rs.getString("name"));
        dish.setDescription(rs.getString("description"));
        dish.setPrice(rs.getBigDecimal("price"));
        dish.setCategory(rs.getString("category"));
        dish.setIsAvailable(rs.getBoolean("is_available"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            dish.setCreatedAt(createdAt.toInstant());
        }
        return dish;
    }

    private Ingredient mapIngredientFromResultSet(ResultSet rs) throws SQLException {
        Ingredient ingredient = new Ingredient();
        ingredient.setId(rs.getInt("id"));
        ingredient.setName(rs.getString("name"));
        ingredient.setDescription(rs.getString("description"));
        ingredient.setQuantityInStock(rs.getBigDecimal("quantity_in_stock"));
        ingredient.setUnit(rs.getString("unit"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            ingredient.setCreatedAt(createdAt.toInstant());
        }
        return ingredient;
    }

    private OrderItem mapOrderItemFromResultSet(ResultSet rs) throws SQLException {
        OrderItem item = new OrderItem();
        item.setId(rs.getInt("id"));
        item.setOrderId(rs.getInt("id_order"));
        item.setDishId(rs.getInt("id_dish"));
        item.setQuantity(rs.getInt("quantity"));
        item.setUnitPrice(rs.getBigDecimal("unit_price"));
        item.setNotes(rs.getString("notes"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            item.setCreatedAt(createdAt.toInstant());
        }
        return item;
    }

    private Customer mapCustomerFromResultSet(ResultSet rs) throws SQLException {
        Customer customer = new Customer();
        customer.setId(rs.getInt("id"));
        customer.setFirstName(rs.getString("first_name"));
        customer.setLastName(rs.getString("last_name"));
        customer.setEmail(rs.getString("email"));
        customer.setPhone(rs.getString("phone"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            customer.setCreatedAt(createdAt.toInstant());
        }
        return customer;
    }

    private Staff mapStaffFromResultSet(ResultSet rs) throws SQLException {
        Staff staff = new Staff();
        staff.setId(rs.getInt("id"));
        staff.setFirstName(rs.getString("first_name"));
        staff.setLastName(rs.getString("last_name"));
        staff.setEmail(rs.getString("email"));
        staff.setPhone(rs.getString("phone"));
        staff.setPosition(rs.getString("position"));
        Date hireDate = rs.getDate("hire_date");
        if (hireDate != null) {
            staff.setHireDate(hireDate.toLocalDate());
        }
        staff.setIsActive(rs.getBoolean("is_active"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            staff.setCreatedAt(createdAt.toInstant());
        }
        return staff;
    }

    private Payment mapPaymentFromResultSet(ResultSet rs) throws SQLException {
        Payment payment = new Payment();
        payment.setId(rs.getInt("id"));
        payment.setOrderId(rs.getInt("id_order"));
        payment.setAmount(rs.getBigDecimal("amount"));
        payment.setPaymentMethod(rs.getString("payment_method"));
        Timestamp paymentDate = rs.getTimestamp("payment_date");
        if (paymentDate != null) {
            payment.setPaymentDate(paymentDate.toInstant());
        }
        payment.setStatus(rs.getString("status"));
        return payment;
    }
}
