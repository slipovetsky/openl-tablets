package org.openl.rules.ruleservice.publish.jaxrs.swagger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;

import org.openl.rules.openapi.OpenAPIConfiguration;

@SuppressWarnings("rawtypes")
public final class OpenApiObjectMapperHack {
    private final List converters;
    private List<Object> oldConverters;

    static {
        OpenAPIConfiguration.configure();
    }

    public OpenApiObjectMapperHack() {
        this.converters = getModelConverters();
    }

    @SuppressWarnings("unchecked")
    public void apply(ObjectMapper objectMapper) {
        List<ModelConverter> hackedConverters = new ArrayList<>();
        hackedConverters.add(new OpenApiSupportConverter());
        oldConverters = new ArrayList<>();
        for (Object converter : converters) {
            oldConverters.add(converter);
            hackedConverters.add(converter instanceof ModelResolver ? new ObjectMapperSupportModelResolver(objectMapper)
                                                                    : (ModelConverter) converter);
        }
        converters.clear();
        OpenApiInheritanceFixConverter openApiInheritanceFixConverter = new OpenApiInheritanceFixConverter(objectMapper,
            hackedConverters);
        converters.add(openApiInheritanceFixConverter);
    }

    @SuppressWarnings("unchecked")
    public List<ModelConverter> getModelConverters() {
        try {
            ModelConverters modelConverters = ModelConverters.getInstance();
            Field convertersField = ModelConverters.class.getDeclaredField("converters");
            convertersField.setAccessible(true);
            return (List<ModelConverter>) convertersField.get(modelConverters);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public void revert() {
        converters.clear();
        if (oldConverters != null) {
            converters.addAll(oldConverters);
        }
    }

}
