package com.fleettrack.mapper;

import com.fleettrack.dto.request.CustomerRequestDto;
import com.fleettrack.dto.response.CustomerResponseDto;
import com.fleettrack.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {
    public Customer toEntity(CustomerRequestDto dto) {
        Customer customer = new Customer();
        customer.setName(dto.getName());
        customer.setPhone(dto.getPhone());
        customer.setPickupLatitude(dto.getPickupLatitude());
        customer.setPickupLongitude(dto.getPickupLongitude());
        customer.setDropLatitude(dto.getDropLatitude());
        customer.setDropLongitude(dto.getDropLongitude());
        return customer;
    }

    public CustomerResponseDto toDto(Customer customer) {
        CustomerResponseDto dto = new CustomerResponseDto();
        dto.setId(customer.getId());
        dto.setName(customer.getName());
        dto.setPhone(customer.getPhone());
        dto.setPickupLatitude(customer.getPickupLatitude());
        dto.setPickupLongitude(customer.getPickupLongitude());
        dto.setDropLatitude(customer.getDropLatitude());
        dto.setDropLongitude(customer.getDropLongitude());
        return dto;
    }
}
