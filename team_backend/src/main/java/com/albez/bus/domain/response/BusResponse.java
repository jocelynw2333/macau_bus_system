package com.albez.bus.domain.response;

import lombok.Data;

import java.util.List;

@Data
public class BusResponse {
    private BusData data;
    private String header;

    @Data
    public static class BusData {
        private String lastBusType;
        private String badCar;
        private String lastBusPlate;
        private String toBeginBus;
        private String busColor;
        private List<RouteStationBusData> routeInfo;
    }

    @Data
    public static class RouteStationBusData {
        private String staCode;
        private List<BusDataInfo> busInfo;
    }

    @Data
    public static class BusDataInfo {
        private String busType;
        private String busCode;
        private String busPlate;
        private String status;
        private String isFacilities;
        private String passengerFlow;
        private String speed;
    }
}
