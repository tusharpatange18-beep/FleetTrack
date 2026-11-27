package com.fleettrack.entity;

import com.fleettrack.entity.enums.VehicleStatus;
import com.fleettrack.entity.enums.VehicleType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import java.time.LocalDateTime;

/*
 * @DynamicUpdate: UPDATE statements contain only the columns that changed. Location pings are
 * frequent and not row-locked; without this, a ping that loaded the vehicle before an assignment
 * committed would write the old status back (a lost update).
 */
@Entity                 // JPA: this class is a database table
@DynamicUpdate          // see the comment above
@Table(name = "vehicle")
// Lombok writes the getters, setters and constructors for us at compile time
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String registrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleStatus status;

    private Double currentLatitude;
    private Double currentLongitude;
    private LocalDateTime locationUpdatedAt;

    // One driver per vehicle. LAZY = the driver row is only loaded when we actually use it.
    // unique = true: the database itself refuses one driver on two vehicles.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", unique = true)
    private Driver driver;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
