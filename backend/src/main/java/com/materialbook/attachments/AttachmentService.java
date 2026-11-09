package com.materialbook.attachments;

import com.materialbook.attachments.AttachmentDtos.AttachmentContent;
import com.materialbook.attachments.AttachmentDtos.AttachmentResponse;
import com.materialbook.attachments.FileValidator.Policy;
import com.materialbook.attachments.FileValidator.Validated;
import com.materialbook.audit.service.AuditService;
import com.materialbook.common.ApiException.ConflictException;
import com.materialbook.common.ApiException.NotFoundException;
import com.materialbook.common.ApiException.ValidationException;
import java.io.IOException;
import java.sql.ResultSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Attachment permissions come from the OWNER's module (material IN/OUT, expense, payment), not from a module of
 * their own. Plain SQL is used for owners so this module never depends on the owning modules' classes.
 */
@Service
public class AttachmentService {
    static final int MAX_PER_OWNER = 10;
    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final String COLUMNS = "id, transaction_id, expense_id, party_payment_id, file_name, content_type, file_size, sha256, uploaded_by, created_at";

    private record Owner(OwnerType type, long id, String module, String status) {}

    private record Stored(AttachmentResponse response, String fileKey) {}

    private final JdbcTemplate jdbc;
    private final FileStorage storage;
    private final FileValidator validator;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public AttachmentService(JdbcTemplate jdbc, FileStorage storage, FileValidator validator, AuditService audit, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.validator = validator;
        this.audit = audit;
        this.tx = tx;
    }

    public AttachmentResponse upload(OwnerType ownerType, Long ownerId, MultipartFile file) {
        Owner owner = requireOwner(ownerType, ownerId);
        requireAny(owner.module(), "CREATE", "UPDATE");
        requirePosted(owner);
        requireCapacity(ownerType, ownerId); // cheap early reject; the authoritative check runs under a row lock below
        byte[] content = read(file);
        Validated valid = validator.validate(file.getOriginalFilename(), file.getContentType(), content, Policy.DOCUMENT);

        String key = FileStorage.newKey();
        storage.put(key, content);
        try {
            return tx.execute(status -> {
                // Serialises concurrent uploads (and cancel) on this owner: count + status are re-checked under the lock.
                String lockedStatus = jdbc.queryForObject("select status from " + ownerType.table + " where id = ? for no key update",
                        String.class, ownerId);
                requirePosted(new Owner(ownerType, ownerId, owner.module(), lockedStatus));
                requireCapacity(ownerType, ownerId);
                AttachmentResponse created = jdbc.queryForObject("insert into attachments(" + ownerType.fkColumn
                        + ", file_name, file_key, content_type, file_size, sha256, uploaded_by) values (?, ?, ?, ?, ?, ?, ?) returning " + COLUMNS,
                        MAPPER, ownerId, valid.fileName(), key, valid.contentType(), valid.size(), valid.sha256(), CurrentUser.id());
                audit.log("ATTACHMENT_UPLOAD", "ATTACHMENT", created.id(), null, created);
                return created;
            });
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
    }

    private void requireCapacity(OwnerType ownerType, Long ownerId) {
        Integer existing = jdbc.queryForObject("select count(*) from attachments where " + ownerType.fkColumn + " = ?", Integer.class, ownerId);
        if (existing != null && existing >= MAX_PER_OWNER) {
            throw new ConflictException("At most " + MAX_PER_OWNER + " attachments are allowed per record");
        }
    }

    public List<AttachmentResponse> list(OwnerType ownerType, Long ownerId) {
        requireAny(requireOwner(ownerType, ownerId).module(), "VIEW");
        return jdbc.query("select " + COLUMNS + " from attachments where " + ownerType.fkColumn + " = ? order by id", MAPPER, ownerId);
    }

    /** Bytes streamed through the app only (files are never served by URL); needs VIEW on the owning record. */
    public AttachmentContent content(Long id) {
        Stored stored = require(id);
        requireAny(ownerOf(stored.response()).module(), "VIEW");
        return new AttachmentContent(stored.response().fileName(), stored.response().contentType(), storage.get(stored.fileKey()));
    }

    /** Row first, object second: a failed object delete leaves an orphan blob, never a row pointing at nothing. */
    public void delete(Long id) {
        Stored stored = require(id);
        Owner owner = ownerOf(stored.response());
        requireAny(owner.module(), "UPDATE");
        requirePosted(owner);
        tx.executeWithoutResult(status -> {
            jdbc.update("delete from attachments where id = ?", id);
            audit.log("ATTACHMENT_DELETE", "ATTACHMENT", id, stored.response(), null);
        });
        deleteQuietly(stored.fileKey());
    }

    private Stored require(Long id) {
        List<Stored> rows = jdbc.query("select " + COLUMNS + ", file_key from attachments where id = ?",
                (rs, n) -> new Stored(MAPPER.mapRow(rs, n), rs.getString("file_key")), id);
        if (rows.isEmpty()) {
            throw new NotFoundException("Attachment not found");
        }
        return rows.get(0);
    }

    private Owner ownerOf(AttachmentResponse a) {
        return requireOwner(a.ownerType(), a.ownerId());
    }

    private Owner requireOwner(OwnerType type, Long id) {
        if (id == null) {
            throw new ValidationException("ownerId is required");
        }
        String select = type == OwnerType.MATERIAL_TRANSACTION ? "transaction_type, status" : "status";
        List<Owner> rows = jdbc.query("select " + select + " from " + type.table + " where id = ?", (rs, n) -> new Owner(type, id,
                type == OwnerType.MATERIAL_TRANSACTION ? "MATERIAL_" + rs.getString("transaction_type")
                        : type == OwnerType.EXPENSE ? "EXPENSE" : "PAYMENT", rs.getString("status")), id);
        if (rows.isEmpty()) {
            throw new NotFoundException(type + " " + id + " not found");
        }
        return rows.get(0);
    }

    private static void requirePosted(Owner owner) {
        if ("CANCELLED".equals(owner.status())) {
            throw new ConflictException("The record is cancelled; its attachments can no longer be changed");
        }
    }

    private static void requireAny(String module, String... actions) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        for (String action : actions) {
            String permission = module + "." + action;
            if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permission))) {
                return;
            }
        }
        throw new AccessDeniedException("Missing permission " + module + "." + actions[0]);
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete file {} from storage; it is now an orphan", key, e);
        }
    }

    private static final RowMapper<AttachmentResponse> MAPPER = (ResultSet rs, int n) -> {
        OwnerType type;
        long ownerId;
        if (rs.getObject("transaction_id") != null) {
            type = OwnerType.MATERIAL_TRANSACTION;
            ownerId = rs.getLong("transaction_id");
        } else if (rs.getObject("expense_id") != null) {
            type = OwnerType.EXPENSE;
            ownerId = rs.getLong("expense_id");
        } else {
            type = OwnerType.PARTY_PAYMENT;
            ownerId = rs.getLong("party_payment_id");
        }
        return new AttachmentResponse(rs.getLong("id"), type, ownerId, rs.getString("file_name"), rs.getString("content_type"),
                rs.getLong("file_size"), rs.getString("sha256"), rs.getObject("uploaded_by", Long.class),
                rs.getTimestamp("created_at").toInstant());
    };

    static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Could not read the uploaded file");
        }
    }
}
