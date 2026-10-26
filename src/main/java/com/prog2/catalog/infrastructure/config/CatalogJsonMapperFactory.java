package com.prog2.catalog.infrastructure.config;

import tools.jackson.databind.*;
import tools.jackson.databind.cfg.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/** Configuración contractual compartida: tipos estrictos y campos adicionales compatibles. */
public final class CatalogJsonMapperFactory {
    private CatalogJsonMapperFactory() { }
    public static JsonMapper strict(JsonMapper mapper) {
        return mapper.rebuild().disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)).build();
    }
}
