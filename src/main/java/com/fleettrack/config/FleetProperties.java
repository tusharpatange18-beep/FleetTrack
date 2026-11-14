package com.fleettrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Application settings under the {@code fleettrack.*} prefix.
 *
 * @param locationMaxAge        a vehicle whose last ping is older than this is not offered by
 *                              nearby search or auto-assignment (its position is unreliable)
 * @param autoAssignRadiusKm    default search radius for auto-assignment
 * @param corsAllowedOrigins    browser origins allowed to call the API (empty = none)
 * @param loginAttemptsPerMinute failed-or-not login attempts allowed per client IP per minute
 */
@ConfigurationProperties(prefix = "fleettrack")
public record FleetProperties(
        @DefaultValue("15m") Duration locationMaxAge,
        @DefaultValue("25") double autoAssignRadiusKm,
        @DefaultValue List<String> corsAllowedOrigins,
        @DefaultValue("10") int loginAttemptsPerMinute) {
}
