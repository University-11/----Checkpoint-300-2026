package ru.axenix.blueprints.diagnostics;

import ru.axenix.blueprints.model.BlueprintModels.*;
import java.util.*;

/**
 * Модуль 3: Детектор алгоритмических дефектов Big-O, метрик McCabe Cyclomatic и SonarSource Cognitive Complexity (S3776).
 */
public class ComplexityAndDefectDetector {

    public static final int SONAR_COGNITIVE_THRESHOLD = 15;
    public static final int PMD_LONG_METHOD_LINES = 38;

    public static String estimateAsymptoticBigO(int maxLoopDepth, boolean hasSortingOrTreeMap) {
        if (maxLoopDepth <= 0) return "O(1)";
        if (maxLoopDepth == 1) return hasSortingOrTreeMap ? "O(n log n)" : "O(n)";
        if (maxLoopDepth == 2) return "O(n²)";
        return "O(n^" + maxLoopDepth + ")";
    }
}
