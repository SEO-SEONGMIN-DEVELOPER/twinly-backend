package com.nidus.twinly.common.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class EnumJsonNames {

    private EnumJsonNames() {
    }

    public static String of(Enum<?> constant) {
        try {
            JsonProperty jsonProperty = constant.getDeclaringClass()
                    .getField(constant.name())
                    .getAnnotation(JsonProperty.class);

            return jsonProperty == null ? constant.name() : jsonProperty.value();
        } catch (NoSuchFieldException e) {
            return constant.name();
        }
    }
}
