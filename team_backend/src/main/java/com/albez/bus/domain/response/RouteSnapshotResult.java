package com.albez.bus.domain.response;

import com.albez.bus.model.BusObservation;
import com.albez.bus.model.SnapshotStation;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class RouteSnapshotResult {
    private Long id;
    @JsonProperty("collected_at")
    private String collectedAt;
    @JsonProperty("route_name")
    private String routeName;
    private Integer direction;
    @JsonProperty("station_count")
    private Integer stationCount;
    @JsonProperty("bus_count")
    private Integer busCount;
    private List<BusObservation> buses;
    private List<SnapshotStation> stations;
}
