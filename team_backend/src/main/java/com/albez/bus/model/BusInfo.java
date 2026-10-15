package com.albez.bus.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("bus_info")
public class BusInfo {
    @TableId(value = "bus_code", type = IdType.INPUT)
    private String busCode;

    private Integer busType;
    private String busPlate;
    private Integer isFacilities;
}
