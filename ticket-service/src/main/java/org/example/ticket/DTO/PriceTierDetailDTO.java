package org.example.ticket.DTO;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PriceTierDetailDTO {
    private static final long serialVersionUID = 1L;

    private Long tierId;
    private String tierName;
    private BigDecimal price;
    private Integer TotalInventory;


    private Long eventId;
    private LocalDateTime EventTime;
    private String venueName;
    private String city;

    private Long ticketId;
    private String ticketName;
    private String description;
}
