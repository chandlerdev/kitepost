package com.kitepost.entity;

import com.kitepost.config.Json;
import com.kitepost.entity.Models.*;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

/** Stores nested objects as JSON text. Works the same on PostgreSQL (TEXT) and MySQL (LONGTEXT). */
public final class JsonConverters {
    private JsonConverters() {}

    abstract static class Base<T> implements AttributeConverter<T, String> {
        private final TypeReference<T> type;
        Base(TypeReference<T> type) { this.type = type; }
        @Override public String convertToDatabaseColumn(T value) { return value == null ? null : Json.write(value); }
        @Override public T convertToEntityAttribute(String db) { return db == null || db.isBlank() ? null : Json.read(db, type); }
    }

    @Converter public static class StringList extends Base<List<String>> { public StringList() { super(new TypeReference<>() {}); } }
    @Converter public static class Regions extends Base<Map<String, List<String>>> { public Regions() { super(new TypeReference<>() {}); } }
    @Converter public static class StrategyJson extends Base<Strategy> { public StrategyJson() { super(new TypeReference<>() {}); } }
    @Converter public static class SenderJson extends Base<Sender> { public SenderJson() { super(new TypeReference<>() {}); } }
    @Converter public static class Queries extends Base<List<Query>> { public Queries() { super(new TypeReference<>() {}); } }
    @Converter public static class CurrentJson extends Base<CurrentSearch> { public CurrentJson() { super(new TypeReference<>() {}); } }
    @Converter public static class DraftJson extends Base<Draft> { public DraftJson() { super(new TypeReference<>() {}); } }
    @Converter public static class SentJson extends Base<SentInfo> { public SentJson() { super(new TypeReference<>() {}); } }
    @Converter public static class Answers extends Base<List<Answer>> { public Answers() { super(new TypeReference<>() {}); } }
}
