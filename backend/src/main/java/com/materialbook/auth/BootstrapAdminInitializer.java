package com.materialbook.auth;

import com.materialbook.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first SUPER_ADMIN from env when none exists. Never logs the password. */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final int MIN_PASSWORD_LENGTH = 10; // same floor as every other password entry point

    private final UserService users;
    private final PasswordEncoder encoder;
    private final AuthProperties.BootstrapAdmin admin;

    public BootstrapAdminInitializer(UserService users, PasswordEncoder encoder, AuthProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.admin = props.bootstrapAdmin();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsWithRole(SUPER_ADMIN)) {
            return;
        }
        if (isBlank(admin.mobile()) || isBlank(admin.password())) {
            log.warn("No SUPER_ADMIN exists and BOOTSTRAP_ADMIN_MOBILE / BOOTSTRAP_ADMIN_PASSWORD are not set; skipping bootstrap");
            return;
        }
        if (admin.password().length() < MIN_PASSWORD_LENGTH) {
            log.warn("BOOTSTRAP_ADMIN_PASSWORD is shorter than {} characters; skipping bootstrap", MIN_PASSWORD_LENGTH);
            return;
        }
        String mobile = admin.mobile().trim();
        if (users.existsByMobile(mobile)) {
            log.warn("Bootstrap admin mobile already belongs to a non-SUPER_ADMIN user; skipping bootstrap");
            return;
        }
        String name = isBlank(admin.name()) ? "Super Admin" : admin.name().trim();
        users.create(name, mobile, encoder.encode(admin.password()), true, SUPER_ADMIN);
        log.info("Bootstrap SUPER_ADMIN created for mobile {}", mobile);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
