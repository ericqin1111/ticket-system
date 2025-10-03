package org.example.ticket.controller;

import jakarta.annotation.Resource;
import org.example.ticket.DTO.PriceTierDetailDTO;
import org.example.ticket.service.TicketService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ticket/tier") // 路径修改为/tiers，更符合RESTful语义
public class TicketController {

    @Resource
    private TicketService ticketService;

    @GetMapping("/{id}")
    public PriceTierDetailDTO getPriceTierDetails(@PathVariable("id") Long id) {
        return ticketService.getPriceTierDetails(id);
    }

    @PostMapping("/update")
    public String updatePriceTier(@RequestBody PriceTierDetailDTO dto) {
        ticketService.updatePriceTier(dto);
        return "Update request processed. Cache eviction message sent for price tier ID: " + dto.getTierId();
    }
}
