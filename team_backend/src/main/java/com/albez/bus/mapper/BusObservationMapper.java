package com.albez.bus.mapper;

import com.albez.bus.model.BusObservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BusObservationMapper {
    @Select("""
            SELECT o.sta_code, o.bus_code, o.bus_plate, o.bus_type,
                   o.status, o.passenger_flow, o.speed, o.latitude, o.longitude,
                   (SELECT e.observed_at FROM bus_station_event e
                    WHERE e.route_name = o.route_name AND e.direction = o.direction
                      AND e.sta_code = o.sta_code AND e.bus_code = o.bus_code
                      AND e.event_type = 'departure'
                      AND e.observed_at BETWEEN #{windowStart} AND #{collectedAt}
                    ORDER BY e.observed_at DESC LIMIT 1) AS departed_at
            FROM bus_observation o WHERE o.snapshot_id = #{snapshotId}
            ORDER BY o.sta_code, o.bus_code
            """)
    List<BusObservation> selectBySnapshotId(@Param("snapshotId") Long snapshotId,
                                          @Param("windowStart") LocalDateTime windowStart,
                                          @Param("collectedAt") LocalDateTime collectedAt);
}
