package com.fleettrack;

import com.fleettrack.entity.Vehicle;
import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.entity.enums.VehicleType;
import com.fleettrack.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the Flyway migrations on a real MySQL 8 and lets Hibernate validate every entity
 * against the result. H2 in MySQL mode is close but not identical, so this is the check
 * that the production schema really matches the code. Skipped when Docker is not available
 * (it always runs in CI).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Autowired JdbcTemplate jdbc;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void migrationsApplyAndMatchEntities() {
        // The context only starts if Flyway succeeded and Hibernate's validate found no mismatch
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        assertNotNull(applied);
        assertTrue(applied >= 1);
    }

    @Test
    void selectForUpdateWorksOnMySql() {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber("MH01MYSQL1");
        vehicle.setType(VehicleType.VAN);
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        Long id = vehicleRepository.save(vehicle).getId();

        new TransactionTemplate(txManager).executeWithoutResult(tx ->
                assertTrue(vehicleRepository.findByIdForUpdate(id).isPresent()));
    }
}
