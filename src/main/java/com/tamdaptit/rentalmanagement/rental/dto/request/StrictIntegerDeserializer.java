package com.tamdaptit.rentalmanagement.rental.dto.request;

/** Reject JSON fractions instead of Jackson's default coercion from e.g. 1.9 to 1. */
public final class StrictIntegerDeserializer extends tools.jackson.databind.ValueDeserializer<Integer> {
    @Override
    public Integer deserialize(tools.jackson.core.JsonParser parser, tools.jackson.databind.DeserializationContext context) {
        if (parser.currentToken() != tools.jackson.core.JsonToken.VALUE_NUMBER_INT) {
            return context.reportInputMismatch(Integer.class, "Giá trị phải là số nguyên JSON");
        }
        return parser.getIntValue();
    }
}
