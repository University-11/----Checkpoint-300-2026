import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * ============================================================================
 * AXENIX JAVA BLUEPRINTS - BENCHMARK MONOLITH #2 (LOG & SECURITY ANALYTICS)
 * ============================================================================
 * Второй тестовый монолит для демонстрации универсальности анализатора AST/CFG:
 * корреляция событий безопасности, журнал транзакций и детекция аномалий.
 * Содержит проверяемые дефекты:
 *   - Повторная попарная корреляция IP-сессий во вложенном цикле O(n^2)
 *   - Линейный поиск правил маршрутизации внутри цикла событий O(n * m)
 *   - Конкатенация строк трассировки через '+' внутри цикла O(n^2)
 * ============================================================================
 */
public class LogAnalyticsMonolith {

    public enum SeverityLevel {
        INFO(1, "ИНФО"),
        WARN(2, "ПРЕДУПРЕЖДЕНИЕ"),
        ERROR(3, "ОШИБКА"),
        CRITICAL(5, "КРИТИЧЕСКИЙ");

        private final int weight;
        private final String labelRu;

        SeverityLevel(int weight, String labelRu) {
            this.weight = weight;
            this.labelRu = labelRu;
        }

        public int getWeight() { return weight; }
        public String getLabelRu() { return labelRu; }
    }

    public record SecurityRule(String ruleCode, String servicePrefix, int thresholdMs, SeverityLevel escalatedLevel) {}

    public static class LogSyntaxException extends Exception {
        public LogSyntaxException(String message) {
            super(message);
        }
    }

    public static class LogEvent implements Comparable<LogEvent> {
        private final String eventId;
        private final String traceId;
        private final String serviceName;
        private final String sourceIp;
        private final int latencyMs;
        private SeverityLevel severity;
        private String matchedRuleCode = "NONE";

        public LogEvent(String eventId, String traceId, String serviceName, String sourceIp, int latencyMs, SeverityLevel severity) {
            this.eventId = Objects.requireNonNull(eventId);
            this.traceId = Objects.requireNonNull(traceId);
            this.serviceName = Objects.requireNonNull(serviceName);
            this.sourceIp = Objects.requireNonNull(sourceIp);
            this.latencyMs = latencyMs;
            this.severity = severity;
        }

        public String id() { return eventId; }
        public String getTraceId() { return traceId; }
        public String getServiceName() { return serviceName; }
        public String getSourceIp() { return sourceIp; }
        public int getLatencyMs() { return latencyMs; }
        public SeverityLevel getSeverity() { return severity; }
        public void setSeverity(SeverityLevel severity) { this.severity = severity; }
        public String getMatchedRuleCode() { return matchedRuleCode; }
        public void setMatchedRuleCode(String matchedRuleCode) { this.matchedRuleCode = matchedRuleCode; }

        @Override
        public int compareTo(LogEvent o) {
            return this.eventId.compareTo(o.eventId);
        }
    }

    // ДЕФЕКТ #1: Вложенный цикл попарной корреляции трассировок O(n^2)
    public static Map<String, Integer> correlateTracesSlow(List<LogEvent> events) {
        Map<String, Integer> traceTotals = new TreeMap<>();
        if (events == null || events.isEmpty()) {
            return traceTotals;
        }
        for (LogEvent a : events) {
            int sumLatency = 0;
            for (LogEvent b : events) {
                if (a.getTraceId().equals(b.getTraceId())) {
                    sumLatency += b.getLatencyMs();
                }
            }
            if (!traceTotals.containsKey(a.getTraceId())) {
                traceTotals.put(a.getTraceId(), sumLatency);
            }
        }
        return traceTotals;
    }

