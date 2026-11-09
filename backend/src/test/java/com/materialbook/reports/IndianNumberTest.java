package com.materialbook.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class IndianNumberTest {
    @Test
    void groupsLastThreeDigitsThenPairs() {
        assertThat(IndianNumber.format(new BigDecimal("0"), 2)).isEqualTo("0.00");
        assertThat(IndianNumber.format(new BigDecimal("999.5"), 2)).isEqualTo("999.50");
        assertThat(IndianNumber.format(new BigDecimal("1000"), 2)).isEqualTo("1,000.00");
        assertThat(IndianNumber.format(new BigDecimal("187500"), 2)).isEqualTo("1,87,500.00");
        assertThat(IndianNumber.format(new BigDecimal("12345678.9"), 2)).isEqualTo("1,23,45,678.90");
        assertThat(IndianNumber.format(new BigDecimal("12.5"), 3)).isEqualTo("12.500");
        assertThat(IndianNumber.format(new BigDecimal("1234.565"), 2)).isEqualTo("1,234.57");
    }

    @Test
    void rupeesPrefixAndNegatives() {
        assertThat(IndianNumber.rupees(new BigDecimal("187500"))).isEqualTo("₹1,87,500.00");
        assertThat(IndianNumber.rupees(new BigDecimal("-1500.5"))).isEqualTo("-₹1,500.50");
        assertThat(IndianNumber.rupees(new BigDecimal("-0.001"))).isEqualTo("₹0.00");
    }
}
