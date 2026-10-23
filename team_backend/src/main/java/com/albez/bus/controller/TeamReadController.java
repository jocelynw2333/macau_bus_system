package com.albez.bus.controller;

import com.albez.bus.domain.response.TeamHealthResult;
import com.albez.bus.domain.response.RouteSnapshotResult;
import com.albez.bus.service.QueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
public class TeamReadController {
    private final QueryService queryService;

    @GetMapping("/health")
    public TeamHealthResult health(@RequestParam(name = "routeName", defaultValue = "51A") String routeName) {
        return queryService.getHealth(routeName);
    }

    @GetMapping({ "/routes/{routeName}/latest"})
    public RouteSnapshotResult latest(@PathVariable String routeName) {
        return queryService.getLatestRouteSnapshot(routeName == null ? "51A" : routeName, 0);
    }
}
