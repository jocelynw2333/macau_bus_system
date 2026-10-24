package com.albez.bus.service;

import com.albez.bus.domain.response.RouteSnapshotResult;
import com.albez.bus.mapper.BusObservationMapper;
import com.albez.bus.mapper.CollectionSnapshotMapper;
import com.albez.bus.model.BusObservation;
import com.albez.bus.model.CollectionSnapshot;
import com.albez.bus.model.SnapshotStation;
import org.junit.jupiter.api.Test;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.albez.bus.model.BusStationEvent;
import com.albez.bus.model.StationInfo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryServiceTests {
    @BeforeAll
    static void initializeTableMetadata() {
        var configuration = new MybatisConfiguration();
        for (Class<?> model : List.of(CollectionSnapshot.class, BusObservation.class,
                BusStationEvent.class, StationInfo.class)) {
            var assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace(model.getName());
            TableInfoHelper.initTableInfo(assistant, model);
        }
    }

    private final CollectionSnapshotMapper snapshots = mock(CollectionSnapshotMapper.class);
    private final BusObservationMapper observations = mock(BusObservationMapper.class);
    private final QueryService service = new QueryService(snapshots, observations);

    @Test
    void healthCountsOnly51ADirectionZero() {
        when(snapshots.selectCount(any())).thenReturn(12L);
        var result = service.getHealth();
        assertTrue(result.isOk());
        assertEquals("MacaoBusCrawler", result.getSource());
        assertEquals(12L, result.getSnapshots());
        verify(snapshots).selectCount(any());
    }

    @Test
    void missingSnapshotReturnsNotFoundWithoutQueryingDetails() {
        var error = assertThrows(ResponseStatusException.class,
                () -> service.getLatestRouteSnapshot("51A", 0));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(observations);
        verify(snapshots, never()).selectJoinList(eq(SnapshotStation.class), any());
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
        when(snapshots.selectOne(any())).thenReturn(snapshot);
        when(observations.selectJoinList(eq(BusObservation.class), any()))
                .thenReturn(List.of(bus));
        when(snapshots.selectJoinList(eq(SnapshotStation.class), any())).thenReturn(List.of(station));

        RouteSnapshotResult result = service.getLatestRouteSnapshot("51A", 0);
        assertEquals(List.of(bus), result.getBuses());
        assertEquals(List.of(station), result.getStations());
        verify(observations).selectJoinList(eq(BusObservation.class), argThat(wrapper -> {
            var query = (MPJLambdaWrapper<?>) wrapper;
            assertTrue(query.getSqlSegment().contains("t.snapshot_id ="));
            assertTrue(query.getSqlSegment().contains("ORDER BY t.sta_code ASC,t.bus_code ASC"));
            String select = query.getSqlSelect();
            assertTrue(select.contains("MAX(st.observed_at)"));
            assertTrue(select.contains("st.route_name = t.route_name"));
            assertTrue(select.contains("st.direction = t.direction"));
            assertTrue(select.contains("st.sta_code = t.sta_code"));
            assertTrue(select.contains("st.bus_code = t.bus_code"));
            assertTrue(select.contains("st.event_type ="));
            assertTrue(select.contains("st.observed_at BETWEEN"));
            assertTrue(query.getParamNameValuePairs().containsValue("departure"));
            assertTrue(query.getParamNameValuePairs().containsValue(7L));
            assertTrue(query.getParamNameValuePairs().containsValue(collectedAt.minusMinutes(30)));
            assertTrue(query.getParamNameValuePairs().containsValue(collectedAt));
            return true;
        }));
        verify(snapshots).selectJoinList(eq(SnapshotStation.class), argThat(wrapper -> {
            var query = (MPJLambdaWrapper<?>) wrapper;
            assertTrue(query.getFrom().contains("JSON_TABLE(t.raw_json"));
            assertTrue(query.getSqlSelect().contains("COALESCE(si.sta_zh_tw, si.sta_zh_cn, jt.sta_code)"));
            assertTrue(query.getSqlSegment().contains("ORDER BY jt.sta_index ASC"));
            assertTrue(query.getParamNameValuePairs().containsValue(7L));
            return true;
        }));
        JsonMapper jsonMapper = JsonMapper.builder().build();
        var json = jsonMapper.readTree(jsonMapper.writeValueAsString(result));
        assertEquals("2026-10-24T12:00", json.get("collected_at").asText());
        assertEquals("51A", json.get("route_name").asText());
        assertEquals(1, json.get("station_count").asInt());
        var busJson = json.get("buses").get(0);
        assertEquals("M1", busJson.get("sta_code").asText());
        assertEquals("1", busJson.get("bus_type").asText());
        assertEquals("2026-10-24T11:58", busJson.get("departed_at").asText());
        assertFalse(busJson.has("snapshotId"));
        assertFalse(busJson.has("routeName"));
        assertFalse(busJson.has("direction"));
        assertFalse(busJson.has("departedAt"));
        assertFalse(busJson.has("departedAtIso"));
        bus.setStatus("1");
        assertTrue(jsonMapper.readTree(jsonMapper.writeValueAsString(bus)).get("departed_at").isNull());
        bus.setStatus("0");
        bus.setDepartedAt(null);
        assertTrue(jsonMapper.readTree(jsonMapper.writeValueAsString(bus)).get("departed_at").isNull());
    }
}
