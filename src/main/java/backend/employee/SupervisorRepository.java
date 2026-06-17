package backend.employee;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class SupervisorRepository {

    public List<Supervisor> findActiveSupervisors() {
        List<Supervisor> supervisors = new ArrayList<>();
        Connection connection = null;

        String sql = """
                SELECT supervisor_id, first_name, last_name, department, position
                FROM supervisors
                WHERE status = 'Active'
                ORDER BY first_name, last_name
                """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    supervisors.add(new Supervisor(
                            resultSet.getInt("supervisor_id"),
                            resultSet.getString("first_name"),
                            resultSet.getString("last_name"),
                            resultSet.getString("department"),
                            resultSet.getString("position")
                    ));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }

        return supervisors;
    }
}