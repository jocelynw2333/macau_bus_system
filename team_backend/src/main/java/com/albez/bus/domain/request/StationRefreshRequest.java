package com.albez.bus.domain.request;

import lombok.Data;

@Data
public class StationRefreshRequest {
    private String routeName;
    private Integer dir;
    private String lang;
}
