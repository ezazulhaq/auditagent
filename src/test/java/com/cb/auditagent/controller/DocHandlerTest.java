package com.cb.auditagent.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.EntityResponse;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void listAndGetDocsFromFilesystemSortedAndFiltered() throws Exception {
        Files.writeString(tempDir.resolve("02-second.md"), "# Second\nBody", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("01-first.md"), "# First — UTF8", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("notes.txt"), "ignore", StandardCharsets.UTF_8);
        Files.createDirectories(tempDir.resolve("nested.md"));

        DocHandler handler = new DocHandler(null, tempDir.toString());

        ServerResponse listResponse = handler.listDocs(MockServerRequest.builder().build()).block();
        assertNotNull(listResponse);
        assertEquals(HttpStatus.OK, listResponse.statusCode());
        EntityResponse<List<String>> listEntity = assertInstanceOf(EntityResponse.class, listResponse);
        assertEquals(List.of("01-first.md", "02-second.md"), listEntity.entity());

        ServerResponse docResponse = handler.getDoc(
                MockServerRequest.builder().pathVariable("filename", "01-first.md").build()).block();
        assertNotNull(docResponse);
        assertEquals(HttpStatus.OK, docResponse.statusCode());
        assertEquals(StandardCharsets.UTF_8, docResponse.headers().getContentType().getCharset());
        EntityResponse<String> docEntity = assertInstanceOf(EntityResponse.class, docResponse);
        assertEquals("# First — UTF8", docEntity.entity());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallsBackToClasspathDocsWhenFilesystemDirectoryIsMissing() throws Exception {
        Path missingDir = tempDir.resolve("missing-doc-dir");
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);

        Resource r2 = new NamedByteArrayResource("02-guide.md", "# Guide");
        Resource r1 = new NamedByteArrayResource("01-intro.md", "# Intro");
        when(resolver.getResources("classpath*:doc/*.md")).thenReturn(new Resource[]{r2, r1});
        when(resolver.getResource("classpath:doc/01-intro.md")).thenReturn(r1);

        DocHandler handler = new DocHandler(resolver, missingDir.toString());

        ServerResponse listResponse = handler.listDocs(MockServerRequest.builder().build()).block();
        assertNotNull(listResponse);
        assertEquals(HttpStatus.OK, listResponse.statusCode());
        EntityResponse<List<String>> listEntity = assertInstanceOf(EntityResponse.class, listResponse);
        assertEquals(List.of("01-intro.md", "02-guide.md"), listEntity.entity());

        ServerResponse docResponse = handler.getDoc(
                MockServerRequest.builder().pathVariable("filename", "01-intro.md").build()).block();
        assertNotNull(docResponse);
        assertEquals(HttpStatus.OK, docResponse.statusCode());
        EntityResponse<String> docEntity = assertInstanceOf(EntityResponse.class, docResponse);
        assertEquals("# Intro", docEntity.entity());
    }

    @Test
    void packagedRepositoryDocsAreDiscoverableOnClasspathFallback() {
        Path missingDir = tempDir.resolve("nonexistent-doc-dir");
        DocHandler handler = new DocHandler(null, missingDir.toString());

        ServerResponse listResponse = handler.listDocs(MockServerRequest.builder().build()).block();
        assertNotNull(listResponse);
        assertEquals(HttpStatus.OK, listResponse.statusCode());
        @SuppressWarnings("unchecked")
        EntityResponse<List<String>> listEntity = assertInstanceOf(EntityResponse.class, listResponse);
        assertTrue(listEntity.entity().contains("README.md"));
        assertTrue(listEntity.entity().contains("01-product-and-business-guide.md"));

        ServerResponse docResponse = handler.getDoc(
                MockServerRequest.builder().pathVariable("filename", "README.md").build()).block();
        assertNotNull(docResponse);
        assertEquals(HttpStatus.OK, docResponse.statusCode());
    }

    @Test
    void rejectsTraversalAndNonMarkdownFilenames() {
        DocHandler handler = new DocHandler(null, tempDir.toString());

        for (String invalid : List.of("../secret.md", "sub/file.md", "sub\\file.md", "notes.txt", "")) {
            ServerResponse response = handler.getDoc(
                    MockServerRequest.builder().pathVariable("filename", invalid).build()).block();
            assertNotNull(response);
            assertEquals(HttpStatus.FORBIDDEN, response.statusCode(), "Expected 403 for " + invalid);
        }
    }

    @Test
    void returnsNotFoundForMissingMarkdownFile() {
        Path missingDir = tempDir.resolve("missing-doc-dir");
        DocHandler handler = new DocHandler(null, missingDir.toString());

        ServerResponse response = handler.getDoc(
                MockServerRequest.builder().pathVariable("filename", "does-not-exist.md").build()).block();
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode());
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(String filename, String content) {
            super(content.getBytes(StandardCharsets.UTF_8));
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
