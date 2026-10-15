package com.albez.bus.service;

import com.albez.bus.client.BusClient;
import com.albez.bus.domain.response.BusRefreshResult;
import com.albez.bus.domain.response.BusResponse;
import com.albez.bus.mapper.BusInfoMapper;
import com.albez.bus.mapper.StationBusInfoMapper;
import com.albez.bus.model.BusInfo;
import com.albez.bus.model.RouteInfo;
import com.albez.bus.model.StationBusInfo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusService {
    private final BusClient busClient;
    private final BusInfoMapper busInfoMapper;
    private final StationBusInfoMapper stationBusInfoMapper;
    private final RouteService routeService;

    @Transactional
    public BusRefreshResult refreshBusInfo() {
        return refreshBusInfo(routeService.listRouteInfo());
    }

    @Transactional
    public BusRefreshResult refreshBusInfo(List<RouteInfo> routeInfoList) {
        if (CollectionUtils.isEmpty(routeInfoList)) {
            return buildBusRefreshResult(List.of(), List.of());
        }

        List<BusInfo> busInfoList = new ArrayList<>();
        List<StationBusInfo> stationBusInfoList = new ArrayList<>();
        for (RouteInfo route : routeInfoList) {
            if (!StringUtils.hasText(route.getRouteName())) {
                log.warn("Skip refreshing bus info because routeName is empty, routeCode={}, direction={}",
                        route.getRouteCode(), route.getDirection());
                continue;
            }
            BusRefreshResult result = refreshBusInfo(route);
            busInfoList.addAll(result.getBusList());
            stationBusInfoList.addAll(result.getStationBusList());
        }
        return buildBusRefreshResult(busInfoList, stationBusInfoList);
    }

    @Transactional
    public BusRefreshResult refreshBusInfo(String routeName, Integer dir) {
        if (!StringUtils.hasText(routeName)) {
            return refreshBusInfo();
        }
        if (dir == null) {
            return refreshBusInfo(routeService.getRouteInfoByRouteName(routeName));
        }
        return refreshBusInfo(routeName, List.of(dir));
    }

    private BusRefreshResult refreshBusInfo(RouteInfo route) {
        return refreshBusInfo(route.getRouteName(), resolveRefreshDirs(route));
    }

    private BusRefreshResult refreshBusInfo(String routeName, List<Integer> dirs) {
        List<BusInfo> busInfoList = new ArrayList<>();
        List<StationBusInfo> stationBusInfoList = new ArrayList<>();
        for (Integer dir : dirs) {
            BusRefreshResult result = refreshBusInfoByDirection(routeName, dir);
            busInfoList.addAll(result.getBusList());
            stationBusInfoList.addAll(result.getStationBusList());
        }
        return buildBusRefreshResult(busInfoList, stationBusInfoList);
    }

    private BusRefreshResult refreshBusInfoByDirection(String routeName, Integer dir) {
        BusResponse.BusData data = getBusData(busClient.getBusData(routeName, dir), routeName, dir);
        Map<String, BusInfo> busInfoMap = new LinkedHashMap<>();
        List<StationBusInfo> stationBusInfoList = new ArrayList<>();

        for (BusResponse.RouteStationBusData routeStation : data.getRouteInfo()) {
            if (!StringUtils.hasText(routeStation.getStaCode()) || CollectionUtils.isEmpty(routeStation.getBusInfo())) {
                continue;
            }
            for (BusResponse.BusDataInfo source : routeStation.getBusInfo()) {
                if (!StringUtils.hasText(source.getBusCode())) {
                    log.warn("Skip bus with empty busCode, routeName={}, dir={}, staCode={}",
                            routeName, dir, routeStation.getStaCode());
                    continue;
                }
                BusInfo busInfo = buildBusInfo(source, routeName, dir, routeStation.getStaCode());
                upsertBusInfo(busInfo);
                busInfoMap.put(busInfo.getBusCode(), busInfo);

                StationBusInfo stationBusInfo = buildStationBusInfo(routeStation.getStaCode(), source, routeName, dir);
                StationBusInfo previous = selectLastStationBusInfo(stationBusInfo.getBusCode());
                if (hasStationBusStateChanged(previous, stationBusInfo)) {
                    stationBusInfoMapper.insert(stationBusInfo);
                    stationBusInfoList.add(stationBusInfo);
                    log.debug("Recorded bus state change, routeName={}, dir={}, busCode={}, staCode={}, status={}, movement={}",
                            routeName, dir, stationBusInfo.getBusCode(), stationBusInfo.getStaCode(),
                            stationBusInfo.getStatus(), resolveMovement(previous, stationBusInfo));
                }
            }
        }

        return buildBusRefreshResult(new ArrayList<>(busInfoMap.values()), stationBusInfoList);
    }

    private BusResponse.BusData getBusData(BusResponse response, String routeName, Integer dir) {
        if (response == null || response.getData() == null || CollectionUtils.isEmpty(response.getData().getRouteInfo())) {
            throw new IllegalStateException("getBusData returned empty routeInfo, routeName=" + routeName + ", dir=" + dir);
        }
        return response.getData();
    }

    private BusInfo buildBusInfo(BusResponse.BusDataInfo source, String routeName, Integer dir, String staCode) {
        BusInfo busInfo = new BusInfo();
        busInfo.setBusCode(source.getBusCode());
        busInfo.setBusType(parseInteger(source.getBusType(), "busType", routeName, dir, staCode, source.getBusCode()));
        busInfo.setBusPlate(source.getBusPlate());
        busInfo.setIsFacilities(parseInteger(source.getIsFacilities(), "isFacilities", routeName, dir, staCode, source.getBusCode()));
        return busInfo;
    }

    private StationBusInfo buildStationBusInfo(String staCode, BusResponse.BusDataInfo source, String routeName, Integer dir) {
        StationBusInfo stationBusInfo = new StationBusInfo();
        stationBusInfo.setStaCode(staCode);
        stationBusInfo.setBusCode(source.getBusCode());
        stationBusInfo.setStatus(parseInteger(source.getStatus(), "status", routeName, dir, staCode, source.getBusCode()));
        stationBusInfo.setPassengerFlow(parseInteger(source.getPassengerFlow(), "passengerFlow", routeName, dir, staCode, source.getBusCode()));
        stationBusInfo.setTime(LocalDateTime.now());
        return stationBusInfo;
    }

    private void upsertBusInfo(BusInfo busInfo) {
        BusInfo existing = busInfoMapper.selectById(busInfo.getBusCode());
        if (existing == null) {
            busInfoMapper.insert(busInfo);
        } else {
            busInfoMapper.updateById(busInfo);
        }
    }

    private boolean hasStationBusStateChanged(StationBusInfo previous, StationBusInfo current) {
        return previous == null
                || !Objects.equals(previous.getStaCode(), current.getStaCode())
                || !Objects.equals(previous.getStatus(), current.getStatus());
    }

    private StationBusInfo selectLastStationBusInfo(String busCode) {
        return stationBusInfoMapper.selectOne(
                Wrappers.<StationBusInfo>lambdaQuery()
                        .eq(StationBusInfo::getBusCode, busCode)
                        .orderByDesc(StationBusInfo::getTime)
                        .orderByDesc(StationBusInfo::getId)
                        .last("LIMIT 1")
        );
    }

    private String resolveMovement(StationBusInfo previous, StationBusInfo current) {
        if (current.getStatus() != null && current.getStatus() == 1) {
            return "ARRIVE";
        }
        if (current.getStatus() != null && current.getStatus() == 0) {
            return "DEPART";
        }
        if (previous != null && !Objects.equals(previous.getStaCode(), current.getStaCode())) {
            return "ARRIVE";
        }
        return "UNKNOWN";
    }

    private List<Integer> resolveRefreshDirs(RouteInfo route) {
        if (route.getDirection() == null) {
            throw new IllegalStateException("Route direction is empty: " + route.getRouteName());
        }
        if (route.getDirection() == 0) {
            return List.of(0, 1);
        }
        if (route.getDirection() == 2) {
            return List.of(0);
        }
        throw new IllegalStateException("Unsupported route direction: " + route.getDirection()
                + ", routeName=" + route.getRouteName());
    }

    private Integer parseInteger(String value, String fieldName, String routeName, Integer dir, String staCode, String busCode) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            log.warn("Unable to parse bus field, fieldName={}, value={}, routeName={}, dir={}, staCode={}, busCode={}",
                    fieldName, value, routeName, dir, staCode, busCode);
            return null;
        }
    }

    private BusRefreshResult buildBusRefreshResult(List<BusInfo> busInfoList,
                                                   List<StationBusInfo> stationBusInfoList) {
        BusRefreshResult result = new BusRefreshResult();
        result.setBusCount(busInfoList.size());
        result.setStationBusCount(stationBusInfoList.size());
        result.setBusList(busInfoList);
        result.setStationBusList(stationBusInfoList);
        return result;
    }
}
