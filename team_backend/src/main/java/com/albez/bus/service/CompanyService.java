package com.albez.bus.service;

import com.albez.bus.client.BusClient;
import com.albez.bus.domain.enums.MacauWebLanguage;
import com.albez.bus.domain.response.RouteAndCompanyResponse;
import com.albez.bus.mapper.CompanyInfoMapper;
import com.albez.bus.model.CompanyInfo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompanyService {
    private final BusClient busClient;
    private final CompanyInfoMapper companyInfoMapper;

    /**
     * 请求所有语言版本，并按公司 color 合并成完整公司信息。
     */
    public List<CompanyInfo> buildCompleteCompanyInfo() {
        Map<String, CompanyInfo> companyInfoMap = new LinkedHashMap<>();

        for (MacauWebLanguage language : MacauWebLanguage.values()) {
            RouteAndCompanyResponse response = busClient.getRouteAndCompanyList(language);
            List<CompanyInfo> companyList = getCompanyList(response, language);

            for (CompanyInfo source : companyList) {
                if (!StringUtils.hasText(source.getColor())) {
                    continue;
                }
                CompanyInfo target = companyInfoMap.computeIfAbsent(source.getColor(), color -> {
                    CompanyInfo company = new CompanyInfo();
                    company.setColor(color);
                    return company;
                });
                setCompanyName(target, language, source.getName());
            }
        }

        return new ArrayList<>(companyInfoMap.values());
    }

    /**
     * 获取指定语言版本的公司信息，并把接口 name 写入对应语言字段。
     */
    public List<CompanyInfo> buildCompanyInfo(MacauWebLanguage language) {
        RouteAndCompanyResponse response = busClient.getRouteAndCompanyList(language);
        List<CompanyInfo> companyInfoList = getCompanyList(response, language);
        List<CompanyInfo> result = new ArrayList<>();

        for (CompanyInfo source : companyInfoList) {
            if (!StringUtils.hasText(source.getColor())) {
                continue;
            }
            CompanyInfo company = new CompanyInfo();
            company.setColor(source.getColor());
            setCompanyName(company, language, source.getName());
            result.add(company);
        }
        return result;
    }

    /**
     * 刷新公司多语言信息，已有记录按 color 匹配后更新。
     */
    @Transactional
    public List<CompanyInfo> refreshCompanyInfo() {
        List<CompanyInfo> companyInfoList = buildCompleteCompanyInfo();
        for (CompanyInfo company : companyInfoList) {
            upsertCompanyInfo(company);
        }
        return companyInfoList;
    }

    /**
     * 刷新公司单语言信息，已有记录按 color 匹配后只更新对应语言字段。
     */
    @Transactional
    public List<CompanyInfo> refreshCompanyInfo(String lang) {
        if (!StringUtils.hasText(lang)) {
            return refreshCompanyInfo();
        }
        MacauWebLanguage language = MacauWebLanguage.fromValue(lang);
        List<CompanyInfo> companyInfoList = buildCompanyInfo(language);
        for (CompanyInfo company : companyInfoList) {
            upsertCompanyInfo(company);
        }
        return companyInfoList;
    }

    /**
     * 从响应中取出公司列表，并校验接口返回是否有效。
     */
    private List<CompanyInfo> getCompanyList(RouteAndCompanyResponse response, MacauWebLanguage language) {
        if (response == null || response.getData() == null || CollectionUtils.isEmpty(response.getData().getCompanyList())) {
            throw new IllegalStateException("getRouteAndCompanyList returned empty companyList, language=" + language.getValue());
        }
        return response.getData().getCompanyList();
    }

    private void upsertCompanyInfo(CompanyInfo company) {
        if (!StringUtils.hasText(company.getColor())) {
            return;
        }
        CompanyInfo existing = companyInfoMapper.selectOne(
                Wrappers.<CompanyInfo>lambdaQuery()
                        .eq(CompanyInfo::getColor, company.getColor())
        );
        if (existing == null) {
            companyInfoMapper.insert(company);
        } else {
            company.setId(existing.getId());
            companyInfoMapper.updateById(company);
        }
    }

    /**
     * 按当前语言写入对应的公司名称字段。
     */
    private void setCompanyName(CompanyInfo company, MacauWebLanguage language, String name) {
        switch (language) {
            case ZH_CN -> company.setNameZhCn(name);
            case ZH_TW -> company.setNameZhTw(name);
            case EN -> company.setNameEn(name);
            case PT -> company.setNamePt(name);
        }
    }
}
