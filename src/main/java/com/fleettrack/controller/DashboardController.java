package com.fleettrack.controller;

import com.fleettrack.dto.response.DashboardSummaryDto;
import com.fleettrack.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "Fleet overview")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Counts of vehicles and deliveries by status, active deliveries and active drivers")
    @GetMapping("/summary")
    public DashboardSummaryDto summary() {
        return dashboardService.summary();
    }
}
