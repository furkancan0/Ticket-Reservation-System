package com.ticketing.service;

import com.ticketing.domain.entity.DiscountCode;
import com.ticketing.domain.repository.DiscountCodeRepository;
import com.ticketing.dto.request.CreateDiscountRequest;
import com.ticketing.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountCodeRepository discountRepo;

    @Transactional
    public DiscountCode create(CreateDiscountRequest req) {
        DiscountCode dc = DiscountCode.builder()
                .code(req.getCode().toUpperCase())
                .discountType(req.getDiscountType())
                .value(req.getValue())
                .maxUses(req.getMaxUses())
                .usedCount(0)
                .validFrom(req.getValidFrom())
                .validUntil(req.getValidUntil())
                .build();
        return discountRepo.save(dc);
    }

    @Transactional(readOnly = true)
    public List<DiscountCode> findAll() {
        return discountRepo.findAll();
    }

    @Transactional
    public void deactivate(UUID id) {
        DiscountCode dc = discountRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount code not found: " + id));
        dc.setActive(false);
        discountRepo.save(dc);
    }
}
