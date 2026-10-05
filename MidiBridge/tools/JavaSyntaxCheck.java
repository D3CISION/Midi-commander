import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import javax.tools.*;

/** Parsing only. Does not resolve Android APIs and is not an Android build. */
public final class JavaSyntaxCheck {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A full JDK is required");
        List<Path> paths;
        try (Stream<Path> stream = Files.walk(Path.of(args[0]))) {
            paths = stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, fm, diagnostics,
                    List.of("--release", "17", "-proc:none"), null, fm.getJavaFileObjectsFromPaths(paths));
            task.parse();
            for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) throw new AssertionError(d.toString());
            }
        }
        System.out.println("PASS Java syntax parsing: " + paths.size()
                + " files. Android API type-checking/build NOT performed.");
    }
}
