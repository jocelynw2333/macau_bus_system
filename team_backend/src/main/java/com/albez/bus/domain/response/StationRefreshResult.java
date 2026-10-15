package com.albez.bus.domain.response;

import com.albez.bus.model.RouteStationInfo;
import com.albez.bus.model.StationInfo;
import lombok.Data;

import java.util.List;

@Data
public class StationRefreshResult {
    private Integer stationCount;
    private Integer routeStationCount;
    private List<StationInfo> stationList;
    private List<RouteStationInfo> routeStationList;
}
