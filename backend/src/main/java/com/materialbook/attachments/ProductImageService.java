package com.materialbook.attachments;

import com.materialbook.attachments.AttachmentDtos.AttachmentContent;
import com.materialbook.attachments.AttachmentDtos.ProductImageResponse;
import com.materialbook.attachments.FileValidator.Policy;
import com.materialbook.attachments.FileValidator.Validated;
import com.materialbook.audit.service.AuditService;
import com.materialbook.common.ApiException.NotFoundException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** Sets/reads {@code products.image_key} with SQL so the products module needs no change. Images are served inline. */
@Service
public class ProductImageService {
    private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);

    private final JdbcTemplate jdbc;
    private final FileStorage storage;
    private final FileValidator validator;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public ProductImageService(JdbcTemplate jdbc, FileStorage storage, FileValidator validator, AuditService audit, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.validator = validator;
        this.audit = audit;
        this.tx = tx;
    }

    public ProductImageResponse set(Long productId, MultipartFile file) {
        String oldKey = imageKey(productId);
        byte[] content = AttachmentService.read(file);
        Validated valid = validator.validate(file.getOriginalFilename(), file.getContentType(), content, Policy.PRODUCT_IMAGE);

        String key = FileStorage.newKey();
        storage.put(key, content);
        try {
            tx.executeWithoutResult(status -> {
                jdbc.update("update products set image_key = ?, updated_at = now() where id = ?", key, productId);
                audit.log("PRODUCT_IMAGE_SET", "PRODUCT", productId, Map.of("imageKey", oldKey == null ? "" : oldKey),
                        Map.of("imageKey", key, "contentType", valid.contentType(), "fileSize", valid.size(), "sha256", valid.sha256()));
            });
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
        if (oldKey != null) {
            deleteQuietly(oldKey);
        }
        return new ProductImageResponse(key);
    }

    /** Image bytes with their detected type; 404 when the material has no (readable image) file. */
    public AttachmentContent content(Long productId) {
        String key = imageKey(productId);
        if (key == null) {
            throw new NotFoundException("Material has no image");
        }
        byte[] bytes = storage.get(key);
        String type = validator.detectImageType(bytes);
        if (type == null) {
            throw new NotFoundException("Material has no image");
        }
        return new AttachmentContent("image", type, bytes);
    }

    private String imageKey(Long productId) {
        List<String> rows = jdbc.query("select image_key from products where id = ?", (rs, n) -> rs.getString(1), productId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Material not found");
        }
        return rows.get(0);
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete file {} from storage; it is now an orphan", key, e);
        }
    }
}
