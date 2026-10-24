package ru.axenix.blueprints.optimizer;

import java.util.*;

/**
 * Модуль 4: Автоматический рецептурный оптимизатор AST (по образцу OpenRewrite / Spoon)
 * и декомпозитор монолита по матрице связности (Single Responsibility Principle).
 */
public class AstRecipeOptimizer {

    public record RefactoringRecipe(
            String recipeId,
            String name,
            String beforeComplexity,
            String afterComplexity,
            String description
    ) {}

    public static List<RefactoringRecipe> getAvailableRecipes() {
        return List.of(
                new RefactoringRecipe(
                        "RECIPE-01-HASHMAP-INDEX",
                        "Индексация вложенного цикла через HashMap",
                        "O(n²)",
                        "O(n)",
                        "Заменяет вложенный цикл подсчёта или поиска по ключу на предварительную однопроходную агрегацию в HashMap<K, V>."
                ),
                new RefactoringRecipe(
                        "RECIPE-02-PAIRWISE-DEDUP",
                        "Устранение повторной обработки одинаковых пар (Слайд 5)",
                        "O(n²)",
                        "O(n log n)",
                        "Трансформирует попарную сверку for(a) { for(b) { if(a.id.equals(b.id)) } } в группировку HashMap + TreeMap."
                ),
                new RefactoringRecipe(
                        "RECIPE-03-STREAM-GROUPING",
                        "Оптимизация Stream-in-Loop через Collectors.groupingBy (Бонусный уровень ★)",
                        "O(n²)",
                        "O(n log n)",
                        "Заменяет повторный вызов .stream().filter(...) внутри цикла на однопроходный коллектор Collectors.groupingBy + summarizingDouble."
                ),
                new RefactoringRecipe(
                        "RECIPE-04-STRINGBUILDER",
                        "Потоковая сборка строк в цикле через StringBuilder",
                        "O(n²)",
                        "O(n)",
                        "Устраняет квадратичное копирование символов при конкатенации '+' внутри цикла (PMD-UseStringBufferForStringAppends)."
                ),
                new RefactoringRecipe(
                        "RECIPE-05-SRP-DECOMPOSITION",
                        "Автоматическая декомпозиция монолита на 4 класса ответственности (Слайд 6)",
                        "1 Монолит (1053L)",
                        "4 Класса SRP",
                        "Кластеризует методы по графу вызовов и типам данных в модули Parser.java, Analyzer.java, Optimizer.java и Main.java."
                )
        );
    }
}
