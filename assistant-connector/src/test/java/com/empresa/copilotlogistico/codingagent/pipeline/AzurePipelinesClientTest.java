package com.empresa.copilotlogistico.codingagent.pipeline;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Testa o cliente contra um servidor HTTP falso que imita a API do Azure DevOps. */
class AzurePipelinesClientTest {

    private HttpServer server;
    private String base;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/org/Proj/_apis/pipelines/7/runs", ex -> {
            lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            String body;
            if ("POST".equals(ex.getRequestMethod())) {
                lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                body = "{\"id\":101,\"state\":\"inProgress\",\"_links\":{\"web\":{\"href\":\"http://web/101\"}}}";
            } else {
                body = "{\"id\":101,\"state\":\"completed\",\"result\":\"succeeded\"}";
            }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.createContext("/org/Proj/_apis/build/builds/101/artifacts", ex -> {
            byte[] b = ("{\"resource\":{\"downloadUrl\":\"" + base + "/download\"}}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.createContext("/download", ex -> {
            ex.getResponseHeaders().add("Location", base + "/blob");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.createContext("/blob", ex -> {
            // Storage com URL assinada: não deve receber Authorization.
            if (ex.getRequestHeaders().getFirst("Authorization") != null) {
                ex.sendResponseHeaders(400, -1);
                ex.close();
                return;
            }
            byte[] zip = zip(Map.of("agent-result/result.json", "{\"status\":\"COMPLETED\"}"));
            ex.sendResponseHeaders(200, zip.length);
            ex.getResponseBody().write(zip);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void disparaAcompanhaEBaixaResultado() {
        AzurePipelinesClient client = new AzurePipelinesClient(base + "/org", "Proj", 7, "main",
                "agent-result", AzurePipelinesClient.pat("abc"));

        PipelineRun run = client.start(Map.of("requestId", "r1", "targetRepository", "app",
                "baseBranch", "main", "workBranch", "agent/X-1", "taskB64", "e30="));
        assertThat(run.runId()).isEqualTo("101");
        assertThat(run.webUrl()).isEqualTo("http://web/101");
        assertThat(lastBody.get()).contains("\"templateParameters\"").contains("\"refs/heads/main\"");
        assertThat(lastAuth.get()).startsWith("Basic ");

        assertThat(client.state(run).phase()).isEqualTo(PipelineState.Phase.SUCCEEDED);

        Map<String, byte[]> files = client.downloadResult(run).orElseThrow();
        assertThat(new String(files.get("result.json"), StandardCharsets.UTF_8)).contains("COMPLETED");
    }

    private static byte[] zip(Map<String, String> entries) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(out)) {
                for (var e : entries.entrySet()) {
                    zos.putNextEntry(new ZipEntry(e.getKey()));
                    zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
