package com.easytest.engineering;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reusable JDBC atom for per-application MySQL connections and data seeding/verification. */
public final class JdbcAtom {

    private JdbcAtom() {
    }

    public static List<Map<String, Object>> query(String url, String username, String password,
                                                  String sql, Object... params) {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement statement = prepare(connection, sql, params);
             ResultSet resultSet = statement.executeQuery()) {
            ResultSetMetaData meta = resultSet.getMetaData();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i), resultSet.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("JDBC query failed: " + e.getMessage(), e);
        }
    }

    public static int update(String url, String username, String password, String sql, Object... params) {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement statement = prepare(connection, sql, params)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("JDBC update failed: " + e.getMessage(), e);
        }
    }

    public static Map<String, Object> queryOne(String url, String username, String password,
                                               String sql, Object... params) {
        List<Map<String, Object>> rows = query(url, username, password, sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
        return statement;
    }
}
