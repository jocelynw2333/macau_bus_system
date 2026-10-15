package com.albez.bus.domain.enums;

public enum MacauWebLanguage {
    ZH_CN("zh-cn"),
    ZH_TW("zh-tw"),
    EN("en"),
    PT("pt");

    private final String value;

    MacauWebLanguage(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MacauWebLanguage fromValue(String value) {
        for (MacauWebLanguage language : MacauWebLanguage.values()) {
            if (language.getValue().equals(value)) {
                return language;
            }
        }
        throw new IllegalArgumentException("Unsupported language: " + value);
    }
}
