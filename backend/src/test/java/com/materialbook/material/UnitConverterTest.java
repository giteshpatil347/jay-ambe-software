package com.materialbook.material;

import static com.materialbook.products.ProductUnit.KG;
import static com.materialbook.products.ProductUnit.PIECE;
import static com.materialbook.products.ProductUnit.TON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.materialbook.material.UnitConverter.UnitConversionException;
import com.materialbook.products.ProductUnit;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UnitConverterTest {

    private static String conv(String q, ProductUnit entered, ProductUnit product) {
        return UnitConverter.convert(new BigDecimal(q), entered, product).toPlainString();
    }

    @ParameterizedTest
    @CsvSource({"500,0.500", "0.5,0.001", "0.4,0.000", "1499,1.499", "1500,1.500", "2.5,0.003", "0.5004,0.001", "12345.678,12.346"})
    void kgToTonRoundsHalfUpToThreeDecimals(String q, String expected) {
        if (expected.equals("0.000")) {
            assertThatThrownBy(() -> conv(q, KG, TON)).isInstanceOf(UnitConversionException.class).hasMessageContaining("too small");
        } else {
            assertThat(conv(q, KG, TON)).isEqualTo(expected);
        }
    }

    @ParameterizedTest
    @CsvSource({"2,2000.000", "0.001,1.000", "1.234,1234.000", "0.0005,0.500", "0.0004,0.400", "12.5,12500.000"})
    void tonToKgMultipliesByThousand(String q, String expected) {
        assertThat(conv(q, TON, KG)).isEqualTo(expected);
    }

    @Test
    void tooSmallAfterRoundingIsRejected() {
        assertThatThrownBy(() -> conv("0.0004", KG, TON)).isInstanceOf(UnitConversionException.class);
        assertThatThrownBy(() -> conv("0.499", KG, TON)).isInstanceOf(UnitConversionException.class);
        assertThat(conv("0.5", KG, TON)).isEqualTo("0.001");
    }

    @Test
    void sameUnitAndNullUnitAreUnchanged() {
        assertThat(conv("12.5", TON, TON)).isEqualTo("12.500");
        assertThat(conv("7", KG, KG)).isEqualTo("7.000");
        assertThat(conv("3", PIECE, PIECE)).isEqualTo("3.000");
        assertThat(conv("12.5", null, TON)).isEqualTo("12.500");
        assertThat(conv("3", null, PIECE)).isEqualTo("3.000");
    }

    @Test
    void pieceConvertsOnlyToAndFromPiece() {
        for (ProductUnit other : new ProductUnit[] {TON, KG}) {
            assertThatThrownBy(() -> conv("1", PIECE, other)).isInstanceOf(UnitConversionException.class);
            assertThatThrownBy(() -> conv("1", other, PIECE)).isInstanceOf(UnitConversionException.class);
        }
    }
}
