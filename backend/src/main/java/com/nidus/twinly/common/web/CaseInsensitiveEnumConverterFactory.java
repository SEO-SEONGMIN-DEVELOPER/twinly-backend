package com.nidus.twinly.common.web;

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
                if (constant.name().equalsIgnoreCase(value)) {
                    return constant;
                }
            }

            throw new IllegalArgumentException("허용되지 않는 값입니다: " + enumType.getSimpleName() + "=" + value);
        }
    }
}
