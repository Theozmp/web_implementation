package gr.uoa.di.ted.events.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves uploaded event photos as static resources under /uploads/**.
 *
 * In dev files land in ./uploads/ (relative to the working directory). In Docker,
 * UPLOAD_DIR is /app/uploads with a named volume mounted there so files survive
 * container restarts.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        // map /uploads/** to the filesystem so browsers can load images directly.
        // Spring's resource handler needs the trailing slash on the location.
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
}
