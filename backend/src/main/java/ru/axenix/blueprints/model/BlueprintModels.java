package ru.axenix.blueprints.model;

import java.util.List;

/**
 * Доменные модели графа потока управления (CFG), графа вызовов, дефектов и результатов верификации.
 */
public final class BlueprintModels {

    private BlueprintModels() {}

    public record CfgNode(
            String id,
            String type,
            String title,
            String code,
            int startLine,
            int endLine,
            int loopDepth,
            String defectBadge
    ) {}

    public record CfgEdge(String from, String to, String label, String edgeType) {}

    public record CallGraphEdge(String callerMethod, String calleeMethod, int line) {}

    public record BonusConstructItem(String category, String constructName, int line, String codeSnippet) {}

    public record DefectFinding(
            String id,
            String severity,
            String ruleId,
            String title,
            String methodName,
            int line,
            String currentBigO,
            String targetBigO,
            String description,
            String risk,
            String recommendation
    ) {}

    public record MethodBlueprint(
            String className,
            String methodName,
            String signature,
            int startLine,
            int endLine,
            int lineCount,
            int maxLoopDepth,
            int cyclomaticComplexity,
            int cognitiveComplexity,
            String estimatedBigO,
            List<CfgNode> nodes,
            List<CfgEdge> edges,
            List<CallGraphEdge> calls
    ) {}

    public record TestResultItem(
            String testId,
            String testName,
            String category,
            boolean passed,
            long baselineMicros,
            long optimizedMicros,
            long decomposedMicros,
            double speedupFactor,
            String details
    ) {}
}
