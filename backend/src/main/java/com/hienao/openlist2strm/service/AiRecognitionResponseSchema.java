package com.hienao.openlist2strm.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** AI 媒体识别响应的唯一结构定义，同时用于请求约束和本地响应校验。 */
final class AiRecognitionResponseSchema {

  private static final List<String> REQUIRED_FIELDS =
      List.of("success", "titleCandidates", "title", "year", "season", "episode", "type", "reason");
  private static final Set<String> REQUIRED_FIELD_SET = new LinkedHashSet<>(REQUIRED_FIELDS);
  private static final List<String> MEDIA_TYPE_VALUES = List.of("movie", "tv", "unknown");
  private static final Set<String> MEDIA_TYPES = Set.copyOf(MEDIA_TYPE_VALUES);
  private static final Pattern YEAR_PATTERN = Pattern.compile("^(?:19|20)\\d{2}$");
  private static final int MAX_TITLE_CANDIDATES = 5;
  private static final int MAX_TEXT_LENGTH = 300;

  private AiRecognitionResponseSchema() {}

  static Map<String, Object> responseFormat() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("success", Map.of("type", "boolean"));
    properties.put(
        "titleCandidates",
        Map.of(
            "type",
            "array",
            "items",
            Map.of("type", "string", "minLength", 1, "maxLength", MAX_TEXT_LENGTH),
            "maxItems",
            MAX_TITLE_CANDIDATES));
    properties.put(
        "title", Map.of("type", List.of("string", "null"), "maxLength", MAX_TEXT_LENGTH));
    properties.put(
        "year", Map.of("type", List.of("string", "null"), "pattern", YEAR_PATTERN.pattern()));
    properties.put("season", Map.of("type", List.of("integer", "null"), "minimum", 0));
    properties.put("episode", Map.of("type", List.of("integer", "null"), "minimum", 0));
    properties.put("type", Map.of("type", "string", "enum", MEDIA_TYPE_VALUES));
    properties.put(
        "reason", Map.of("type", List.of("string", "null"), "maxLength", MAX_TEXT_LENGTH));

    Map<String, Object> schema = new HashMap<>();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.put("required", REQUIRED_FIELDS);
    schema.put("properties", properties);

    return Map.of(
        "type",
        "json_schema",
        "json_schema",
        Map.of("name", "media_filename_parse", "strict", true, "schema", schema));
  }

  static ValidationResult validate(JsonNode value) {
    if (value == null || !value.isObject()) {
      return ValidationResult.invalid("响应必须是 JSON 对象");
    }

    Set<String> actualFields = new LinkedHashSet<>();
    value.fieldNames().forEachRemaining(actualFields::add);
    if (!actualFields.equals(REQUIRED_FIELD_SET)) {
      Set<String> missing = new LinkedHashSet<>(REQUIRED_FIELD_SET);
      missing.removeAll(actualFields);
      Set<String> additional = new LinkedHashSet<>(actualFields);
      additional.removeAll(REQUIRED_FIELD_SET);
      return ValidationResult.invalid("字段不匹配，缺少=" + missing + "，多余=" + additional);
    }

    JsonNode success = value.get("success");
    JsonNode titleCandidates = value.get("titleCandidates");
    JsonNode title = value.get("title");
    JsonNode year = value.get("year");
    JsonNode season = value.get("season");
    JsonNode episode = value.get("episode");
    JsonNode type = value.get("type");
    JsonNode reason = value.get("reason");

    if (!success.isBoolean()) {
      return ValidationResult.invalid("success 必须是 boolean");
    }
    if (!titleCandidates.isArray() || titleCandidates.size() > MAX_TITLE_CANDIDATES) {
      return ValidationResult.invalid("titleCandidates 必须是最多 5 项的数组");
    }
    for (JsonNode candidate : titleCandidates) {
      if (!isNonBlankText(candidate)) {
        return ValidationResult.invalid("titleCandidates 只能包含非空字符串");
      }
      if (candidate.textValue().length() > MAX_TEXT_LENGTH) {
        return ValidationResult.invalid("titleCandidates 中的标题过长");
      }
    }
    if (!isNullableText(title) || textLength(title) > MAX_TEXT_LENGTH) {
      return ValidationResult.invalid("title 必须是长度不超过 300 的字符串或 null");
    }
    if (!(year.isNull()
        || (year.isTextual() && YEAR_PATTERN.matcher(year.textValue()).matches()))) {
      return ValidationResult.invalid("year 必须是 1900-2099 的四位年份或 null");
    }
    if (!isNullableNonNegativeInteger(season)) {
      return ValidationResult.invalid("season 必须是非负整数或 null");
    }
    if (!isNullableNonNegativeInteger(episode)) {
      return ValidationResult.invalid("episode 必须是非负整数或 null");
    }
    if (!type.isTextual() || !MEDIA_TYPES.contains(type.textValue())) {
      return ValidationResult.invalid("type 必须是 movie、tv 或 unknown");
    }
    if (!isNullableText(reason) || textLength(reason) > MAX_TEXT_LENGTH) {
      return ValidationResult.invalid("reason 必须是长度不超过 300 的字符串或 null");
    }

    if (success.booleanValue()) {
      boolean hasTitle = isNonBlankText(title);
      if (!hasTitle && titleCandidates.isEmpty()) {
        return ValidationResult.invalid("success=true 时必须提供 title 或 titleCandidates");
      }
    } else if (!isNonBlankText(reason)) {
      return ValidationResult.invalid("success=false 时必须提供非空 reason");
    }

    return ValidationResult.success();
  }

  private static boolean isNullableText(JsonNode value) {
    return value.isNull() || value.isTextual();
  }

  private static boolean isNonBlankText(JsonNode value) {
    return value != null && value.isTextual() && !value.textValue().isBlank();
  }

  private static int textLength(JsonNode value) {
    return value.isTextual() ? value.textValue().length() : 0;
  }

  private static boolean isNullableNonNegativeInteger(JsonNode value) {
    return value.isNull()
        || (value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 0);
  }

  record ValidationResult(boolean valid, String reason) {

    static ValidationResult success() {
      return new ValidationResult(true, null);
    }

    static ValidationResult invalid(String reason) {
      return new ValidationResult(false, reason);
    }
  }
}
