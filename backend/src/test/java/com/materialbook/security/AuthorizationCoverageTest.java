package com.materialbook.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.materialbook.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Default-deny guard (ARCHITECTURE section 4): every controller handler must carry {@code @PreAuthorize}, and every permission code
 * named in it must exist in the seeded {@code permissions} table (a typo would silently lock everybody out, or worse).
 * Adding an endpoint without authorization, or with a misspelt permission, fails this test.
 */
@AutoConfigureMockMvc
class AuthorizationCoverageTest extends AbstractIntegrationTest {
    /** The only endpoints that may be called without a token (they authenticate by credential / refresh cookie). */
    private static final Set<String> PUBLIC = Set.of("AuthController#login", "AuthController#refresh", "AuthController#logout",
            "BrandingController#get", "BrandingController#icon");
    private static final Pattern CODE = Pattern.compile("'([A-Z_]+\\.[A-Z_]+)'");

    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    @Autowired MockMvc mvc;

    private Map<RequestMappingInfo, HandlerMethod> appHandlers() {
        Map<RequestMappingInfo, HandlerMethod> result = new java.util.LinkedHashMap<>();
        mappings.getHandlerMethods().forEach((info, h) -> {
            if (h.getBeanType().getName().startsWith("com.materialbook.")) {
                result.put(info, h);
            }
        });
        return result;
    }

    private static String id(HandlerMethod h) {
        return h.getBeanType().getSimpleName() + "#" + h.getMethod().getName();
    }

    private static PreAuthorize rule(HandlerMethod h) {
        PreAuthorize onMethod = AnnotatedElementUtils.findMergedAnnotation(h.getMethod(), PreAuthorize.class);
        return onMethod != null ? onMethod : AnnotatedElementUtils.findMergedAnnotation(h.getBeanType(), PreAuthorize.class);
    }

    @Test
    void everyHandlerIsAuthorizedAndOnlyNamesSeededPermissions() {
        Set<String> seeded = new HashSet<>(jdbc.queryForList("select permission_code from permissions", String.class));
        Map<RequestMappingInfo, HandlerMethod> handlers = appHandlers();
        assertThat(handlers.size()).as("handlers discovered").isGreaterThan(60);

        Set<String> found = new HashSet<>();
        List<String> problems = new ArrayList<>();
        for (HandlerMethod h : handlers.values()) {
            found.add(id(h));
            if (PUBLIC.contains(id(h))) {
                continue;
            }
            PreAuthorize pre = rule(h);
            if (pre == null) {
                problems.add(id(h) + " has no @PreAuthorize");
                continue;
            }
            Matcher m = CODE.matcher(pre.value());
            boolean any = false;
            while (m.find()) {
                any = true;
                if (!seeded.contains(m.group(1))) {
                    problems.add(id(h) + " names unknown permission " + m.group(1));
                }
            }
            if (!any && !pre.value().equals("isAuthenticated()")) {
                problems.add(id(h) + " has an @PreAuthorize that names no permission: " + pre.value());
            }
        }
        assertThat(found).as("whitelist entries must match real handlers").containsAll(PUBLIC);
        assertThat(problems).isEmpty();
    }

    @Test
    void everyNonPublicEndpointRejectsAnonymousCallersWith401() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : appHandlers().entrySet()) {
            if (PUBLIC.contains(id(e.getValue()))) {
                continue;
            }
            Set<org.springframework.web.bind.annotation.RequestMethod> methods = e.getKey().getMethodsCondition().getMethods();
            HttpMethod method = methods.isEmpty() ? HttpMethod.GET : HttpMethod.valueOf(methods.iterator().next().name());
            for (String pattern : e.getKey().getPathPatternsCondition().getPatternValues()) {
                String path = pattern.replaceAll("\\{[^}]+}", "1");
                int status = mvc.perform(request(method, path)).andReturn().getResponse().getStatus();
                if (status != 401) {
                    problems.add(method + " " + path + " answered " + status + " without a token");
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void publicEndpointsAreExactlyTheWhitelistedOnes() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/api/v1/users")).andExpect(status().isUnauthorized());
        mvc.perform(request(HttpMethod.GET, "/api/v1/does-not-exist")).andExpect(status().isUnauthorized());
    }
}
