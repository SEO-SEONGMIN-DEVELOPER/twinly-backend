package com.nidus.twinly.common.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

public class CaseInsensitiveEnumConverterFactory implements ConverterFactory<String, Enum> {

    @Override
    public Converter getConverter(Class targetType) {
        return new StringToEnumConverter(targetType);
    }

    private static final class StringToEnumConverter<T extends Enum> implements Converter<String, T> {

        private final Class<T> enumType;

        private StringToEnumConverter(Class<T> enumType) {
            this.enumType = enumType;
        }

        @Override
        public T convert(String source) {
            String value = source.trim();
            if (value.isEmpty()) {
                return null;
            }

            for (T constant : enumType.getEnumConstants()) {
                if (jsonName(constant).equalsIgnoreCase(value)) {
                    return constant;
                }
            }

            throw new IllegalArgumentException("허용되지 않는 값입니다: " + enumType.getSimpleName() + "=" + value);
        }

        private String jsonName(T constant) {
            try {
                JsonProperty jsonProperty = enumType.getField(constant.name()).getAnnotation(JsonProperty.class);
                return jsonProperty == null ? constant.name() : jsonProperty.value();
            } catch (NoSuchFieldException e) {
                return constant.name();
            }
        }
    }
}
