package com.materialbook.attachments;

/** What an attachment hangs off; each maps to exactly one nullable FK column on {@code attachments}. */
public enum OwnerType {
    MATERIAL_TRANSACTION("transaction_id", "material_transactions"),
    EXPENSE("expense_id", "expenses"),
    PARTY_PAYMENT("party_payment_id", "party_payments");

    final String fkColumn;
    final String table;

    OwnerType(String fkColumn, String table) {
        this.fkColumn = fkColumn;
        this.table = table;
    }
}
