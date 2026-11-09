package com.materialbook.common;

import java.util.Locale;

/** Builds a case-insensitive "contains" LIKE pattern with the user's own %, _ and \ escaped (PostgreSQL default escape is backslash). */
public final class LikePattern {
    private LikePattern() {}

    public static String contains(String search) {
        String escaped = search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
