package com.nidus.twinly.common.openapi;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.Iterator;

@Component
public class JsonFormatStringModelConverter implements ModelConverter {

    @Override
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        if (schema == null || !hasStringShape(type)) {
            return schema;
        }

        Schema<?> items = schema.getItems();
        if (items != null) {
            if (items.get$ref() == null) {
                schema.setItems(toStringSchema(items));
            }
            return schema;
        }

        return toStringSchema(schema);
    }

    private StringSchema toStringSchema(Schema<?> source) {
        StringSchema stringSchema = new StringSchema();
        if (source.getDescription() != null) {
            stringSchema.setDescription(source.getDescription());
        }
        if (source.getNullable() != null) {
            stringSchema.setNullable(source.getNullable());
        }

        if (source.getExample() != null) {
            stringSchema.setExample(source.getExample());
        }

        return stringSchema;
    }

    private boolean hasStringShape(AnnotatedType type) {
        Annotation[] annotations = type.getCtxAnnotations();
        if (annotations == null) {
            return false;
        }

        for (Annotation annotation : annotations) {
            if (annotation instanceof JsonFormat jsonFormat && jsonFormat.shape() == JsonFormat.Shape.STRING) {
                return true;
            }
        }

        return false;
    }
}
