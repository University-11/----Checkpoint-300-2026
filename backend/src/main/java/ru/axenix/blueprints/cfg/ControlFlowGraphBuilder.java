package ru.axenix.blueprints.cfg;

import ru.axenix.blueprints.model.BlueprintModels.*;
import java.util.*;

/**
 * Модуль 2: Построитель 2D ориентированного графа потока управления (CFG) и графа вызовов методов (Call Graph).
 */
public class ControlFlowGraphBuilder {

    public static Map<String, List<String>> buildAdjacencyMap(List<CfgEdge> edges) {
        Map<String, List<String>> adj = new LinkedHashMap<>();
        for (CfgEdge edge : edges) {
            adj.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());
        }
        return adj;
    }

    public static List<CallGraphEdge> filterInternalCallGraph(Set<String> declaredMethods, List<CallGraphEdge> rawCalls) {
        List<CallGraphEdge> filtered = new ArrayList<>();
        for (CallGraphEdge edge : rawCalls) {
            if (declaredMethods.contains(edge.calleeMethod()) && !edge.callerMethod().equals(edge.calleeMethod())) {
                filtered.add(edge);
            }
        }
        return filtered;
    }
}
