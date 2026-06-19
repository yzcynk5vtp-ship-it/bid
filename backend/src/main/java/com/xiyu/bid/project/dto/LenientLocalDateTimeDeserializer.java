package com.xiyu.bid.project.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 兼容多种前端日期字符串的 LocalDateTime 反序列化器。
 * 支持 ISO-8601（yyyy-MM-ddTHH:mm:ss）以及带空格格式（yyyy-MM-dd HH:mm:ss、yyyy-MM-dd HH:mm）。
 */
public class LenientLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SPACE_NO_SEC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getValueAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDateTime.parse(trimmed, ISO);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(trimmed, SPACE);
            } catch (DateTimeParseException ignored2) {
                return LocalDateTime.parse(trimmed, SPACE_NO_SEC);
            }
        }
    }
}
