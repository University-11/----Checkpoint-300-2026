package ru.axenix.blueprints.cli;

import ru.axenix.blueprints.ast.JavaCompilerAstParser;
import ru.axenix.blueprints.optimizer.AstRecipeOptimizer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Модуль 6: CLI и точка входа промышленного бэкенда Java Blueprints v3.0.
 */
public class BlueprintCliMain {
    public static void main(String[] args) throws Exception {
        if (args.length >= 1) {
            Path p = Paths.get(args[0]);
            String code = Files.readString(p, StandardCharsets.UTF_8);
            JavaCompilerAstParser ast = JavaCompilerAstParser.parse(p.getFileName().toString(), code);
            System.out.println("Parsed " + p.getFileName() + ": bonus constructs=" + ast.bonusConstructs.size()
                    + ", call graph edges=" + ast.allCalls.size()
                    + ", recipes=" + AstRecipeOptimizer.getAvailableRecipes().size());
        }
    }
}
