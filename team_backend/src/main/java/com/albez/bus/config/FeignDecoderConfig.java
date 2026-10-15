package com.albez.bus.config;

import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Configuration
public class FeignDecoderConfig {

    /**
     * 澳门巴士接口返回 JSON，但 Content-Type 是 text/html，需要让 Feign 的 Jackson converter 兼容解析。
     */
    @Bean
    public HttpMessageConverterCustomizer textHtmlJsonMessageConverterCustomizer() {
        return converters -> converters.forEach(this::supportTextHtmlForJacksonConverter);
    }

    private void supportTextHtmlForJacksonConverter(HttpMessageConverter<?> converter) {
        if (converter instanceof AbstractJacksonHttpMessageConverter<?> jacksonConverter) {
            addTextHtmlIfAbsent(jacksonConverter.getSupportedMediaTypes(), jacksonConverter::setSupportedMediaTypes);
        }
        if (converter instanceof AbstractJackson2HttpMessageConverter jackson2Converter) {
            addTextHtmlIfAbsent(jackson2Converter.getSupportedMediaTypes(), jackson2Converter::setSupportedMediaTypes);
        }
    }

    private void addTextHtmlIfAbsent(List<MediaType> supportedMediaTypes, Consumer<List<MediaType>> mediaTypesSetter) {
        if (supportedMediaTypes.stream().anyMatch(mediaType -> mediaType.includes(MediaType.TEXT_HTML))) {
            return;
        }
        List<MediaType> mediaTypes = new ArrayList<>(supportedMediaTypes);
        mediaTypes.add(MediaType.TEXT_HTML);
        mediaTypesSetter.accept(mediaTypes);
    }
}
