package com.zhishuiyun.water.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/** Pixel coordinates and quarter turns must be exact integer JSON values. */
public class PhotoIntegerDeserializer extends StdDeserializer<Integer> {
    public PhotoIntegerDeserializer() { super(Integer.class); }
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
            return (Integer) context.handleUnexpectedToken(Integer.class, parser);
        return parser.getIntValue();
    }
}
