package com.cb.auditagent.controller;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import org.springframework.http.MediaType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class DocHandler {
    private static final Path DOC_DIR = Paths.get("doc");

    public Mono<ServerResponse> listDocs(ServerRequest request) {
        try {
            if (!Files.exists(DOC_DIR)) {
                return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(List.of());
            }
            try (Stream<Path> paths = Files.list(DOC_DIR)) {
                List<String> docs = paths
                        .filter(p -> p.toString().endsWith(".md"))
                        .map(p -> p.getFileName().toString())
                        .collect(Collectors.toList());
                return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(docs);
            }
        } catch (Exception e) {
            return ServerResponse.status(500).bodyValue("Error reading doc directory: " + e.getMessage());
        }
    }

    public Mono<ServerResponse> getDoc(ServerRequest request) {
        String filename = request.pathVariable("filename");
        Path filePath = DOC_DIR.resolve(filename).normalize();

        // Security check
        if (!filePath.startsWith(DOC_DIR.toAbsolutePath().normalize()) && !filePath.startsWith(DOC_DIR.normalize())) {
            return ServerResponse.status(403).bodyValue("Access denied");
        }

        if (!Files.exists(filePath) || !filePath.toString().endsWith(".md")) {
            return ServerResponse.notFound().build();
        }

        try {
            String content = Files.readString(filePath);
            return ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValue(content);
        } catch (Exception e) {
            return ServerResponse.status(500).bodyValue("Error reading file: " + e.getMessage());
        }
    }
}
