import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * ============================================================================
 * AXENIX JAVA BLUEPRINTS - BENCHMARK MONOLITH #3 (BONUS SCOPE SHOWCASE)
 * ============================================================================
 * Специализированный эталонный модуль для демонстрации 100% поддержки
 * расширенного (бонусного ★) уровня кейса (Слайд 8):
 *   1. ★ record и enum (TransactionRecord, RiskReport, PaymentChannel, RiskTier)
 *   2. ★ дженерики с ограничениями типов (Bounded Generics: SmartIndex<K extends Comparable<K>, V>)
 *   3. ★ лямбда-выражения и весь пакет java.util.function:
 *        Predicate, Function, BiFunction, Consumer, Supplier, UnaryOperator, BinaryOperator
 *   4. ★ Stream API и Collectors:
 *        groupingBy, partitioningBy, summarizingDouble, mapping, reducing, flatMap, joining
 *   5. Содержит намеренный анти-паттерн O(n^2): повторная фильтрация потока Stream внутри
 *      лямбды .map(...) (Stream-in-Loop O(n^2)), который оптимизируется через Collectors.groupingBy за O(n).
 * ============================================================================
 */
public class BonusScopeShowcaseMonolith {

    // ★ Бонус 1: Enum с полиморфными лямбда-правилами комиссии (Function / UnaryOperator)
    public enum PaymentChannel {
        SBP_QR("СБП QR", amount -> amount * 0.004, 1),
        CORPORATE_CARD("Корпоративная карта", amount -> amount * 0.018 + 15.0, 2),
        WIRE_TRANSFER("Банковский перевод", amount -> Math.min(1500.0, amount * 0.008), 1),
        CROSS_BORDER("Трансграничный перевод", amount -> amount * 0.025 + 120.0, 3);

        private final String titleRu;
        private final DoubleUnaryOperator feeCalculator;
        private final int baseRiskWeight;

        PaymentChannel(String titleRu, DoubleUnaryOperator feeCalculator, int baseRiskWeight) {
            this.titleRu = titleRu;
            this.feeCalculator = feeCalculator;
            this.baseRiskWeight = baseRiskWeight;
        }

        public String getTitleRu() { return titleRu; }
        public double computeFee(double amount) { return Math.round(feeCalculator.applyAsDouble(amount) * 100.0) / 100.0; }
        public int getBaseRiskWeight() { return baseRiskWeight; }
    }

    public enum RiskTier {
        LOW("Низкий"),
        MEDIUM("Средний"),
        HIGH("Высокий"),
        CRITICAL("Критический");

