package org.example.ticket.service;

import org.example.ticket.DTO.PriceTierDetailDTO;

public interface TicketService  {
    PriceTierDetailDTO getPriceTierDetails(Long priceTierId);

    void updatePriceTier(PriceTierDetailDTO priceTierDetailDTO);

    void evictPriceTierCacher(Long priceTierId);
}
