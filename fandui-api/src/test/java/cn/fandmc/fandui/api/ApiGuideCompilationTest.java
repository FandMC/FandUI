package cn.fandmc.fandui.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiGuideCompilationTest {
    private static final String START_MARKER = "<!-- api-compile-start -->";
    private static final String END_MARKER = "<!-- api-compile-end -->";

    @Test
    void quickstartCompilesOnJava17(@TempDir Path temporaryDirectory) throws Exception {
        Path guide = findWorkspace().resolve("docs/API-GUIDE.md");
        String markdown = Files.readString(guide, StandardCharsets.UTF_8);
        int markedStart = markdown.indexOf(START_MARKER);
        int markedEnd = markdown.indexOf(END_MARKER, markedStart);
        assertTrue(markedStart >= 0 && markedEnd > markedStart, "Missing API guide compile markers");
        String marked = markdown.substring(markedStart + START_MARKER.length(), markedEnd);
        int codeStart = marked.indexOf("```java");
        int codeEnd = marked.indexOf("```", codeStart + "```java".length());
        assertTrue(codeStart >= 0 && codeEnd > codeStart, "Missing Java code fence inside compile markers");
        String sourceText = marked.substring(codeStart + "```java".length(), codeEnd).strip();

        Path sourceDirectory = Files.createDirectories(temporaryDirectory.resolve("src/example"));
        Path outputDirectory = Files.createDirectories(temporaryDirectory.resolve("classes"));
        Path source = sourceDirectory.resolve("ExampleUi.java");
        Files.writeString(source, sourceText, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A full JDK is required for the API guide compilation gate");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjects(source.toFile());
            List<String> options = List.of(
                    "--release", "17",
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", outputDirectory.toString());
            boolean compiled = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            assertTrue(compiled, () -> "API guide quickstart failed to compile: " + diagnostics.getDiagnostics());
        }
    }

    private static Path findWorkspace() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null && !Files.isRegularFile(candidate.resolve("settings.gradle"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Could not locate the FandUI workspace");
        }
        return candidate;
    }
}
