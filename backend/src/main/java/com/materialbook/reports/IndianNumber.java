package com.materialbook.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Indian digit grouping (last three digits, then pairs): 187500 -> 1,87,500. */
public final class IndianNumber {
    private IndianNumber() {}

    public static String format(BigDecimal value, int scale) {
        BigDecimal scaled = value.setScale(scale, RoundingMode.HALF_UP);
        String plain = scaled.abs().toPlainString();
        int dot = plain.indexOf('.');
        String integer = dot < 0 ? plain : plain.substring(0, dot);
        String fraction = dot < 0 ? "" : plain.substring(dot);
        StringBuilder grouped = new StringBuilder();
        int head = integer.length() - 3;
        if (head > 0) {
            for (int i = 0; i < head; i++) {
                if (i > 0 && (head - i) % 2 == 0) {
                    grouped.append(',');
                }
                grouped.append(integer.charAt(i));
            }
            grouped.append(',').append(integer, head, integer.length());
        } else {
            grouped.append(integer);
        }
        return (scaled.signum() < 0 ? "-" : "") + grouped + fraction;
    }

    /** {@code ₹1,87,500.00}; negative values render as {@code -₹1,500.00}. */
    public static String rupees(BigDecimal value) {
        String formatted = format(value, 2);
        return formatted.startsWith("-") ? "-₹" + formatted.substring(1) : "₹" + formatted;
    }
}
