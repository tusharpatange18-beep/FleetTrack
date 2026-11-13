package com.fleettrack.concurrency;

import com.fleettrack.entity.Customer;
import com.fleettrack.entity.Delivery;
import com.fleettrack.entity.Driver;
import com.fleettrack.entity.Vehicle;
import com.fleettrack.entity.enums.DeliveryStatus;
import com.fleettrack.entity.enums.DriverStatus;
import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.entity.enums.VehicleType;
import com.fleettrack.exception.ConflictException;
import com.fleettrack.repository.CustomerRepository;
import com.fleettrack.repository.DeliveryRepository;
import com.fleettrack.repository.DriverRepository;
import com.fleettrack.repository.VehicleRepository;
import com.fleettrack.service.DeliveryService;
import com.fleettrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the two race fixes against a real database with real transactions:
 * row locking on assignment, and @DynamicUpdate against lost updates from location pings.
 */
class ConcurrencyTest extends IntegrationTestSupport {

    @Autowired DeliveryService deliveryService;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired DeliveryRepository deliveryRepository;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void manyDispatchersAssigningTheSameVehicle_onlyOneWins() throws Exception {
        Vehicle vehicle = readyVehicle();
        int attempts = 8;
        List<Long> deliveryIds = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            deliveryIds.add(newDelivery().getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (Long deliveryId : deliveryIds) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    deliveryService.assignDelivery(deliveryId, vehicle.getId());
                    successes.incrementAndGet();
                } catch (ConflictException e) {
                    conflicts.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown(); // release all threads at once
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(1, successes.get(), "exactly one assignment may succeed");
        assertEquals(attempts - 1, conflicts.get());
        assertEquals(1, deliveryRepository.findAll().stream()
                .filter(d -> d.getStatus() == DeliveryStatus.ASSIGNED).count());
        assertEquals(VehicleStatus.BUSY, vehicleRepository.findById(vehicle.getId()).orElseThrow().getStatus());
    }

    /**
     * A location ping loads the vehicle (AVAILABLE); before it commits, an assignment marks the
     * vehicle BUSY and commits. Without @DynamicUpdate the ping's UPDATE would rewrite every
     * column and put the status back to AVAILABLE.
     */
    @Test
    void locationPing_doesNotOverwriteConcurrentStatusChange() {
        Long vehicleId = readyVehicle().getId();
        TransactionTemplate ping = new TransactionTemplate(txManager);
        TransactionTemplate assignment = new TransactionTemplate(txManager);
        assignment.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        ping.executeWithoutResult(outer -> {
            Vehicle stale = vehicleRepository.findById(vehicleId).orElseThrow();
            assertEquals(VehicleStatus.AVAILABLE, stale.getStatus());

            // Another transaction commits a status change in the meantime
            assignment.executeWithoutResult(inner ->
                    vehicleRepository.findById(vehicleId).orElseThrow().setStatus(VehicleStatus.BUSY));

            stale.setCurrentLatitude(19.5);
            stale.setCurrentLongitude(73.5);
            stale.setLocationUpdatedAt(LocalDateTime.now());
        });

        Vehicle after = vehicleRepository.findById(vehicleId).orElseThrow();
        assertEquals(VehicleStatus.BUSY, after.getStatus(), "status change must survive the location update");
        assertEquals(19.5, after.getCurrentLatitude());
    }

    private Vehicle readyVehicle() {
        Driver driver = new Driver();
        driver.setName("Ravi");
        driver.setPhone("9000000001");
        driver.setLicenseNumber("LIC-RACE");
        driver.setStatus(DriverStatus.ACTIVE);
        driverRepository.save(driver);

        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber("MH12RACE01");
        vehicle.setType(VehicleType.VAN);
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        vehicle.setDriver(driver);
        vehicle.setCurrentLatitude(19.0);
        vehicle.setCurrentLongitude(73.0);
        vehicle.setLocationUpdatedAt(LocalDateTime.now());
        return vehicleRepository.save(vehicle);
    }

    private Delivery newDelivery() {
        Customer customer = new Customer();
        customer.setName("C");
        customer.setPhone("9111111111");
        customer.setPickupLatitude(19.0);
        customer.setPickupLongitude(73.0);
        customer.setDropLatitude(19.1);
        customer.setDropLongitude(73.1);
        customerRepository.save(customer);

        Delivery delivery = new Delivery();
        delivery.setCustomer(customer);
        delivery.setStatus(DeliveryStatus.CREATED);
        return deliveryRepository.save(delivery);
    }
}
