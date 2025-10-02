package org.example.ticket.controller;

import org.example.ticket.DTO.PriceTierDetailDTO;
import org.example.ticket.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TestController {

    private final TicketService ticketService;

    @Autowired
    public TestController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/tier/{priceTierId}")
    public ResponseEntity<PriceTierDetailDTO> getTierDetails(@PathVariable Long priceTierId) {
        PriceTierDetailDTO details = ticketService.getPriceTierDetails(priceTierId);
        if (details != null) {
            return ResponseEntity.ok(details);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}