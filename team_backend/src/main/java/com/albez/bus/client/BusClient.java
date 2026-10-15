package com.albez.bus.client;

import com.albez.bus.config.FeignDecoderConfig;
import com.albez.bus.domain.enums.MacauWebLanguage;
import com.albez.bus.domain.response.BusResponse;
import com.albez.bus.domain.response.RouteAndCompanyResponse;
import com.albez.bus.domain.response.RouteResponse;
import com.albez.bus.domain.response.StationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "busClient", url = "${macauWeb.url}", configuration = FeignDecoderConfig.class)
public interface BusClient {
    /**
     * 获取站点实时交通状态。
     */
    @GetMapping
    StationResponse getStationTraffic();

    /**
     * 根据表单语言参数获取路线和公司列表。
     */
    @PostMapping(value = "/getRouteAndCompanyList.html", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    RouteAndCompanyResponse getRouteAndCompanyList(@RequestParam("lang") String lang);

    /**
     * 默认使用繁体中文获取路线和公司列表。
     */
    default RouteAndCompanyResponse getRouteAndCompanyList() {
        return getRouteAndCompanyList(MacauWebLanguage.ZH_TW);
    }

    /**
     * 根据指定语言获取路线和公司列表。
     */
    default RouteAndCompanyResponse getRouteAndCompanyList(MacauWebLanguage language) {
        return getRouteAndCompanyList(language.getValue());
    }

    /**
     * 根据线路名、方向和语言获取线路站点明细。
     */
    @PostMapping(value = "/getRouteData.html", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    RouteResponse getRouteData(@RequestParam("routeName") String routeName,
                               @RequestParam("dir") Integer dir,
                               @RequestParam("lang") String lang);

    /**
     * 默认使用繁体中文获取线路站点明细。
     */
    default RouteResponse getRouteData(String routeName, Integer dir) {
        return getRouteData(routeName, dir, MacauWebLanguage.ZH_TW.getValue());
    }

    @PostMapping(value = "/routestation/bus", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    BusResponse getBusData(@RequestParam("routeName") String routeName,
                           @RequestParam("dir") Integer dir);
}
