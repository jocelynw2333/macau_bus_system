package com.albez.bus.domain.response;

import com.albez.bus.model.StationInfo;
import lombok.Data;

import java.util.List;

@Data
public class StationResponse {
    private StationData data;
    private Header header;

    @Data
    public static class StationData {
        private String routeCode;
        private List<StationInfo> stationInfo;
    }

    @Data
    public static class Header {
        private String status;
    }
}
