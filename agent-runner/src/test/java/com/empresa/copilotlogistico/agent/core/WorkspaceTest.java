package com.empresa.copilotlogistico.agent.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceTest {

    @TempDir
    Path dir;
    Workspace ws;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(dir.resolve("src/main"));
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve("src/main/App.java"), "class App {\n  int x = 1;\n}\n");
        ws = new Workspace(dir);
    }

    @Test
    void bloqueiaCaminhoForaDoWorkspace() {
        assertThatThrownBy(() -> ws.resolve("../../etc/passwd")).isInstanceOf(SecurityException.class);
    }

    @Test
    void bloqueiaPastaGit() {
        assertThatThrownBy(() -> ws.writeFile(".git/config", "x")).isInstanceOf(SecurityException.class);
    }

    @Test
    void leComNumerosDeLinha() {
        assertThat(ws.readFile("src/main/App.java", 2, 2)).isEqualTo("2\t  int x = 1;\n");
    }

    @Test
    void substituiTrechoUnico() throws Exception {
        ws.replaceInFile("src/main/App.java", "int x = 1;", "int x = 2;");
        assertThat(Files.readString(dir.resolve("src/main/App.java"))).contains("int x = 2;");
    }

    @Test
    void recusaTrechoAmbiguo() {
        ws.writeFile("a.txt", "abc\nabc\n");
        assertThatThrownBy(() -> ws.replaceInFile("a.txt", "abc", "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mais de uma vez");
    }

    @Test
    void buscaIgnoraGit() throws Exception {
        Files.writeString(dir.resolve(".git/HEAD"), "int x");
        assertThat(ws.search("int x", null, 10)).containsExactly("src/main/App.java:2: int x = 1;");
    }
}
