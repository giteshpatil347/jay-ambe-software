package com.materialbook.material;

import com.materialbook.products.ProductUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts an entered quantity into the product's own stock unit (1 TON = 1000 KG). Result has scale 3, HALF_UP.
 * PIECE converts only to/from PIECE. A null entered unit means "already in the product unit".
 */
final class UnitConverter {
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    static final int SCALE = 3;

    private UnitConverter() {}

    /** Thrown when the conversion is impossible or the result is not a positive quantity. */
    static final class UnitConversionException extends RuntimeException {
        UnitConversionException(String message) { super(message); }
    }

    static BigDecimal convert(BigDecimal quantity, ProductUnit entered, ProductUnit product) {
        ProductUnit from = entered == null ? product : entered;
        BigDecimal result;
        if (from == product) {
            result = quantity.setScale(SCALE, RoundingMode.HALF_UP);
        } else if (from == ProductUnit.TON && product == ProductUnit.KG) {
            result = quantity.multiply(THOUSAND).setScale(SCALE, RoundingMode.HALF_UP);
        } else if (from == ProductUnit.KG && product == ProductUnit.TON) {
            result = quantity.divide(THOUSAND, SCALE, RoundingMode.HALF_UP);
        } else {
            throw new UnitConversionException("Cannot convert " + from + " to " + product);
        }
        if (result.signum() <= 0) {
            throw new UnitConversionException("Quantity is too small for the material unit (" + product + ")");
        }
        return result;
    }
}
