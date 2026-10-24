package com.albez.bus.service;

import com.albez.bus.domain.response.RouteSnapshotResult;
import com.albez.bus.domain.response.TeamHealthResult;
import com.albez.bus.mapper.BusObservationMapper;
import com.albez.bus.mapper.CollectionSnapshotMapper;
import com.albez.bus.model.CollectionSnapshot;
import com.albez.bus.model.BusObservation;
import com.albez.bus.model.BusStationEvent;
import com.albez.bus.model.SnapshotStation;
import com.albez.bus.model.StationInfo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yulichang.wrapper.MPJLambdaWrapper;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

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
                collectionSnapshotMapper.selectCount(Wrappers.<CollectionSnapshot>lambdaQuery()
                        .eq(CollectionSnapshot::getRouteName, routeName)
                        .eq(CollectionSnapshot::getDirection, 0)));
    }

    @Transactional(readOnly = true)
    public RouteSnapshotResult getLatestRouteSnapshot(String routeName, Integer direction) {
        CollectionSnapshot snapshot = collectionSnapshotMapper.selectOne(Wrappers.<CollectionSnapshot>lambdaQuery()
                .eq(CollectionSnapshot::getRouteName, routeName)
                .eq(CollectionSnapshot::getDirection, direction)
                .orderByDesc(CollectionSnapshot::getCollectedAt, CollectionSnapshot::getId)
                .last("LIMIT 1"));
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
        result.setBuses(selectBuses(snapshot));
        result.setStations(selectStations(snapshot.getId()));
        return result;
    }

    private List<BusObservation> selectBuses(CollectionSnapshot snapshot) {
        var query = new MPJLambdaWrapper<>(BusObservation.class)
                .select(BusObservation::getStaCode, BusObservation::getBusCode,
                        BusObservation::getBusPlate, BusObservation::getBusType,
                        BusObservation::getStatus, BusObservation::getPassengerFlow,
                        BusObservation::getSpeed, BusObservation::getLatitude, BusObservation::getLongitude)
                .selectSub(BusStationEvent.class, event -> event
                                .selectMax(BusStationEvent::getObservedAt)
                                .eq(BusStationEvent::getRouteName, BusObservation::getRouteName)
                                .eq(BusStationEvent::getDirection, BusObservation::getDirection)
                                .eq(BusStationEvent::getStaCode, BusObservation::getStaCode)
                                .eq(BusStationEvent::getBusCode, BusObservation::getBusCode)
                                .eq(BusStationEvent::getEventType, "departure")
                                .between(BusStationEvent::getObservedAt,
                                        snapshot.getCollectedAt().minusMinutes(30), snapshot.getCollectedAt()),
                        BusObservation::getDepartedAt)
                .eq(BusObservation::getSnapshotId, snapshot.getId())
                .orderByAsc(BusObservation::getStaCode, BusObservation::getBusCode);
        return busObservationMapper.selectJoinList(BusObservation.class, query);
    }

    private List<SnapshotStation> selectStations(Long snapshotId) {
        var query = new MPJLambdaWrapper<>(CollectionSnapshot.class)
                .selectAs("jt.sta_code", SnapshotStation::getCode)
                .select("jt.sta_index AS `index`")
                .selectFunc("COALESCE(%s, %s, jt.sta_code)",
                        args -> args.accept(StationInfo::getStaZhTw, StationInfo::getStaZhCn),
                        SnapshotStation::getName)
                .innerJoin("""
                        JSON_TABLE(t.raw_json, '$.response.data.routeInfo[*]'
                          COLUMNS (sta_index FOR ORDINALITY, sta_code VARCHAR(20) PATH '$.staCode')) jt ON TRUE
                        """)
                .leftJoin(StationInfo.class, "si", on -> on.apply("si.sta_code = jt.sta_code"))
                .eq(CollectionSnapshot::getId, snapshotId)
                .orderByAsc("jt.sta_index");
        return collectionSnapshotMapper.selectJoinList(SnapshotStation.class, query);
    }
}
