package com.chatbi.chat.models;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StringOrArrayDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.START_ARRAY) {
            List<String> values = new ArrayList<>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                values.add(p.getValueAsString());
            }
            return values;
        }
        return Collections.singletonList(p.getValueAsString());
    }
}
