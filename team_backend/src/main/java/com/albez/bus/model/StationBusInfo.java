package com.albez.bus.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("station_bus_info")
public class StationBusInfo {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String staCode;
    private String busCode;
    private Integer status;
    private Integer passengerFlow;
    private LocalDateTime time;
}
