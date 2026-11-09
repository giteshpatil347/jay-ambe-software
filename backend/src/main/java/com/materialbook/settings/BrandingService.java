package com.materialbook.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.materialbook.audit.service.AuditService;
import com.materialbook.settings.BrandingDtos.BrandingResponse;
import com.materialbook.settings.BrandingDtos.StoredIcon;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Business name and icon live in {@code business_settings} (keys BUSINESS_NAME = JSON string, BUSINESS_ICON = JSON object
 * {contentType, data(base64), version}); the icon is in the database so it survives redeploys. Only SETTINGS.MANAGE may change them.
 */
@Service
public class BrandingService {
    static final String DEFAULT_NAME = "MaterialBook";
    static final int MAX_NAME = 60;
    static final int MAX_ICON_BYTES = 512 * 1024;
    private static final String NAME_KEY = "BUSINESS_NAME";
    private static final String ICON_KEY = "BUSINESS_ICON";
    private static final String MODULE = "SETTINGS";
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\p{Cf}\\u2028\\u2029]");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;

    public BrandingService(JdbcTemplate jdbc, ObjectMapper mapper, AuditService audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public BrandingResponse get() {
        Optional<StoredIcon> icon = loadIcon();
        return new BrandingResponse(currentName(), icon.isPresent(), icon.map(StoredIcon::version).orElse(0L));
    }

    @Transactional(readOnly = true)
    public Optional<StoredIcon> icon() { return loadIcon(); }

    @Transactional
    public BrandingResponse update(Long userId, String rawName, MultipartFile file) {
        String name = validName(rawName);
        StoredIcon newIcon = file == null || file.isEmpty() ? null : validIcon(file);
        String oldName = currentName();
        Optional<StoredIcon> oldIcon = loadIcon();

        write(NAME_KEY, mapper.valueToTree(name), userId);
        if (newIcon != null) {
            long version = Math.max(System.currentTimeMillis(), oldIcon.map(i -> i.version() + 1).orElse(0L));
            ObjectNode node = mapper.createObjectNode();
            node.put("contentType", newIcon.contentType());
            node.put("data", Base64.getEncoder().encodeToString(newIcon.data()));
            node.put("version", version);
            write(ICON_KEY, node, userId);
        }
        audit.log("BRANDING_UPDATE", MODULE, null, summary(oldName, oldIcon.orElse(null)),
                summary(name, newIcon != null ? newIcon : oldIcon.orElse(null)));
        return get();
    }

    @Transactional
    public void removeIcon() {
        Optional<StoredIcon> old = loadIcon();
        if (old.isEmpty()) {
            return;
        }
        jdbc.update("delete from business_settings where setting_key = ?", ICON_KEY);
        audit.log("BRANDING_ICON_REMOVE", MODULE, null, summary(currentName(), old.get()), summary(currentName(), null));
    }

    /** Name and icon metadata only: the bytes themselves never reach the audit log. */
    private static Map<String, Object> summary(String name, StoredIcon icon) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("iconBytes", icon == null ? 0 : icon.data().length);
        if (icon != null) {
            m.put("iconContentType", icon.contentType());
        }
        return m;
    }

    private String currentName() {
        List<String> names = jdbc.queryForList(
                "select setting_value #>> '{}' from business_settings where setting_key = ? and jsonb_typeof(setting_value) = 'string'",
                String.class, NAME_KEY);
        return names.isEmpty() || names.get(0).isBlank() ? DEFAULT_NAME : names.get(0).trim();
    }

    private Optional<StoredIcon> loadIcon() {
        List<String> rows = jdbc.queryForList(
                "select setting_value::text from business_settings where setting_key = ? and jsonb_typeof(setting_value) = 'object'",
                String.class, ICON_KEY);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        try {
            JsonNode n = mapper.readTree(rows.get(0));
            if (!n.hasNonNull("data") || !n.hasNonNull("contentType")) {
                return Optional.empty();
            }
            return Optional.of(new StoredIcon(n.get("contentType").asText(), Base64.getDecoder().decode(n.get("data").asText()),
                    n.path("version").asLong(0)));
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty(); // a damaged value must not break the login page
        }
    }

    private void write(String key, JsonNode value, Long userId) {
        jdbc.update("""
                insert into business_settings(setting_key, setting_value, updated_by, updated_at) values (?, ?::jsonb, ?, now())
                on conflict (setting_key) do update set setting_value = excluded.setting_value,
                  updated_by = excluded.updated_by, updated_at = now()""", key, value.toString(), userId);
    }

    static String validName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BrandingValidationException("name", "Business name is required");
        }
        if (name.length() > MAX_NAME) {
            throw new BrandingValidationException("name", "Business name must be at most " + MAX_NAME + " characters");
        }
        if (CONTROL.matcher(name).find()) {
            throw new BrandingValidationException("name", "Business name must not contain control characters");
        }
        return name;
    }

    private static StoredIcon validIcon(MultipartFile file) {
        if (file.getSize() > MAX_ICON_BYTES) {
            throw new BrandingValidationException("icon", "Icon must be 512 KB or smaller");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BrandingValidationException("icon", "The icon could not be read");
        }
        if (data.length > MAX_ICON_BYTES) {
            throw new BrandingValidationException("icon", "Icon must be 512 KB or smaller");
        }
        String sniffed = sniff(data);
        if (sniffed == null) {
            throw new BrandingValidationException("icon", "Icon must be a PNG, JPEG or WEBP image");
        }
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT).split(";")[0].trim();
        if (declared.equals("image/jpg")) {
            declared = "image/jpeg";
        }
        if (!declared.equals(sniffed)) {
            throw new BrandingValidationException("icon", "The file content does not match its type; upload a real PNG, JPEG or WEBP image");
        }
        return new StoredIcon(sniffed, data, 0);
    }

    /** Decides the type from magic bytes only; SVG (text) and everything else is rejected. */
    static String sniff(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
