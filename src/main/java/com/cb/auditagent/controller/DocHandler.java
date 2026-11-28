package com.cb.auditagent.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class DocHandler {
    private static final String CLASSPATH_DOCS_PATTERN = "classpath*:doc/*.md";
    private static final MediaType TEXT_PLAIN_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final ResourcePatternResolver resourceResolver;
    private final Path docDir;

    @Autowired
    public DocHandler(
            @Autowired(required = false) ResourcePatternResolver resourceResolver,
            @Value("${auditagent.docs.dir:doc}") String docsDir) {
        this.resourceResolver = resourceResolver != null ? resourceResolver : new PathMatchingResourcePatternResolver();
        this.docDir = Paths.get(docsDir != null && !docsDir.isBlank() ? docsDir : "doc");
    }

    public DocHandler() {
        this(new PathMatchingResourcePatternResolver(), "doc");
    }

    public Mono<ServerResponse> listDocs(ServerRequest request) {
        try {
            List<String> docs = listFilesystemDocs();
            if (docs.isEmpty()) {
                docs = listClasspathDocs();
            }
            return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(docs);
        } catch (Exception e) {
            return ServerResponse.status(500).bodyValue("Error reading doc directory: " + e.getMessage());
        }
    }

    public Mono<ServerResponse> getDoc(ServerRequest request) {
        String filename = request.pathVariable("filename");
        if (!isSafeDocFilename(filename)) {
            return ServerResponse.status(403).bodyValue("Access denied");
        }

        Path normalizedDir = docDir.toAbsolutePath().normalize();
        Path filePath = normalizedDir.resolve(filename).normalize();
        if (!filePath.startsWith(normalizedDir) || !Objects.equals(filePath.getParent(), normalizedDir)) {
            return ServerResponse.status(403).bodyValue("Access denied");
        }

        try {
            if (Files.isRegularFile(filePath)) {
                String content = Files.readString(filePath, StandardCharsets.UTF_8);
                return ServerResponse.ok().contentType(TEXT_PLAIN_UTF8).bodyValue(content);
            }

            Resource classpathDoc = findClasspathDoc(filename);
            if (classpathDoc != null && classpathDoc.exists() && classpathDoc.isReadable()) {
                try (InputStream is = classpathDoc.getInputStream()) {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    return ServerResponse.ok().contentType(TEXT_PLAIN_UTF8).bodyValue(content);
                }
            }

            return ServerResponse.notFound().build();
        } catch (Exception e) {
            return ServerResponse.status(500).bodyValue("Error reading file: " + e.getMessage());
        }
    }

    private List<String> listFilesystemDocs() throws IOException {
        if (!Files.isDirectory(docDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(docDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".md"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    private List<String> listClasspathDocs() throws IOException {
        Resource[] resources = resourceResolver.getResources(CLASSPATH_DOCS_PATTERN);
        if (resources == null || resources.length == 0) {
            return List.of();
        }
        return Arrays.stream(resources)
                .filter(r -> r != null && r.exists())
                .map(Resource::getFilename)
                .filter(name -> name != null && name.endsWith(".md") && isSafeDocFilename(name))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private Resource findClasspathDoc(String filename) throws IOException {
        Resource direct = resourceResolver.getResource("classpath:doc/" + filename);
        if (direct.exists() && direct.isReadable()) {
            return direct;
        }
        Resource[] resources = resourceResolver.getResources(CLASSPATH_DOCS_PATTERN);
        if (resources != null) {
            for (Resource resource : resources) {
                if (resource != null && resource.exists() && filename.equals(resource.getFilename())) {
                    return resource;
                }
            }
        }
        return null;
    }

    private static boolean isSafeDocFilename(String filename) {
        if (filename == null || filename.isBlank() || !filename.endsWith(".md")) {
            return false;
        }
        return !filename.contains("/")
                && !filename.contains("\\")
                && !filename.contains("..")
                && filename.indexOf('\0') < 0;
    }
}
