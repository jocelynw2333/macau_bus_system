package com.albez.bus.domain.response;

import com.albez.bus.model.CompanyInfo;
import com.albez.bus.model.RouteInfo;
import lombok.Data;

import java.util.List;

@Data
public class RouteAndCompanyResponse {
    private RouteData data;
    private String header;


    @Data
    public static class RouteData {
        private List<CompanyInfo> companyList;
        private List<RouteInfo> routeList;
    }
}
