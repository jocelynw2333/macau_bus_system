package com.albez.bus.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only endpoints over the same MySQL history written by the Python importer. */
@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
public class TeamReadController {
    private final JdbcTemplate jdbc;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Long snapshots = jdbc.queryForObject(
                "SELECT COUNT(*) FROM collection_snapshot WHERE route_name='51A' AND direction=0", Long.class);
        return Map.of("ok", true, "source", "MacaoBusCrawler", "snapshots", snapshots);
    }

    @GetMapping("/routes/51A/latest")
    public Map<String, Object> latest51A() {
        List<Map<String, Object>> snapshots = jdbc.query("""
                SELECT id, collected_at, route_name, direction, station_count, bus_count
                FROM collection_snapshot
                WHERE route_name='51A' AND direction=0
                ORDER BY collected_at DESC LIMIT 1
                """, (rs, row) -> {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("id", rs.getLong("id"));
            snapshot.put("collected_at", iso(rs.getTimestamp("collected_at")));
            snapshot.put("route_name", rs.getString("route_name"));
            snapshot.put("direction", rs.getInt("direction"));
            snapshot.put("station_count", rs.getInt("station_count"));
            snapshot.put("bus_count", rs.getInt("bus_count"));
            return snapshot;
        });
        if (snapshots.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "还没有 51A 采集数据");
        }
        Map<String, Object> snapshot = snapshots.getFirst();
        long snapshotId = ((Number) snapshot.get("id")).longValue();
        LocalDateTime collectedAt = LocalDateTime.parse((String) snapshot.get("collected_at"));

        snapshot.put("buses", jdbc.query("""
                SELECT o.sta_code, o.bus_code, o.bus_plate, o.bus_type,
                       o.status, o.passenger_flow, o.speed, o.latitude, o.longitude,
                       (SELECT e.observed_at FROM bus_station_event e
                        WHERE e.route_name=o.route_name AND e.direction=o.direction
                          AND e.sta_code=o.sta_code AND e.bus_code=o.bus_code
                          AND e.event_type='departure'
                          AND e.observed_at BETWEEN ? AND ?
                        ORDER BY e.observed_at DESC LIMIT 1) AS departed_at
                FROM bus_observation o WHERE o.snapshot_id=?
                ORDER BY o.sta_code, o.bus_code
                """, (rs, row) -> {
            Map<String, Object> bus = new LinkedHashMap<>();
            for (String key : List.of("sta_code", "bus_code", "bus_plate", "bus_type",
                    "status", "passenger_flow")) {
                bus.put(key, rs.getString(key));
            }
            for (String key : List.of("speed", "latitude", "longitude")) {
                bus.put(key, rs.getBigDecimal(key));
            }
            bus.put("departed_at", "0".equals(bus.get("status"))
                    ? iso(rs.getTimestamp("departed_at")) : null);
            return bus;
        }, Timestamp.valueOf(collectedAt.minusMinutes(30)), Timestamp.valueOf(collectedAt), snapshotId));

        snapshot.put("stations", jdbc.query("""
                SELECT jt.sta_code, COALESCE(si.sta_zh_tw, si.sta_zh_cn, jt.sta_code) AS sta_name,
                       jt.sta_index
                FROM collection_snapshot s
                JOIN JSON_TABLE(s.raw_json, '$.response.data.routeInfo[*]'
                  COLUMNS (sta_index FOR ORDINALITY, sta_code VARCHAR(20) PATH '$.staCode')) jt
                LEFT JOIN station_info si ON si.sta_code=jt.sta_code
                WHERE s.id=? ORDER BY jt.sta_index
                """, (rs, row) -> Map.of(
                "code", rs.getString("sta_code"),
                "name", rs.getString("sta_name"),
                "index", rs.getInt("sta_index")), snapshotId));
        return snapshot;
    }

    private static String iso(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().toString();
    }
}
