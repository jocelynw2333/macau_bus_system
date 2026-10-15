package com.albez.bus.controller;

import com.albez.bus.domain.request.LanguageRefreshRequest;
import com.albez.bus.domain.request.StationRefreshRequest;
import com.albez.bus.domain.response.BusRefreshResult;
import com.albez.bus.domain.response.StationRefreshResult;
import com.albez.bus.model.CompanyInfo;
import com.albez.bus.model.RouteInfo;
import com.albez.bus.service.BusService;
import com.albez.bus.service.CompanyService;
import com.albez.bus.service.RouteService;
import com.albez.bus.service.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BusDataController {
    private final BusService busService;
    private final CompanyService companyService;
    private final RouteService routeService;
    private final StationService stationService;

    /**
     * 手动刷新公司信息。
     */
    @PostMapping("/company/refresh")
    public List<CompanyInfo> refreshCompanyInfo(@RequestBody(required = false) LanguageRefreshRequest request) {
        if (request == null || !StringUtils.hasText(request.getLang())) {
            return companyService.refreshCompanyInfo();
        }
        return companyService.refreshCompanyInfo(request.getLang());
    }

    /**
     * 手动刷新线路信息。
     */
    @PostMapping("/route/refresh")
    public List<RouteInfo> refreshRouteInfo() {
        return routeService.refreshRouteInfo();
    }

    /**
     * 手动刷新站点信息；未指定线路时按数据库已有线路逐一刷新。
     */
    @PostMapping("/station/refresh")
    public StationRefreshResult refreshStationInfo(@RequestBody(required = false) StationRefreshRequest request) {
        if (request == null || !StringUtils.hasText(request.getRouteName())) {
            return stationService.refreshStationInfo();
        }
        if (!StringUtils.hasText(request.getLang())) {
            return stationService.refreshStationInfo(request.getRouteName(), request.getDir());
        }
        return stationService.refreshStationInfo(request.getRouteName(), request.getDir(), request.getLang());
    }

    /**
     * 手动刷新车辆状态；未指定线路时按数据库已有线路逐一刷新。
     */
    @PostMapping("/bus/refresh")
    public BusRefreshResult refreshBusInfo(@RequestBody(required = false) StationRefreshRequest request) {
        if (request == null || !StringUtils.hasText(request.getRouteName())) {
            return busService.refreshBusInfo();
        }
        return busService.refreshBusInfo(request.getRouteName(), request.getDir());
    }
}
