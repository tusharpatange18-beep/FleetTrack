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

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public Page<CustomerResponseDto> getCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable).map(customerMapper::toDto);
    }

    public CustomerResponseDto createCustomer(CustomerRequestDto requestDto) {
        Customer customer = customerMapper.toEntity(requestDto);
        return customerMapper.toDto(customerRepository.save(customer));
    }

    public CustomerResponseDto getCustomer(Long id) {
        return customerMapper.toDto(customerRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found")));
    }
}
