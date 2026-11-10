package com.zhishuiyun.water.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/** Do not let JavaScript floating point JSON numbers silently become decimal strings. */
public class PhotoDecimalStringDeserializer extends StdDeserializer<String> {
    public PhotoDecimalStringDeserializer() { super(String.class); }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING))
            return (String) context.handleUnexpectedToken(String.class, parser);
        return parser.getText();
    }
}
