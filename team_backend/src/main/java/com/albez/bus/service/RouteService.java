package com.albez.bus.service;

import com.albez.bus.client.BusClient;
import com.albez.bus.domain.response.RouteAndCompanyResponse;
import com.albez.bus.mapper.RouteInfoMapper;
import com.albez.bus.model.RouteInfo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RouteService {
    private final BusClient busClient;
    private final RouteInfoMapper routeInfoMapper;

    /**
     * 获取线路列表，线路名称不受语言参数影响。
     */
    public List<RouteInfo> buildRouteInfo() {
        RouteAndCompanyResponse response = busClient.getRouteAndCompanyList();
        List<RouteInfo> routeInfoList = getRouteList(response);
        for (RouteInfo route : routeInfoList) {
            route.setRouteCode(buildRouteCode(route.getRouteName()));
        }
        return routeInfoList;
    }

    public List<RouteInfo> listRouteInfo() {
        return routeInfoMapper.selectList(null);
    }

    public RouteInfo getRouteInfoByRouteName(String routeName) {
        if (!StringUtils.hasText(routeName)) {
            throw new IllegalArgumentException("Route name must not be blank");
        }
        RouteInfo routeInfo = routeInfoMapper.selectOne(
                Wrappers.<RouteInfo>lambdaQuery()
                        .eq(RouteInfo::getRouteName, routeName)
        );
        if (routeInfo == null) {
            throw new IllegalArgumentException("Route not found: " + routeName);
        }
        return routeInfo;
    }

    /**
     * 刷新线路信息，已有记录按 routeCode 匹配后更新。
     */
    @Transactional
    public List<RouteInfo> refreshRouteInfo() {
        List<RouteInfo> routeInfoList = buildRouteInfo();
        for (RouteInfo route : routeInfoList) {
            if (!StringUtils.hasText(route.getRouteCode())) {
                continue;
            }
            RouteInfo existing = routeInfoMapper.selectById(route.getRouteCode());
            if (existing == null) {
                routeInfoMapper.insert(route);
            } else {
                routeInfoMapper.updateById(route);
            }
        }
        return routeInfoList;
    }

    /**
     * 从响应中取出线路列表，并校验接口返回是否有效。
     */
    private List<RouteInfo> getRouteList(RouteAndCompanyResponse response) {
        if (response == null || response.getData() == null || CollectionUtils.isEmpty(response.getData().getRouteList())) {
            throw new IllegalStateException("getRouteAndCompanyList returned empty routeList");
        }
        return response.getData().getRouteList();
    }

    /**
     * 根据线路名生成线路编码，不足 5 位时前置补 0。
     */
    private String buildRouteCode(String routeName) {
        if (!StringUtils.hasText(routeName)) {
            return null;
        }
        return "0".repeat(Math.max(0, 5 - routeName.length())) + routeName;
    }
}
