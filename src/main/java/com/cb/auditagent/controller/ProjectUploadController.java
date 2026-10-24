package com.cb.auditagent.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.cb.auditagent.domain.AuthenticatedUser;
import com.cb.auditagent.domain.ReportArtifact;
import com.cb.auditagent.service.GitHubAuthService;
import com.cb.auditagent.service.ProjectUploadService;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class ProjectUploadController {

    private final ProjectUploadService projectUploadService;
    private final GitHubAuthService auth;

    public ProjectUploadController(ProjectUploadService projectUploadService, GitHubAuthService auth) {
        this.projectUploadService = projectUploadService;
        this.auth = auth;
    }

    @PostMapping(value = "/scan/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<String>> scanUpload(
            @RequestPart("file") FilePart filePart,
            @RequestPart(value = "projectName", required = false) FormFieldPart projectNamePart,
            @RequestPart(value = "scannerName", required = false) FormFieldPart scannerNamePart,
            ServerWebExchange exchange) {
        AuthenticatedUser user = auth.requireUser(exchange);
        String projectName = projectNamePart != null ? projectNamePart.value() : null;
        String scannerName = scannerNamePart != null ? scannerNamePart.value() : "semgrep";

        Flux<String> stream = projectUploadService.scanUpload(user, filePart, projectName, scannerName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .body(stream);
    }

    @GetMapping("/reports/upload")
    public ResponseEntity<?> getUploadReport(@RequestParam String uploadId, ServerWebExchange exchange) {
        AuthenticatedUser user = auth.requireUser(exchange);
        return ResponseEntity.ok(projectUploadService.getUploadReport(user, uploadId));
    }

    @GetMapping("/reports/upload/export")
    public ResponseEntity<byte[]> exportUploadReport(
            @RequestParam String uploadId,
            @RequestParam String format,
            ServerWebExchange exchange) {
        AuthenticatedUser user = auth.requireUser(exchange);
        ReportArtifact artifact = projectUploadService.exportUploadReport(user, uploadId, format);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(artifact.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(artifact.fileName(), StandardCharsets.UTF_8).build());
        headers.setContentLength(artifact.content().length);
        headers.setCacheControl("private, no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(artifact.content());
    }
}

