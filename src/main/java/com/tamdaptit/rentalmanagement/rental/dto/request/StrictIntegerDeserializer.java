package com.tamdaptit.rentalmanagement.rental.dto.request;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Reject JSON fractions instead of Jackson's default coercion from e.g. 1.9 to 1. */
public final class StrictIntegerDeserializer extends ValueDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return context.reportInputMismatch(Integer.class, "Giá trị phải là số nguyên JSON");
        }

        return parser.getIntValue();
    }
}
