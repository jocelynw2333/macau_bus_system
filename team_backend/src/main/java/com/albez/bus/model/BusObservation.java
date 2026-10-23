package com.albez.bus.model;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BusObservation {
    @JsonProperty("sta_code")
    private String staCode;
    @JsonProperty("bus_code")
    private String busCode;
    @JsonProperty("bus_plate")
    private String busPlate;
    @JsonProperty("bus_type")
    private String busType;
    private String status;
    @JsonProperty("passenger_flow")
    private String passengerFlow;
    private BigDecimal speed;
    private BigDecimal latitude;
    private BigDecimal longitude;
    @JsonIgnore
    private LocalDateTime departedAt;

    @JsonGetter("departed_at")
    public String getDepartedAtIso() {
        return "0".equals(status) && departedAt != null ? departedAt.toString() : null;
    }
}
