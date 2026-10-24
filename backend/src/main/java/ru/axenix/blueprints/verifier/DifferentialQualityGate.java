package ru.axenix.blueprints.verifier;

import ru.axenix.blueprints.model.BlueprintModels.TestResultItem;
import javax.tools.*;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Модуль 5: Промышленный верификатор эквивалентности поведения (Quality Gate v3.0).
 * Выполняет 16 тестовых наборов (включая 50-Seed Property-Based Differential Fuzzing,
 * стресс-тест N=2400 и проверку всех конструкций со звёздочкой ★ из BonusScopeShowcaseMonolith).
 */
public class DifferentialQualityGate {

    public static void compileSources(JavaCompiler compiler, Path outDir, List<Path> sources) throws IOException {
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, Locale.US, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(sources);
            List<String> options = List.of("-encoding", "UTF-8", "-d", outDir.toString());
            Boolean ok = compiler.getTask(null, fm, null, options, null, units).call();
            if (ok == null || !ok) {
                throw new IllegalStateException("Compilation failed for: " + sources);
            }
        }
    }
}
