package com.materialbook.attachments;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

/** Private file storage. Keys are server-generated; user-supplied names never reach the storage layer. */
public interface FileStorage {
    /** Key {@code yyyy/MM/<uuid>}. */
    static String newKey() {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        return String.format("%04d/%02d/%s", now.getYear(), now.getMonthValue(), UUID.randomUUID());
    }

    void put(String key, byte[] content);

    /** Whole file (uploads are capped at 10 MB, so buffering is bounded); NotFoundException when missing. */
    byte[] get(String key);

    /** Idempotent: deleting a missing file is not an error. */
    void delete(String key);
}
