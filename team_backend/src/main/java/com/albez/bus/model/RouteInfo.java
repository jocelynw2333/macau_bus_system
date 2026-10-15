package com.albez.bus.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("route_info")
public class RouteInfo {
    @TableId(value = "route_code", type = IdType.INPUT)
    private String routeCode;

    private String color;
    //路线变更：0否，1是
    private Integer routeChange;
    //方向：0双向，2单向
    private Integer direction;
    private String routeName;
}
