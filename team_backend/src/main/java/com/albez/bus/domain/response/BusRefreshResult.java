package com.albez.bus.domain.response;

import com.albez.bus.model.BusInfo;
import com.albez.bus.model.StationBusInfo;
import lombok.Data;

import java.util.List;

@Data
public class BusRefreshResult {
    private Integer busCount;
    private Integer stationBusCount;
    private List<BusInfo> busList;
    private List<StationBusInfo> stationBusList;
}