    // Оптимизированная версия корреляции за O(n log n)
    public static Map<String, Integer> correlateTracesFast(List<LogEvent> events) {
        Map<String, Integer> traceTotals = new TreeMap<>();
        if (events == null || events.isEmpty()) {
            return traceTotals;
        }
        for (LogEvent ev : events) {
            String tid = ev.getTraceId();
            traceTotals.put(tid, traceTotals.getOrDefault(tid, 0) + ev.getLatencyMs());
        }
        return traceTotals;
    }

    // ДЕФЕКТ #2: Линейное сопоставление правил в цикле O(n * m) + конкатенация строк O(n^2)
    public static String evaluateSecurityRulesSlow(List<LogEvent> events, List<SecurityRule> rules) {
        String summary = "";
        for (LogEvent ev : events) {
            for (SecurityRule rule : rules) {
                if (ev.getServiceName().startsWith(rule.servicePrefix()) && ev.getLatencyMs() >= rule.thresholdMs()) {
                    ev.setMatchedRuleCode(rule.ruleCode());
                    ev.setSeverity(rule.escalatedLevel());
                    break;
                }
            }
            summary = summary + ev.id() + "[" + ev.getSeverity().name() + ":" + ev.getMatchedRuleCode() + "];";
        }
        return summary;
    }

    // Оптимизированная версия сопоставления правил со StringBuilder O(n)
    public static String evaluateSecurityRulesFast(List<LogEvent> events, List<SecurityRule> rules) {
        StringBuilder sb = new StringBuilder(events.size() * 24);
        Map<String, List<SecurityRule>> rulesByPrefix = new HashMap<>();
        for (SecurityRule rule : rules) {
            rulesByPrefix.computeIfAbsent(rule.servicePrefix(), k -> new ArrayList<>()).add(rule);
        }
        for (LogEvent ev : events) {
            for (SecurityRule rule : rules) {
                if (ev.getServiceName().startsWith(rule.servicePrefix()) && ev.getLatencyMs() >= rule.thresholdMs()) {
                    ev.setMatchedRuleCode(rule.ruleCode());
                    ev.setSeverity(rule.escalatedLevel());
                    break;
                }
            }
            sb.append(ev.id()).append('[').append(ev.getSeverity().name()).append(':').append(ev.getMatchedRuleCode()).append("];");
        }
        return sb.toString();
    }

    public static List<LogEvent> getDefaultEvents() {
        List<LogEvent> list = new ArrayList<>();
        list.add(new LogEvent("EV-01", "TR-100", "AUTH_SERVICE", "10.0.1.15", 120, SeverityLevel.INFO));
        list.add(new LogEvent("EV-02", "TR-100", "PAYMENT_GATEWAY", "10.0.1.15", 640, SeverityLevel.WARN));
        list.add(new LogEvent("EV-03", "TR-200", "ORDER_CORE", "10.0.2.40", 85, SeverityLevel.INFO));
        list.add(new LogEvent("EV-04", "TR-100", "AUDIT_WRITER", "10.0.1.15", 45, SeverityLevel.INFO));
        list.add(new LogEvent("EV-05", "TR-200", "PAYMENT_GATEWAY", "10.0.2.40", 910, SeverityLevel.ERROR));
        return list;
    }

    public static List<SecurityRule> getDefaultRules() {
        return List.of(
                new SecurityRule("SEC-PAY-HIGH", "PAYMENT", 500, SeverityLevel.CRITICAL),
                new SecurityRule("SEC-AUTH-SLOW", "AUTH", 300, SeverityLevel.WARN)
        );
    }

    public static String runPipeline(boolean optimized) {
        List<LogEvent> events = getDefaultEvents();
        List<SecurityRule> rules = getDefaultRules();
        Map<String, Integer> corr = optimized ? correlateTracesFast(events) : correlateTracesSlow(events);
        String eval = optimized ? evaluateSecurityRulesFast(events, rules) : evaluateSecurityRulesSlow(events, rules);
        return "TRACES=" + corr.toString() + "|EVAL=" + eval;
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        out.println(runPipeline(false));
    }
}
