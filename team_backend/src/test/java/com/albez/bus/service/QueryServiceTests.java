package com.albez.bus.service;

import com.albez.bus.domain.response.RouteSnapshotResult;
import com.albez.bus.mapper.BusObservationMapper;
import com.albez.bus.mapper.CollectionSnapshotMapper;
import com.albez.bus.model.BusObservation;
import com.albez.bus.model.CollectionSnapshot;
import com.albez.bus.model.SnapshotStation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryServiceTests {
    private final CollectionSnapshotMapper snapshots = mock(CollectionSnapshotMapper.class);
    private final BusObservationMapper observations = mock(BusObservationMapper.class);
    private final QueryService service = new QueryService(snapshots, observations);

    @Test
    void healthCountsOnly51ADirectionZero() {
        when(snapshots.countByRoute("51A", 0)).thenReturn(12L);
        var result = service.getHealth();
        assertTrue(result.isOk());
        assertEquals("MacaoBusCrawler", result.getSource());
        assertEquals(12L, result.getSnapshots());
        verify(snapshots).countByRoute("51A", 0);
    }

    @Test
    void missingSnapshotReturnsNotFoundWithoutQueryingDetails() {
        var error = assertThrows(ResponseStatusException.class,
                () -> service.getLatestRouteSnapshot("51A", 0));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(observations);
        verify(snapshots, never()).selectStationsBySnapshotId(any());
    }

    @Test
    void assemblesSnapshotWithThirtyMinuteWindowAndCompatibleJson() {
        LocalDateTime collectedAt = LocalDateTime.of(2026, 10, 24, 12, 0);
        CollectionSnapshot snapshot = new CollectionSnapshot();
        snapshot.setId(7L);
        snapshot.setCollectedAt(collectedAt);
        snapshot.setRouteName("51A");
        snapshot.setDirection(0);
        snapshot.setStationCount(1);
        snapshot.setBusCount(1);
        BusObservation bus = new BusObservation();
        bus.setStaCode("M1");
        bus.setBusCode("B1");
        bus.setBusType("1");
        bus.setStatus("0");
        bus.setDepartedAt(collectedAt.minusMinutes(2));
        SnapshotStation station = new SnapshotStation();
        station.setCode("M1");
        station.setName("Station");
        station.setIndex(1);
        when(snapshots.selectLatestByRoute("51A", 0)).thenReturn(snapshot);
        when(observations.selectBySnapshotId(7L, collectedAt.minusMinutes(30), collectedAt))
                .thenReturn(List.of(bus));
        when(snapshots.selectStationsBySnapshotId(7L)).thenReturn(List.of(station));

        RouteSnapshotResult result = service.getLatestRouteSnapshot("51A", 0);
        assertEquals(List.of(bus), result.getBuses());
        assertEquals(List.of(station), result.getStations());
        verify(observations).selectBySnapshotId(7L, collectedAt.minusMinutes(30), collectedAt);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        var json = jsonMapper.readTree(jsonMapper.writeValueAsString(result));
        assertEquals("2026-10-24T12:00", json.get("collected_at").asText());
        assertEquals("51A", json.get("route_name").asText());
        assertEquals(1, json.get("station_count").asInt());
        var busJson = json.get("buses").get(0);
        assertEquals("M1", busJson.get("sta_code").asText());
        assertEquals("1", busJson.get("bus_type").asText());
        assertEquals("2026-10-24T11:58", busJson.get("departed_at").asText());
        assertFalse(busJson.has("departedAt"));
        assertFalse(busJson.has("departedAtIso"));
        bus.setStatus("1");
        assertTrue(jsonMapper.readTree(jsonMapper.writeValueAsString(bus)).get("departed_at").isNull());
        bus.setStatus("0");
        bus.setDepartedAt(null);
        assertTrue(jsonMapper.readTree(jsonMapper.writeValueAsString(bus)).get("departed_at").isNull());
    }
}
