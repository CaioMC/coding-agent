package com.empresa.copilotlogistico.codingagent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** HTTP e JSON compartilhados pelos clientes, só com a biblioteca padrão e Jackson. */
class HttpSupport {

    final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final Supplier<String> authorization;
    private final Map<String, String> extraHeaders;

    HttpSupport(Supplier<String> authorization, Map<String, String> extraHeaders) {
        this.authorization = authorization;
        this.extraHeaders = extraHeaders == null ? Map.of() : extraHeaders;
    }

    HttpResponse<byte[]> send(String method, String url, Object body, boolean authenticated) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json");
            if (authenticated) {
                b.header("Authorization", authorization.get());
                extraHeaders.forEach(b::header);
            }
            if (body != null) {
                b.header("Content-Type", "application/json");
                b.method(method, HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            return http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha HTTP em " + method + " " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido", e);
        }
    }

    JsonNode json(String method, String url, Object body) {
        HttpResponse<byte[]> resp = send(method, url, body, true);
        ensureOk(resp, method, url);
        try {
            return resp.body().length == 0 ? mapper.createObjectNode() : mapper.readTree(resp.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Baixa um arquivo. Se a API redirecionar para um storage com URL assinada, segue o
     * redirecionamento SEM o cabeçalho Authorization (alguns storages recusam os dois juntos).
     */
    byte[] download(String url) {
        HttpResponse<byte[]> resp = send("GET", url, null, true);
        int hops = 0;
        while (resp.statusCode() / 100 == 3 && hops++ < 5) {
            String location = resp.headers().firstValue("Location")
                    .orElseThrow(() -> new IllegalStateException("Redirecionamento sem Location"));
            resp = send("GET", location, null, false);
        }
        ensureOk(resp, "GET", url);
        return resp.body();
    }

    static void ensureOk(HttpResponse<byte[]> resp, String method, String url) {
        if (resp.statusCode() / 100 != 2) {
            String detail = new String(resp.body());
            throw new IllegalStateException("HTTP " + resp.statusCode() + " em " + method + " " + url + ": "
                    + (detail.length() > 500 ? detail.substring(0, 500) : detail));
        }
    }

    /** Descompacta o zip do artefato. Remove a pasta raiz ("agent-result/") dos nomes. */
    static Map<String, byte[]> unzip(byte[] zip) {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                int slash = name.lastIndexOf('/');
                files.put(slash >= 0 ? name.substring(slash + 1) : name, in.readAllBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Artefato inválido", e);
        }
        return files;
    }
}
