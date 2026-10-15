package com.albez.bus.task;

import com.albez.bus.service.RouteAndCompanyService;
import com.albez.bus.service.StationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "bus.refresh-on-startup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StartupRefreshTask {
    private final RouteAndCompanyService routeAndCompanyService;
    private final StationService stationService;
    private final Executor refreshTaskExecutor;

    public StartupRefreshTask(RouteAndCompanyService routeAndCompanyService,
                              StationService stationService,
                              @Qualifier("refreshTaskExecutor") Executor refreshTaskExecutor) {
        this.routeAndCompanyService = routeAndCompanyService;
        this.stationService = stationService;
        this.refreshTaskExecutor = refreshTaskExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        log.info("Submit startup refresh tasks for 12-hour persisted data");
        CompletableFuture.allOf(
                runStartupRefresh("route and company info", () -> routeAndCompanyService.refreshRouteAndCompanyInfo()),
                runStartupRefresh("station info", () -> stationService.refreshStationInfo())
        ).whenComplete((ignored, throwable) ->
                log.info("Finished startup refresh attempt for 12-hour persisted data"));
    }

    private CompletableFuture<Void> runStartupRefresh(String taskName, Runnable refreshAction) {
        return CompletableFuture.runAsync(() -> {
            log.info("Start refreshing {} on startup", taskName);
            try {
                refreshAction.run();
                log.info("Finished refreshing {} on startup", taskName);
            } catch (Exception e) {
                log.error("Failed to refresh {} on startup", taskName, e);
            }
        }, refreshTaskExecutor);
    }
}
