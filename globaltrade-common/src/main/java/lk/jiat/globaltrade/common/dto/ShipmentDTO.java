package lk.jiat.globaltrade.common.dto;

import lk.jiat.globaltrade.common.constant.ShipmentStatus;
import java.io.Serializable;
import java.time.Instant;

public record ShipmentDTO(Long id,
                          String trackingNumber,
                          String originCountry,
                          String destinationCountry,
                          String carrierCode,
                          ShipmentStatus status,
                          Instant estimatedArrival,
                          Long vendorId) implements Serializable { }
