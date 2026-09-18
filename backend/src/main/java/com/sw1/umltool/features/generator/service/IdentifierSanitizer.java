package com.sw1.umltool.features.generator.service;

import java.text.Normalizer;
import java.util.Locale;

final class IdentifierSanitizer {
    private IdentifierSanitizer() {}

    static String pascal(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) return "Unnamed";
        StringBuilder result = new StringBuilder();
        for (String part : normalized.split(" +")) {
            if (part.isBlank()) continue;
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.length() == 0 ? "Unnamed" : result.toString();
    }

    static String camel(String value) {
        String pascal = pascal(value);
        if ("Unnamed".equals(pascal)) return "unnamed";
        if ("Id".equalsIgnoreCase(pascal)) return "id";
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    static String snake(String value) {
        return camel(value).replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD);
        String withoutMarks = decomposed.replaceAll("\\p{M}+", "");
        return withoutMarks.replaceAll("[^A-Za-z0-9]+", " ").trim();
    }
}
