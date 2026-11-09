package com.materialbook.config;

import java.io.IOException;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the compiled React app (copied into {@code classpath:/static/} at build time) from the same origin as the API.
 * <ul>
 *   <li>{@code /assets/**}: content-hashed by Vite, cached for a year.</li>
 *   <li>everything else ({@code index.html}, {@code sw.js}, manifest, icons): {@code no-cache}, so a new deployment is picked up.</li>
 *   <li>SPA fallback: a path that is not a server path and has no file extension returns index.html (deep links survive a refresh).</li>
 * </ul>
 * Only {@code classpath:/static/} is exposed (never the classpath root, so application.yml etc. are unreachable); there is no directory listing.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {
    private static final String STATIC = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(STATIC + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new SpaFallbackResolver());
    }

    /** "/" has an empty path inside the resource handler (which refuses it), so the app's front door is a functional route. */
    @Bean
    RouterFunction<ServerResponse> indexRoute() {
        HandlerFunction<ServerResponse> index = request -> ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noCache())
                .body(new ClassPathResource("static/index.html"));
        return RouterFunctions.route().GET("/", index).HEAD("/", index).build();
    }

    static final class SpaFallbackResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            String path = "/" + resourcePath;
            if (SpaPaths.isServerPath(path)) {
                return null;
            }
            Resource found = super.getResource(resourcePath, location);
            if (found != null && found.isReadable()) {
                return found;
            }
            if (SpaPaths.isClientRoute(path)) {
                Resource index = new ClassPathResource("static/index.html");
                return index.isReadable() ? index : null;
            }
            return null;
        }
    }
}
