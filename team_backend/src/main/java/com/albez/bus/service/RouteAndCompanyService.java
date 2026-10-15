package com.albez.bus.service;

import com.albez.bus.domain.response.RouteAndCompanyRefreshResult;
import com.albez.bus.model.CompanyInfo;
import com.albez.bus.model.RouteInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Service
@Slf4j
public class RouteAndCompanyService {
    private final CompanyService companyService;
    private final RouteService routeService;
    private final Executor refreshTaskExecutor;

    public RouteAndCompanyService(CompanyService companyService,
                                  RouteService routeService,
                                  @Qualifier("refreshTaskExecutor") Executor refreshTaskExecutor) {
        this.companyService = companyService;
        this.routeService = routeService;
        this.refreshTaskExecutor = refreshTaskExecutor;
    }

    /**
     * 同步刷新公司和线路信息。
     */
    public RouteAndCompanyRefreshResult refreshRouteAndCompanyInfo() {
        CompletableFuture<List<CompanyInfo>> companyFuture = refreshAsync("company info", companyService::refreshCompanyInfo);
        CompletableFuture<List<RouteInfo>> routeFuture = refreshAsync("route info", routeService::refreshRouteInfo);

        try {
            CompletableFuture.allOf(companyFuture, routeFuture).join();
        } catch (CompletionException e) {
            throw unwrapCompletionException(e);
        }

        List<CompanyInfo> companyInfoList = companyFuture.join();
        List<RouteInfo> routeInfoList = routeFuture.join();

        RouteAndCompanyRefreshResult result = new RouteAndCompanyRefreshResult();
        result.setCompanyCount(companyInfoList.size());
        result.setRouteCount(routeInfoList.size());
        result.setStationCount(0);
        result.setRouteStationCount(0);
        result.setCompanyList(companyInfoList);
        result.setRouteList(routeInfoList);
        result.setStationList(List.of());
        result.setRouteStationList(List.of());
        return result;
    }

    private <T> CompletableFuture<T> refreshAsync(String taskName, Supplier<T> refreshAction) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Start refreshing {}", taskName);
            try {
                T result = refreshAction.get();
                log.info("Finished refreshing {}", taskName);
                return result;
            } catch (Exception e) {
                log.error("Failed to refresh {}", taskName, e);
                throw e;
            }
        }, refreshTaskExecutor);
    }

    private RuntimeException unwrapCompletionException(CompletionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return e;
    }
}
