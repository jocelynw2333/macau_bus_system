package com.albez.bus.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bus_station_event")
public class BusStationEvent {
    private String routeName;
    private Integer direction;
    private String staCode;
    private String busCode;
    private String eventType;
    private LocalDateTime observedAt;
}
