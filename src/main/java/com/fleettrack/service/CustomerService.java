package com.fleettrack.service;

import com.fleettrack.dto.request.CustomerRequestDto;
import com.fleettrack.dto.response.CustomerResponseDto;
import com.fleettrack.entity.Customer;
import com.fleettrack.exception.ResourceNotFoundException;
import com.fleettrack.mapper.CustomerMapper;
import com.fleettrack.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Customers and their saved pickup/drop addresses. */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    /** One page of customers, converted to response DTOs. */
    public Page<CustomerResponseDto> getCustomers(Pageable pageable) {
        Page<Customer> page = customerRepository.findAll(pageable);
        // Page.map converts every Customer on the page with customerMapper.toDto
        return page.map(customerMapper::toDto);
    }

    public CustomerResponseDto createCustomer(CustomerRequestDto requestDto) {
        Customer customer = customerMapper.toEntity(requestDto);
        Customer saved = customerRepository.save(customer);
        return customerMapper.toDto(saved);
    }

    public CustomerResponseDto getCustomer(Long id) {
        // orElseThrow: return the customer if found, otherwise throw a 404 error
        Customer customer = customerRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return customerMapper.toDto(customer);
    }
}