        private final String label;
        RiskTier(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ★ Бонус 2: Record-классы с компактными конструкторами и вычисляемыми методами
    public record TransactionRecord(
            String txId,
            String merchantId,
            String category,
            PaymentChannel channel,
            double amountRub,
            List<String> tags
    ) implements Comparable<TransactionRecord> {
        public TransactionRecord {
            Objects.requireNonNull(txId, "txId cannot be null");
            Objects.requireNonNull(merchantId, "merchantId cannot be null");
            category = (category == null || category.isBlank()) ? "GENERAL" : category.toUpperCase(Locale.ROOT);
            tags = (tags == null) ? List.of() : List.copyOf(tags);
        }

        public double netAmountRub() {
            return Math.round((amountRub - channel.computeFee(amountRub)) * 100.0) / 100.0;
        }

        @Override
        public int compareTo(TransactionRecord other) {
            int cmp = Double.compare(other.amountRub, this.amountRub);
            return cmp != 0 ? cmp : this.txId.compareTo(other.txId);
        }
    }

    public record MerchantAnalyticsSummary(
            String merchantId,
            long txCount,
            double grossVolumeRub,
            double netVolumeRub,
            double maxSingleTxRub,
            RiskTier riskTier
    ) {}

    // ★ Бонус 3: Обобщённый (Generic) параметризованный индекс с ограничениями типов
    public static class SmartGenericIndex<K extends Comparable<K>, V> implements Iterable<Map.Entry<K, List<V>>> {
        private final Map<K, List<V>> backingMap = new TreeMap<>();

        public void putItem(K key, V value) {
            backingMap.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }

        public List<V> getItems(K key) {
            return backingMap.getOrDefault(key, Collections.emptyList());
        }

        public <R> Map<K, R> transformValues(Function<List<V>, R> transformer) {
            Map<K, R> out = new TreeMap<>();
            for (Map.Entry<K, List<V>> entry : backingMap.entrySet()) {
                out.put(entry.getKey(), transformer.apply(entry.getValue()));
            }
            return out;
        }

        public List<V> filterAll(Predicate<V> predicate) {
            return backingMap.values().stream()
                    .flatMap(Collection::stream)
                    .filter(predicate)
                    .collect(Collectors.toList());
        }

        @Override
        public Iterator<Map.Entry<K, List<V>>> iterator() {
            return backingMap.entrySet().iterator();
        }
    }

    // ★ Бонус 4: Функциональный конвейер на java.util.function (Predicate, BiFunction, UnaryOperator, BinaryOperator, Supplier, Consumer)
    public static BiFunction<Double, Integer, RiskTier> buildRiskClassifier(double highVolumeThreshold) {
        return (totalVolume, highRiskCount) -> {
            if (totalVolume >= highVolumeThreshold * 2.0 || highRiskCount >= 3) {
                return RiskTier.CRITICAL;
            } else if (totalVolume >= highVolumeThreshold || highRiskCount >= 2) {
                return RiskTier.HIGH;
            } else if (totalVolume >= highVolumeThreshold * 0.4 || highRiskCount == 1) {
                return RiskTier.MEDIUM;
            }
            return RiskTier.LOW;
        };
    }

    // ДЕФЕКТ O(n^2) В STREAM API: Вложенный повторный проход txs.stream().filter(...) внутри .map(...)
    public static List<MerchantAnalyticsSummary> analyzeMerchantsStreamSlow(List<TransactionRecord> txs) {
        if (txs == null || txs.isEmpty()) {
            return List.of();
        }
        BiFunction<Double, Integer, RiskTier> classifier = buildRiskClassifier(100_000.0);
        Predicate<TransactionRecord> isCrossBorder = tx -> tx.channel() == PaymentChannel.CROSS_BORDER;

        // O(n^2) дефект: для каждого уникального merchantId заново фильтруется весь список txs
        List<String> distinctMerchants = txs.stream()
                .map(TransactionRecord::merchantId)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<MerchantAnalyticsSummary> result = new ArrayList<>();
        for (String mid : distinctMerchants) {
            long count = 0;
            double gross = 0.0;
            double net = 0.0;
            double maxTx = 0.0;
            int crossBorderCount = 0;
            for (TransactionRecord candidate : txs) {
                if (candidate.merchantId().equals(mid)) {
                    count++;
                    gross += candidate.amountRub();
                    net += candidate.netAmountRub();
                    if (candidate.amountRub() > maxTx) {
                        maxTx = candidate.amountRub();
                    }
                    if (isCrossBorder.test(candidate)) {
                        crossBorderCount++;
                    }
                }
            }
            result.add(new MerchantAnalyticsSummary(
                    mid,
                    count,
                    Math.round(gross * 100.0) / 100.0,
                    Math.round(net * 100.0) / 100.0,
                    Math.round(maxTx * 100.0) / 100.0,
                    classifier.apply(gross, crossBorderCount)
            ));
        }
        return result;
    }

    // ОПТИМИЗИРОВАННАЯ ВЕРСИЯ O(n log n) через Collectors.groupingBy + summarizingDouble
    public static List<MerchantAnalyticsSummary> analyzeMerchantsStreamFast(List<TransactionRecord> txs) {
        if (txs == null || txs.isEmpty()) {
            return List.of();
        }
        BiFunction<Double, Integer, RiskTier> classifier = buildRiskClassifier(100_000.0);
        Predicate<TransactionRecord> isCrossBorder = tx -> tx.channel() == PaymentChannel.CROSS_BORDER;

        Map<String, List<TransactionRecord>> grouped = txs.stream()
                .collect(Collectors.groupingBy(TransactionRecord::merchantId, TreeMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> {
                    String mid = entry.getKey();
                    List<TransactionRecord> list = entry.getValue();
                    DoubleSummaryStatistics stats = list.stream()
                            .collect(Collectors.summarizingDouble(TransactionRecord::amountRub));
                    double net = list.stream()
                            .mapToDouble(TransactionRecord::netAmountRub)
                            .reduce(0.0, Double::sum);
                    int crossBorderCount = (int) list.stream().filter(isCrossBorder).count();
                    double gross = Math.round(stats.getSum() * 100.0) / 100.0;
                    return new MerchantAnalyticsSummary(
                            mid,
                            stats.getCount(),
                            gross,
                            Math.round(net * 100.0) / 100.0,
                            Math.round(stats.getMax() * 100.0) / 100.0,
                            classifier.apply(gross, crossBorderCount)
                    );
                })
                .collect(Collectors.toList());
    }

    // Демонстрация всех видов Collectors: partitioningBy, mapping, flatMap, joining
    public static String buildStreamCollectorsReport(List<TransactionRecord> txs, boolean optimized) {
        List<MerchantAnalyticsSummary> summaries = optimized
                ? analyzeMerchantsStreamFast(txs)
                : analyzeMerchantsStreamSlow(txs);

        Map<Boolean, List<TransactionRecord>> partitionedVip = txs.stream()
                .collect(Collectors.partitioningBy(tx -> tx.amountRub() >= 50_000.0));

        String uniqueTagsJoined = txs.stream()
                .flatMap(tx -> tx.tags().stream())
                .map(String::toUpperCase)
                .distinct()
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));

        Map<String, Double> netByCategory = txs.stream()
                .collect(Collectors.groupingBy(
                        TransactionRecord::category,
                        TreeMap::new,
                        Collectors.reducing(0.0, TransactionRecord::netAmountRub, Double::sum)
                ));

        StringBuilder sb = new StringBuilder();
        sb.append("MERCHANTS=");
        for (MerchantAnalyticsSummary s : summaries) {
            sb.append(String.format(Locale.US, "%s(cnt=%d,gross=%.2f,net=%.2f,risk=%s);",
                    s.merchantId(), s.txCount(), s.grossVolumeRub(), s.netVolumeRub(), s.riskTier().name()));
        }
        sb.append("|VIP_COUNT=").append(partitionedVip.get(true).size());
        sb.append("|STD_COUNT=").append(partitionedVip.get(false).size());
        sb.append("|TAGS=").append(uniqueTagsJoined);
        sb.append("|CATS=");
        for (Map.Entry<String, Double> e : netByCategory.entrySet()) {
            sb.append(String.format(Locale.US, "%s:%.2f,", e.getKey(), e.getValue()));
        }
        return sb.toString();
    }

