package org.example.order.DTO;

import lombok.Data;

@Data
public class QueueConfigRequest {
    private Long ticketItemId;
    private Integer releaseRate;
    private Boolean isActive;
}
