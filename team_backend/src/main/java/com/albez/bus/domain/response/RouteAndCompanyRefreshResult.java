package com.albez.bus.domain.response;

import com.albez.bus.model.CompanyInfo;
import com.albez.bus.model.RouteInfo;
import com.albez.bus.model.RouteStationInfo;
import com.albez.bus.model.StationInfo;
import lombok.Data;

import java.util.List;

@Data
public class RouteAndCompanyRefreshResult {
    private Integer companyCount;
    private Integer routeCount;
    private Integer stationCount;
    private Integer routeStationCount;
    private List<CompanyInfo> companyList;
    private List<RouteInfo> routeList;
    private List<StationInfo> stationList;
    private List<RouteStationInfo> routeStationList;
}
