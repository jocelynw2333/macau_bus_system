package com.albez.bus.domain.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TeamHealthResult {
    private boolean ok;
    private String source;
    private Long snapshots;
}
