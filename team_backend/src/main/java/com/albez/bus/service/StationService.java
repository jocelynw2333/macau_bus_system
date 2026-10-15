package com.albez.bus.service;

import com.albez.bus.client.BusClient;
import com.albez.bus.domain.enums.MacauWebLanguage;
import com.albez.bus.domain.response.RouteResponse;
import com.albez.bus.domain.response.StationRefreshResult;
import com.albez.bus.mapper.RouteStationInfoMapper;
import com.albez.bus.mapper.StationInfoMapper;
import com.albez.bus.model.RouteInfo;
import com.albez.bus.model.RouteStationInfo;
import com.albez.bus.model.StationInfo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class StationService {
    private final BusClient busClient;
    private final StationInfoMapper stationInfoMapper;
    private final RouteStationInfoMapper routeStationInfoMapper;
    private final RouteService routeService;

    /**
     * 获取指定线路的站点明细。
     */
    public List<StationInfo> buildStationInfo(String routeName, Integer dir, String lang) {
        return buildStationRefreshResult(routeName, dir, lang).getStationList();
    }

    /**
     * 获取指定线路的站点和线路站点关系。
     */
    public StationRefreshResult buildStationRefreshResult(String routeName, Integer dir, String lang) {
        RouteResponse response = busClient.getRouteData(routeName, dir, lang);
        RouteResponse.RouteData data = getRouteData(response, routeName, dir, lang);
        List<StationInfo> stationInfoList = data.getRouteInfo();
        List<RouteStationInfo> routeStationInfoList = buildRouteStationInfo(data, dir);
        return buildStationRefreshResult(stationInfoList, routeStationInfoList);
    }

    /**
     * 按数据库已有线路刷新站点多语言明细。
     */
    @Transactional
    public StationRefreshResult refreshStationInfo() {
        List<RouteInfo> routeInfoList = routeService.listRouteInfo();
        log.info("Start refreshing station info for all routes, routeCount={}", routeInfoList.size());
        return refreshStationInfo(routeInfoList);
    }

    /**
     * 按线路列表刷新站点多语言明细，线路站点关系按繁体中文请求写入一次。
     */
    @Transactional
    public StationRefreshResult refreshStationInfo(List<RouteInfo> routeInfoList) {
        if (CollectionUtils.isEmpty(routeInfoList)) {
            log.warn("Skip refreshing station info because route list is empty");
            return buildStationRefreshResult(List.of(), List.of());
        }
        List<StationInfo> stationInfoList = new ArrayList<>();
        List<RouteStationInfo> routeStationInfoList = new ArrayList<>();
        for (RouteInfo route : routeInfoList) {
            if (!StringUtils.hasText(route.getRouteName())) {
                log.warn("Skip refreshing station info because routeName is empty, routeCode={}, direction={}",
                        route.getRouteCode(), route.getDirection());
                continue;
            }
            StationRefreshResult stationRefreshResult = refreshStationInfo(route);
            stationInfoList.addAll(stationRefreshResult.getStationList());
            routeStationInfoList.addAll(stationRefreshResult.getRouteStationList());
        }
        log.info("Finished refreshing station info for all routes, stationCount={}, routeStationCount={}",
                stationInfoList.size(), routeStationInfoList.size());
        return buildStationRefreshResult(stationInfoList, routeStationInfoList);
    }

    /**
     * 刷新指定线路站点四语言明细，线路站点关系按繁体中文请求写入一次。
     */
    @Transactional
    public StationRefreshResult refreshStationInfo(String routeName, Integer dir) {
        if (!StringUtils.hasText(routeName)) {
            return refreshStationInfo();
        }
        if (dir == null) {
            return refreshStationInfo(routeService.getRouteInfoByRouteName(routeName));
        }
        log.debug("Refresh station info by explicit direction, routeName={}, dir={}", routeName, dir);
        return refreshStationInfoByDirection(routeName, dir);
    }

    private StationRefreshResult refreshStationInfo(RouteInfo route) {
        List<Integer> dirs = resolveRefreshDirs(route);
        log.debug("Resolved station refresh directions, routeName={}, routeCode={}, routeDirection={}, refreshDirs={}",
                route.getRouteName(), route.getRouteCode(), route.getDirection(), dirs);
        return refreshStationInfo(route.getRouteName(), dirs);
    }

    private StationRefreshResult refreshStationInfo(String routeName, List<Integer> dirs) {
        List<StationInfo> stationInfoList = new ArrayList<>();
        List<RouteStationInfo> routeStationInfoList = new ArrayList<>();
        for (Integer dir : dirs) {
            StationRefreshResult stationRefreshResult = refreshStationInfoByDirection(routeName, dir);
            stationInfoList.addAll(stationRefreshResult.getStationList());
            routeStationInfoList.addAll(stationRefreshResult.getRouteStationList());
        }
        return buildStationRefreshResult(stationInfoList, routeStationInfoList);
    }

    private StationRefreshResult refreshStationInfoByDirection(String routeName, Integer dir) {
        Map<String, StationInfo> stationInfoMap = new LinkedHashMap<>();
        List<RouteStationInfo> routeStationInfoList = new ArrayList<>();

        for (MacauWebLanguage language : MacauWebLanguage.values()) {
            StationRefreshResult stationRefreshResult = buildStationRefreshResult(routeName, dir, language.getValue());
            log.debug("Fetched route station data, routeName={}, dir={}, lang={}, stationCount={}, routeStationCount={}",
                    routeName, dir, language.getValue(), stationRefreshResult.getStationCount(),
                    stationRefreshResult.getRouteStationCount());
            for (StationInfo station : stationRefreshResult.getStationList()) {
                if (!StringUtils.hasText(station.getStaCode())) {
                    log.warn("Skip station with empty staCode, routeName={}, dir={}, lang={}, staName={}",
                            routeName, dir, language.getValue(), station.getStaName());
                    continue;
                }
                StationInfo target = stationInfoMap.computeIfAbsent(station.getStaCode(), staCode -> {
                    StationInfo stationInfo = new StationInfo();
                    stationInfo.setStaCode(staCode);
                    return stationInfo;
                });
                mergeStationInfo(target, station, language);
            }
            if (language == MacauWebLanguage.ZH_TW) {
                routeStationInfoList.addAll(stationRefreshResult.getRouteStationList());
            }
        }

        List<StationInfo> stationInfoList = new ArrayList<>(stationInfoMap.values());
        for (StationInfo station : stationInfoList) {
            upsertStationInfo(station);
        }
        upsertRouteStationInfo(routeStationInfoList);

        log.debug("Finished refreshing station info by direction, routeName={}, dir={}, stationCount={}, routeStationCount={}",
                routeName, dir, stationInfoList.size(), routeStationInfoList.size());
        return buildStationRefreshResult(stationInfoList, routeStationInfoList);
    }

    /**
     * 刷新指定线路单语言站点明细，已有记录按 staCode 匹配后更新。
     */
    @Transactional
    public StationRefreshResult refreshStationInfo(String routeName, Integer dir, String lang) {
        if (!StringUtils.hasText(routeName)) {
            return refreshStationInfo();
        }
        if (!StringUtils.hasText(lang)) {
            return refreshStationInfo(routeName, dir);
        }
        MacauWebLanguage language = MacauWebLanguage.fromValue(lang);
        if (dir == null) {
            RouteInfo route = routeService.getRouteInfoByRouteName(routeName);
            List<Integer> dirs = resolveRefreshDirs(route);
            log.debug("Resolved station refresh directions for single language, routeName={}, routeCode={}, routeDirection={}, refreshDirs={}, lang={}",
                    route.getRouteName(), route.getRouteCode(), route.getDirection(), dirs, language.getValue());
            return refreshStationInfo(route.getRouteName(), dirs, language);
        }
        log.debug("Refresh station info by explicit direction and language, routeName={}, dir={}, lang={}",
                routeName, dir, language.getValue());
        return refreshStationInfoByDirection(routeName, dir, language, true);
    }

    private StationRefreshResult refreshStationInfo(String routeName, List<Integer> dirs, MacauWebLanguage language) {
        List<StationInfo> stationInfoList = new ArrayList<>();
        List<RouteStationInfo> routeStationInfoList = new ArrayList<>();
        for (Integer dir : dirs) {
            StationRefreshResult stationRefreshResult = refreshStationInfoByDirection(routeName, dir, language, true);
            stationInfoList.addAll(stationRefreshResult.getStationList());
            routeStationInfoList.addAll(stationRefreshResult.getRouteStationList());
        }
        return buildStationRefreshResult(stationInfoList, routeStationInfoList);
    }

    private StationRefreshResult refreshStationInfoByDirection(String routeName, Integer dir, MacauWebLanguage language, boolean refreshRouteStation) {
        StationRefreshResult stationRefreshResult = buildStationRefreshResult(routeName, dir, language.getValue());
        log.debug("Fetched route station data, routeName={}, dir={}, lang={}, stationCount={}, routeStationCount={}, refreshRouteStation={}",
                routeName, dir, language.getValue(), stationRefreshResult.getStationCount(),
                stationRefreshResult.getRouteStationCount(), refreshRouteStation);
        for (StationInfo station : stationRefreshResult.getStationList()) {
            setStationName(station, language);
            upsertStationInfo(station);
        }
        if (refreshRouteStation) {
            upsertRouteStationInfo(stationRefreshResult.getRouteStationList());
        }
        return stationRefreshResult;
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

    /**
     * 从线路明细响应中取出数据，并校验接口返回是否有效。
     */
    private RouteResponse.RouteData getRouteData(RouteResponse response, String routeName, Integer dir, String lang) {
        if (response == null || response.getData() == null || CollectionUtils.isEmpty(response.getData().getRouteInfo())) {
            log.warn("getRouteData returned empty routeInfo, routeName={}, dir={}, lang={}, responseDataExists={}",
                    routeName, dir, lang, response != null && response.getData() != null);
            throw new IllegalStateException("getRouteData returned empty routeInfo, routeName="
                    + routeName + ", dir=" + dir + ", lang=" + lang);
        }
        return response.getData();
    }

    /**
     * 根据线路明细响应顺序构建线路站点关系。
     */
    private List<RouteStationInfo> buildRouteStationInfo(RouteResponse.RouteData data, Integer requestDir) {
        Integer direction = data.getDirection() == null ? requestDir : data.getDirection();
        List<RouteStationInfo> routeStationInfoList = new ArrayList<>();
        for (int i = 0; i < data.getRouteInfo().size(); i++) {
            StationInfo station = data.getRouteInfo().get(i);
            if (!StringUtils.hasText(station.getStaCode())) {
                continue;
            }

            RouteStationInfo routeStationInfo = new RouteStationInfo();
            routeStationInfo.setRouteCode(data.getRouteCode());
            routeStationInfo.setStaCode(station.getStaCode());
            routeStationInfo.setDirection(direction);
            routeStationInfo.setStaIndex(i + 1);
            routeStationInfoList.add(routeStationInfo);
        }
        return routeStationInfoList;
    }

    private StationRefreshResult buildStationRefreshResult(List<StationInfo> stationInfoList,
                                                           List<RouteStationInfo> routeStationInfoList) {
        StationRefreshResult result = new StationRefreshResult();
        result.setStationCount(stationInfoList.size());
        result.setRouteStationCount(routeStationInfoList.size());
        result.setStationList(stationInfoList);
        result.setRouteStationList(routeStationInfoList);
        return result;
    }

    private void upsertStationInfo(StationInfo station) {
        if (!StringUtils.hasText(station.getStaCode())) {
            return;
        }
        StationInfo existing = stationInfoMapper.selectById(station.getStaCode());
        if (existing == null) {
            stationInfoMapper.insert(station);
        } else {
            stationInfoMapper.updateById(station);
        }
    }

    private void upsertRouteStationInfo(List<RouteStationInfo> routeStationInfoList) {
        Set<RouteStationKey> duplicatedKeys = findDuplicatedRouteStationKeys(routeStationInfoList);
        for (RouteStationInfo routeStation : routeStationInfoList) {
            upsertRouteStationInfo(routeStation, duplicatedKeys.contains(RouteStationKey.from(routeStation)));
        }
    }

    private void upsertRouteStationInfo(RouteStationInfo routeStation, boolean duplicateStationInSameDirection) {
        if (!StringUtils.hasText(routeStation.getRouteCode())
                || !StringUtils.hasText(routeStation.getStaCode())
                || routeStation.getDirection() == null) {
            return;
        }
        RouteStationInfo existing = selectExistingRouteStationInfo(routeStation, duplicateStationInSameDirection);
        if (existing == null) {
            routeStationInfoMapper.insert(routeStation);
        } else {
            routeStation.setId(existing.getId());
            routeStationInfoMapper.updateById(routeStation);
        }
    }

    private RouteStationInfo selectExistingRouteStationInfo(RouteStationInfo routeStation,
                                                            boolean duplicateStationInSameDirection) {
        var query = Wrappers.<RouteStationInfo>lambdaQuery()
                .eq(RouteStationInfo::getRouteCode, routeStation.getRouteCode())
                .eq(RouteStationInfo::getStaCode, routeStation.getStaCode())
                .eq(RouteStationInfo::getDirection, routeStation.getDirection());
        if (duplicateStationInSameDirection) {
            query.eq(RouteStationInfo::getStaIndex, routeStation.getStaIndex());
        }
        return routeStationInfoMapper.selectOne(query);
    }

    private Set<RouteStationKey> findDuplicatedRouteStationKeys(List<RouteStationInfo> routeStationInfoList) {
        Map<RouteStationKey, Integer> keyCountMap = new HashMap<>();
        for (RouteStationInfo routeStation : routeStationInfoList) {
            if (!StringUtils.hasText(routeStation.getRouteCode())
                    || !StringUtils.hasText(routeStation.getStaCode())
                    || routeStation.getDirection() == null) {
                continue;
            }
            RouteStationKey key = RouteStationKey.from(routeStation);
            keyCountMap.merge(key, 1, Integer::sum);
        }

        Set<RouteStationKey> duplicatedKeys = new HashSet<>();
        for (Map.Entry<RouteStationKey, Integer> entry : keyCountMap.entrySet()) {
            if (entry.getValue() > 1) {
                duplicatedKeys.add(entry.getKey());
            }
        }
        return duplicatedKeys;
    }

    /**
     * 按当前语言写入对应的站点名称和车道名称字段。
     */
    private void setStationName(StationInfo station, MacauWebLanguage language) {
        switch (language) {
            case ZH_CN -> {
                station.setStaZhCn(station.getStaName());
                station.setLaneZhCn(station.getLaneName());
            }
            case ZH_TW -> {
                station.setStaZhTw(station.getStaName());
                station.setLaneZhTw(station.getLaneName());
            }
            case EN -> {
                station.setStaEn(station.getStaName());
                station.setLaneEn(station.getLaneName());
            }
            case PT -> {
                station.setStaPt(station.getStaName());
                station.setLanePt(station.getLaneName());
            }
        }
    }

    /**
     * 将单次语言响应合并到最终站点对象。
     */
    private void mergeStationInfo(StationInfo target, StationInfo source, MacauWebLanguage language) {
        target.setSuspendState(source.getSuspendState());
        target.setMetro(source.getMetro());
        target.setBusStopCode(source.getBusStopCode());
        target.setStaName(source.getStaName());
        target.setLaneName(source.getLaneName());
        setStationName(target, language);
    }

    private record RouteStationKey(String routeCode, String staCode, Integer direction) {
        private static RouteStationKey from(RouteStationInfo routeStation) {
            return new RouteStationKey(routeStation.getRouteCode(), routeStation.getStaCode(), routeStation.getDirection());
        }
    }
}
