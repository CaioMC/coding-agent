package com.example.codingagent.infrastructure.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalWorkspaceTest {

    @TempDir
    Path dir;

    @TempDir
    Path outside;

    LocalWorkspace workspace;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(this.dir.resolve("src/main"));
        Files.createDirectories(this.dir.resolve(".git"));
        Files.createDirectories(this.dir.resolve("target"));
        Files.writeString(this.dir.resolve("src/main/App.java"), "class App {\n  int x = 1;\n}\n");
        this.workspace = new LocalWorkspace(this.dir);
    }

    @Test
    void bloqueiaCaminhoForaDoWorkspace() {
        assertThatThrownBy(() -> this.workspace.readFile("../../etc/passwd", null, null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void bloqueiaLinkSimbolicoParaFora() throws Exception {
        Files.writeString(this.outside.resolve("secret.txt"), "segredo");
        Files.createSymbolicLink(this.dir.resolve("link"), this.outside);

        assertThatThrownBy(() -> this.workspace.readFile("link/secret.txt", null, null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void bloqueiaPastaGit() {
        assertThatThrownBy(() -> this.workspace.writeFile(".git/config", "x"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void leComNumerosDeLinha() {
        assertThat(this.workspace.readFile("src/main/App.java", 2, 2)).isEqualTo("2\t  int x = 1;\n");
    }

    @Test
    void substituiTrechoUnico() throws Exception {
        this.workspace.replaceInFile("src/main/App.java", "int x = 1;", "int x = 2;");

        assertThat(Files.readString(this.dir.resolve("src/main/App.java"))).contains("int x = 2;");
    }

    @Test
    void recusaTrechoAmbiguo() {
        this.workspace.writeFile("a.txt", "abc\nabc\n");

        assertThatThrownBy(() -> this.workspace.replaceInFile("a.txt", "abc", "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mais de uma vez");
    }

    @Test
    void buscaIgnoraGit() throws Exception {
        Files.writeString(this.dir.resolve(".git/HEAD"), "int x");

        assertThat(this.workspace.search("int x", null, 10)).containsExactly("src/main/App.java:2: int x = 1;");
    }

    @Test
    void listagemIgnoraPastasDeBuild() {
        assertThat(this.workspace.listFiles(".", 3, 100))
                .containsExactly("src/", "src/main/", "src/main/App.java");
    }

    @Test
    void readIfExistsDevolveVazioQuandoNaoHaArquivo() {
        assertThat(this.workspace.readIfExists("AGENTS.md")).isEmpty();
    }
}
