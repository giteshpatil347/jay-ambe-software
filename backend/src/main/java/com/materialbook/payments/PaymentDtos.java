package com.materialbook.payments;

import com.materialbook.expenses.PaymentMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record PaymentRequest(
            @NotNull Long partyId,
            @NotNull LocalDate paymentDate,
            @NotNull PaymentDirection direction,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            PaymentMode paymentMode,
            @Size(max = 100) String referenceNumber,
            @Size(max = 1000) String remarks) {}

    public record CancelRequest(@NotBlank @Size(max = 1000) String reason) {}

    public record PaymentResponse(Long id, String paymentNumber, Long partyId, String partyName, LocalDate paymentDate,
                                  PaymentDirection direction, BigDecimal amount, PaymentMode paymentMode,
                                  String referenceNumber, String remarks, PaymentStatus status, Instant createdAt,
                                  Instant cancelledAt, String cancellationReason) {}

    /** Page envelope plus {@code totalAmount}: sum of POSTED rows matching the filters (both directions), independent of paging. */
    public record PaymentPage(List<PaymentResponse> content, int page, int size, long totalElements, BigDecimal totalAmount) {}
}
