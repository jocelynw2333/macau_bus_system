package com.albez.bus.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("route_station_info")
public class RouteStationInfo {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String routeCode;
    private String staCode;
    private Integer direction;
    private Integer staIndex;
}
