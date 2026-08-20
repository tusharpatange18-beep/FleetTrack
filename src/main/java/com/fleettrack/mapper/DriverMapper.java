package com.fleettrack.mapper;

import com.fleettrack.dto.request.DriverRequestDto;
import com.fleettrack.dto.response.DriverResponseDto;
import com.fleettrack.entity.Driver;
import org.springframework.stereotype.Component;

@Component
public class DriverMapper {
    public Driver toEntity(DriverRequestDto dto) {
        Driver driver = new Driver();
        driver.setName(dto.getName());
        driver.setPhone(dto.getPhone());
        driver.setLicenseNumber(dto.getLicenseNumber());
        driver.setStatus(dto.getStatus());
        return driver;
    }

    public DriverResponseDto toDto(Driver driver) {
        DriverResponseDto dto = new DriverResponseDto();
        dto.setId(driver.getId());
        dto.setName(driver.getName());
        dto.setPhone(driver.getPhone());
        dto.setLicenseNumber(driver.getLicenseNumber());
        dto.setStatus(driver.getStatus().name());
        return dto;
    }
}
