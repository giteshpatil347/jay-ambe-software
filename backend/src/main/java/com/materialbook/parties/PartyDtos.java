package com.materialbook.parties;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class PartyDtos {
    static final String GSTIN = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";
    static final String MOBILE = "^(\\+91)?[6-9][0-9]{9}$";

    private PartyDtos() {}

    public record PartyResponse(Long id, String code, String name, PartyType type, String mobile, String email,
                                String address, String gstNumber, BigDecimal openingBalance, PartyStatus status) {}

    /** Used for both create and update; {@code openingBalance} (signed: positive = party owes us) is locked once the party has transactions. */
    public record PartyRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull PartyType type,
            @Pattern(regexp = MOBILE, message = "must be a 10-digit Indian mobile number, optionally prefixed with +91") String mobile,
            @Email @Size(max = 150) String email,
            @Size(max = 1000) String address,
            @Pattern(regexp = GSTIN, message = "must be a valid 15-character GSTIN") String gstNumber,
            @Digits(integer = 12, fraction = 2) BigDecimal openingBalance) {}
}
