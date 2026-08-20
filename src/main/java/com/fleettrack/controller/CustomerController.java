package com.fleettrack.controller;

import com.fleettrack.dto.request.CustomerRequestDto;
import com.fleettrack.dto.response.CustomerResponseDto;
import com.fleettrack.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public Page<CustomerResponseDto> getCustomers(Pageable pageable) {
        return customerService.getCustomers(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponseDto createCustomer(@Valid @RequestBody CustomerRequestDto request) {
        return customerService.createCustomer(request);
    }

    @GetMapping("/{id}")
    public CustomerResponseDto getCustomer(@PathVariable Long id) {
        return customerService.getCustomer(id);
    }
}
