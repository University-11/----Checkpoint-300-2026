package ru.axenix.blueprints;

import ru.axenix.blueprints.ast.JavaCompilerAstParser;
import ru.axenix.blueprints.optimizer.AstRecipeOptimizer;

/**
 * Автотест модульной архитектуры бэкенда ru.axenix.blueprints (JUnit 5 / Standalone Runner).
 */
public class QualityGateRunnerTest {
    public static void main(String[] args) throws Exception {
        String sample = "import java.util.*; import java.util.stream.*; public class Sample { public record R(String id) {} public enum E { A, B } public <T> List<T> run(List<T> in) { return in.stream().collect(Collectors.toList()); } }";
        JavaCompilerAstParser parsed = JavaCompilerAstParser.parse("Sample.java", sample);
        if (parsed.bonusConstructs.size() < 3) {
            throw new AssertionError("Expected at least 3 bonus constructs (record, enum, generic/stream)");
        }
        if (AstRecipeOptimizer.getAvailableRecipes().size() < 5) {
            throw new AssertionError("Expected 5 refactoring recipes");
        }
        System.out.println("QualityGateRunnerTest PASSED: detected " + parsed.bonusConstructs.size() + " bonus constructs.");
    }
}
