package com.materialbook.inventory;

import com.materialbook.inventory.StockService.Line;
import com.materialbook.products.AbstractCatalogApiTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;

/** Drives StockService directly with test-created material_transactions rows (the material module does not exist yet). */
abstract class AbstractStockTest extends AbstractCatalogApiTest {
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected StockService stock;
    @Autowired protected TransactionTemplate tx;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** Authenticates the current thread as the bootstrap admin (thread-local, so every worker thread must call it). */
    protected void actAsAdmin() {
        Long id = jdbc.queryForObject("select id from users where mobile = '9000000001'", Long.class);
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("sub", String.valueOf(id)));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    protected long newProduct(String minimumStock) {
        int n = SEQ.incrementAndGet();
        return jdbc.queryForObject("insert into products(product_code, product_name, unit, minimum_stock) values (?, ?, 'KG', ?::numeric) returning id",
                Long.class, "ST-" + n, "Stock product " + n, minimumStock);
    }

    protected long newTransaction(String type) {
        int n = SEQ.incrementAndGet();
        Long creator = jdbc.queryForObject("select id from users where mobile = '9000000001'", Long.class);
        Long party = "IN".equals(type) ? jdbc.queryForObject(
                "insert into parties(party_code, party_name, party_type) values (?, 'Supplier', 'SUPPLIER') returning id", Long.class, "SP-" + n) : null;
        return jdbc.queryForObject("""
                insert into material_transactions(transaction_number, transaction_type, transaction_date, party_id, created_by)
                values (?, ?, current_date, ?, ?) returning id
                """, Long.class, "TX-" + n, type, party, creator);
    }

    protected long newItem(long transactionId, long productId, String quantity) {
        return jdbc.queryForObject("insert into material_transaction_items(transaction_id, product_id, quantity) values (?, ?, ?::numeric) returning id",
                Long.class, transactionId, productId, quantity);
    }

    /** Creates a transaction with one item per entry (productId -> quantity) and posts it, all in one DB transaction. Returns the tx id. */
    protected long postNew(MovementType type, LocalDate date, Object... productAndQuantity) {
        actAsAdmin();
        return tx.execute(status -> {
            long txId = newTransaction(type.name());
            List<Line> lines = new java.util.ArrayList<>();
            for (int i = 0; i < productAndQuantity.length; i += 2) {
                long productId = (Long) productAndQuantity[i];
                String quantity = (String) productAndQuantity[i + 1];
                lines.add(new Line(productId, newItem(txId, productId, quantity), new BigDecimal(quantity)));
            }
            stock.post(type, txId, date, lines);
            return txId;
        });
    }

    protected long postNew(MovementType type, Object... productAndQuantity) {
        return postNew(type, LocalDate.now(), productAndQuantity);
    }

    protected BigDecimal balance(long productId) {
        return jdbc.queryForObject("select quantity from stock_balances where product_id = ?", BigDecimal.class, productId);
    }

    protected BigDecimal sumDelta(long productId) {
        return jdbc.queryForObject("select coalesce(sum(quantity_delta), 0) from stock_movements where product_id = ?", BigDecimal.class, productId);
    }

    protected int movementCount(long productId) {
        return jdbc.queryForObject("select count(*) from stock_movements where product_id = ?", Integer.class, productId);
    }

    protected static void assertQty(BigDecimal actual, String expected) {
        org.assertj.core.api.Assertions.assertThat(actual).isEqualByComparingTo(expected);
    }
}
