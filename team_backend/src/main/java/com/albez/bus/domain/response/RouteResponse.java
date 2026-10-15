package com.albez.bus.domain.response;

import com.albez.bus.model.StationInfo;
import lombok.Data;

import java.util.List;

@Data
public class RouteResponse {
    private RouteData data;
    private String header;

    @Data
    public static class RouteData {
        private String routeCode;
        private String suspend;
        private List<Object> routeCoors;
        private Integer routeChange;
        private List<Object> msgList;
        private String dir;
        private String routeChangeWebBaseURL;
        private List<StationInfo> routeInfo;
        private List<Object> busInfo;
        private String routeName;
        private String organCode;
        private String lastBusPlate;
        private List<Object> stationCoors;
        private Integer direction;
    }
}
