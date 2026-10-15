package com.albez.bus.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("station_info")
public class StationInfo {
    @TableId(value = "sta_code", type = IdType.INPUT)
    private String staCode;

    @TableField(exist = false)
    private String staName;

    @TableField(exist = false)
    private String laneName;

    private String staZhCn;
    private String laneZhCn;
    private String staZhTw;
    private String laneZhTw;
    private String staEn;
    private String laneEn;
    private String staPt;
    private String lanePt;

    private Integer suspendState;
    private String metro;
    private String busStopCode;

    public String getBusStopCode() {
        return busStopCode;
    }

    public void setBusStopCode(String busStopCode) {
        this.busStopCode = busStopCode;
    }

    /**
     * 兼容接口返回的 busstopcode 字段。
     */
    public void setBusstopcode(String busstopcode) {
        this.busStopCode = busstopcode;
    }
}
