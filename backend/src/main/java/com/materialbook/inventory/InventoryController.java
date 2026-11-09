package com.materialbook.inventory;

import com.materialbook.common.PageResponse;
import com.materialbook.inventory.InventoryDtos.LedgerResponse;
import com.materialbook.inventory.InventoryDtos.OpeningStockRequest;
import com.materialbook.inventory.InventoryDtos.StockRow;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryQueryService queries;
    private final StockService stock;

    public InventoryController(InventoryQueryService queries, StockService stock) {
        this.queries = queries;
        this.stock = stock;
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('INVENTORY.VIEW')")
    PageResponse<StockRow> stock(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) Long categoryId,
                                 @RequestParam(defaultValue = "false") boolean lowStockOnly,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return queries.stock(q, categoryId, lowStockOnly, page, size);
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAuthority('INVENTORY.VIEW')")
    LedgerResponse ledger(@RequestParam Long productId,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(defaultValue = "false") boolean newestFirst,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "50") int size) {
        return queries.ledger(productId, from, to, newestFirst, page, size);
    }

    @PostMapping("/opening-stock")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTORY.ADJUST')")
    void openingStock(@Valid @RequestBody OpeningStockRequest request) {
        stock.recordOpening(request.date() == null ? LocalDate.now() : request.date(), request.items());
    }
}