    public static List<TransactionRecord> getDefaultTransactions() {
        Supplier<List<TransactionRecord>> supplier = () -> List.of(
                new TransactionRecord("TX-01", "M-ALPHA", "CLOUD", PaymentChannel.SBP_QR, 42000.0, List.of("b2b", "fast")),
                new TransactionRecord("TX-02", "M-ALPHA", "CLOUD", PaymentChannel.CORPORATE_CARD, 68000.0, List.of("b2b", "vip")),
                new TransactionRecord("TX-03", "M-BETA", "HARDWARE", PaymentChannel.CROSS_BORDER, 95000.0, List.of("import", "fx")),
                new TransactionRecord("TX-04", "M-BETA", "HARDWARE", PaymentChannel.CROSS_BORDER, 115000.0, List.of("import", "priority")),
                new TransactionRecord("TX-05", "M-GAMMA", "BOOKS", PaymentChannel.WIRE_TRANSFER, 18500.0, List.of("edu")),
                new TransactionRecord("TX-06", "M-ALPHA", "SOFTWARE", PaymentChannel.CROSS_BORDER, 54000.0, List.of("license", "fx"))
        );
        return supplier.get();
    }

    public static String runBonusPipeline(boolean optimized) {
        return buildStreamCollectorsReport(getDefaultTransactions(), optimized);
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        out.println(runBonusPipeline(false));
    }
}
