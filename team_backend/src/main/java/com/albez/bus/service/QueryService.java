package com.albez.bus.service;

import com.albez.bus.domain.response.RouteSnapshotResult;
import com.albez.bus.domain.response.TeamHealthResult;
import com.albez.bus.mapper.BusObservationMapper;
import com.albez.bus.mapper.CollectionSnapshotMapper;
import com.albez.bus.model.CollectionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class QueryService {
    private final CollectionSnapshotMapper collectionSnapshotMapper;
    private final BusObservationMapper busObservationMapper;

    public TeamHealthResult getHealth() {
        return getHealth("51A");
    }

    public TeamHealthResult getHealth(String routeName) {
        return new TeamHealthResult(true,
                "MacaoBusCrawler",
                collectionSnapshotMapper.countByRoute(routeName, 0));
    }

    @Transactional(readOnly = true)
    public RouteSnapshotResult getLatestRouteSnapshot(String routeName, Integer direction) {
        CollectionSnapshot snapshot = collectionSnapshotMapper.selectLatestByRoute(routeName, direction);
        if (snapshot == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "还没有 " + routeName + " 采集数据");
        }
        RouteSnapshotResult result = new RouteSnapshotResult();
        result.setId(snapshot.getId());
        result.setCollectedAt(snapshot.getCollectedAt().toString());
        result.setRouteName(snapshot.getRouteName());
        result.setDirection(snapshot.getDirection());
        result.setStationCount(snapshot.getStationCount());
        result.setBusCount(snapshot.getBusCount());
        result.setBuses(busObservationMapper.selectBySnapshotId(snapshot.getId(),
                snapshot.getCollectedAt().minusMinutes(30), snapshot.getCollectedAt()));
        result.setStations(collectionSnapshotMapper.selectStationsBySnapshotId(snapshot.getId()));
        return result;
    }
}
