package com.cb.auditagent.domain;

public record ReportArtifact(
                byte[] content,
                String contentType,
                String fileName) {
}
