package cn.fandmc.fandui.api;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiArchitectureTest {
    private static final String INTERNAL_PACKAGE = "cn.fandmc.fandui.internal";
    private static final List<String> FORBIDDEN_PACKAGES = List.of(
            "net.minecraft",
            "net.fabricmc",
            "com.mojang.blaze3d",
            "io.github.humbleui.skija",
            "org.lwjgl",
            "org.spongepowered.asm.mixin",
            "org.lwjgl.nanovg");

    @Test
    void publicApiSourcesDoNotReferencePlatformPackages() throws Exception {
        Path sourceRoot = Path.of("src/main/java/cn/fandmc/fandui/api").toAbsolutePath();
        assertTrue(Files.isDirectory(sourceRoot), () -> "Missing API source root: " + sourceRoot);

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                for (String forbidden : FORBIDDEN_PACKAGES) {
                    assertFalse(source.contains(forbidden), () -> file + " references " + forbidden);
                }
            }
        }
    }

    @Test
    void everyPublicTopLevelApiTypeHasJavadoc() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A full JDK is required for the API documentation gate");
        Path sourceRoot = Path.of("src/main/java/cn/fandmc/fandui/api").toAbsolutePath();
        List<Path> sources;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            sources = files.filter(path -> path.toString().endsWith(".java")).toList();
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sources);
            JavacTask task = (JavacTask) compiler.getTask(
                    null, fileManager, diagnostics, List.of("-proc:none"), null, units);
            DocTrees docTrees = DocTrees.instance(task);
            for (CompilationUnitTree unit : task.parse()) {
                for (Tree declaration : unit.getTypeDecls()) {
                    if (declaration instanceof ClassTree type
                            && type.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.PUBLIC)) {
                        TreePath path = TreePath.getPath(unit, type);
                        assertNotNull(docTrees.getDocCommentTree(path),
                                () -> unit.getSourceFile().getName() + " public type lacks Javadoc");
                    }
                }
            }
        }
    }

    @Test
    void compiledApiClassesDoNotContainPlatformPackageReferences() throws Exception {
        Path classesRoot = Path.of(UiKey.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve("cn/fandmc/fandui/api");
        assertTrue(Files.isDirectory(classesRoot), () -> "Missing API classes root: " + classesRoot);

        try (Stream<Path> files = Files.walk(classesRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String constantPoolView = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                for (String forbidden : FORBIDDEN_PACKAGES) {
                    assertFalse(constantPoolView.contains(forbidden), () -> file + " references " + forbidden);
                    String internalName = forbidden.replace('.', '/');
                    assertFalse(constantPoolView.contains(internalName), () -> file + " references " + internalName);
                }
            }
        }
    }

    @Test
    void publicAndProtectedApiSignaturesDoNotReferenceInternalTypes() throws Exception {
        Path classesRoot = apiClassesRoot();
        try (Stream<Path> files = Files.walk(classesRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String relative = classesRoot.relativize(file).toString().replace('\\', '/');
                String className = "cn.fandmc.fandui.api."
                        + relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
                Class<?> type = Class.forName(className, false, getClass().getClassLoader());
                if (isPubliclyReachable(type)) {
                    assertPublicSignatureDoesNotReferenceInternal(type);
                }
            }
        }
    }

    @Test
    void everyMainJavaSourceUsesTheFandUiRootPackage() throws Exception {
        Path workspace = findWorkspace();
        try (Stream<Path> files = Files.walk(workspace)) {
            for (Path file : files.filter(ApiArchitectureTest::isMainJavaSource).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                assertTrue(source.contains("package cn.fandmc.fandui"),
                        () -> file + " is outside cn.fandmc.fandui");
            }
        }
    }

    @Test
    void java17ConsumerCompilesAndLoads(@TempDir Path temporaryDirectory) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A full JDK is required for the Java 17 API consumer gate");
        Path sourceDirectory = Files.createDirectories(temporaryDirectory.resolve("src/fixture"));
        Path outputDirectory = Files.createDirectories(temporaryDirectory.resolve("classes"));
        Path source = sourceDirectory.resolve("Consumer.java");
        Files.writeString(source, """
                package fixture;

                import cn.fandmc.fandui.api.UiKey;
                import cn.fandmc.fandui.api.canvas.Path;
                import cn.fandmc.fandui.api.layout.Rect;
                import cn.fandmc.fandui.api.style.Style;

                public final class Consumer {
                    public static String run() {
                        UiKey key = UiKey.of("fixture", "panel");
                        Style style = Style.builder().opacity(0.5f).build();
                        Path path = Path.builder().rect(new Rect(0.0f, 0.0f, 8.0f, 4.0f)).build();
                        return key + "|" + style.opacity() + "|" + path.bounds().width();
                    }
                }
                """, StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjects(source.toFile());
            List<String> options = List.of(
                    "--release", "17",
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", outputDirectory.toString());
            boolean compiled = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            assertTrue(compiled, () -> "Java 17 consumer compile failed: " + diagnostics.getDiagnostics());
        }

        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{outputDirectory.toUri().toURL()},
                getClass().getClassLoader())) {
            Class<?> consumer = Class.forName("fixture.Consumer", true, loader);
            assertEquals("fixture:panel|0.5|8.0", consumer.getMethod("run").invoke(null));
        }
    }

    private static boolean isMainJavaSource(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.contains("/src/main/java/") && normalized.endsWith(".java");
    }

    private static Path apiClassesRoot() throws Exception {
        return Path.of(UiKey.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve("cn/fandmc/fandui/api");
    }

    private static boolean isPubliclyReachable(Class<?> type) {
        int modifiers = type.getModifiers();
        if (!Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers)) {
            return false;
        }
        Class<?> enclosing = type.getEnclosingClass();
        return enclosing == null || isPubliclyReachable(enclosing);
    }

    private static void assertPublicSignatureDoesNotReferenceInternal(Class<?> type) {
        assertTypes(type + " superclass", type.getGenericSuperclass());
        assertTypes(type + " interfaces", type.getGenericInterfaces());
        assertTypeVariables(type.toString(), type.getTypeParameters());
        assertAnnotations(type.toString(), type);
        assertTypes(type + " permitted subclasses", type.getPermittedSubclasses());

        for (Field field : type.getDeclaredFields()) {
            if (isPublicOrProtected(field.getModifiers())) {
                assertTypes(field.toString(), field.getGenericType());
                assertAnnotations(field.toString(), field);
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (isPublicOrProtected(constructor.getModifiers())) {
                assertExecutable(constructor);
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (isPublicOrProtected(method.getModifiers())) {
                assertTypes(method + " return type", method.getGenericReturnType());
                assertExecutable(method);
            }
        }
        RecordComponent[] components = type.getRecordComponents();
        if (components != null) {
            for (RecordComponent component : components) {
                assertTypes(component.toString(), component.getGenericType());
                assertAnnotations(component.toString(), component);
            }
        }
    }

    private static void assertExecutable(Executable executable) {
        assertTypes(executable + " parameters", executable.getGenericParameterTypes());
        assertTypes(executable + " exceptions", executable.getGenericExceptionTypes());
        assertTypeVariables(executable.toString(), executable.getTypeParameters());
        assertAnnotations(executable.toString(), executable);
    }

    private static boolean isPublicOrProtected(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static void assertTypeVariables(String owner, TypeVariable<?>[] variables) {
        for (TypeVariable<?> variable : variables) {
            assertTypes(owner + " type variable " + variable.getName(), variable.getBounds());
        }
    }

    private static void assertTypes(String owner, Type... types) {
        if (types == null) {
            return;
        }
        for (Type type : types) {
            if (type != null) {
                String signature = type.getTypeName();
                assertFalse(signature.contains(INTERNAL_PACKAGE), () -> owner + " references " + signature);
            }
        }
    }

    private static void assertAnnotations(String owner, AnnotatedElement element) {
        for (Annotation annotation : element.getDeclaredAnnotations()) {
            String annotationType = annotation.annotationType().getName();
            assertFalse(annotationType.startsWith(INTERNAL_PACKAGE),
                    () -> owner + " references annotation " + annotationType);
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
