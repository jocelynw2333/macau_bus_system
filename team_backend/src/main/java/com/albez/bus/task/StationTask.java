package com.albez.bus.task;

import com.albez.bus.service.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "bus.collector", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class StationTask {
    private final StationService stationService;

    @Scheduled(cron = "0 0 0,12 * * ?")
    public void refreshStationInfo() {
        stationService.refreshStationInfo();
    }
}
