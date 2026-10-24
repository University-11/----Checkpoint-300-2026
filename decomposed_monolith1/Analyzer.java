import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Модуль ответственности #2 (Декомпозиция монолита — слайд 6 Axenix):
 * Аналитика категорий, регионов, статистических моментов и экстремумов.
 */
public class Analyzer {

    public static Map<String, Double> computeCategoryTotals(Collection<Parser.Item> items) {
        Map<String, Double> categoryTotals = new TreeMap<>();
        if (items == null) return categoryTotals;
        Iterator<Parser.Item> iterator = items.iterator();
        while (iterator.hasNext()) {
            Parser.Item item = iterator.next();
            if (item == null || item.getQuantity() <= 0) continue;
            String cat = item.getCategory();
            double current = categoryTotals.getOrDefault(cat, 0.0);
            categoryTotals.put(cat, Math.round((current + item.lineTotal()) * 100.0) / 100.0);
        }
        return categoryTotals;
    }

    public static Set<String> findDistinctActiveRegions(List<Parser.Order> orders) {
        Set<String> hashRegions = new HashSet<>();
        for (Parser.Order o : orders) {
            if (o != null && o.getStatus() == Parser.OrderStatus.PROCESSED) {
                hashRegions.add(o.getRegion());
            }
        }
        return new TreeSet<>(hashRegions);
    }

    public static Optional<Parser.Order> findHighestValueOrder(List<Parser.Order> orders) {
        if (orders == null || orders.isEmpty()) return Optional.empty();
        return orders.stream()
                .filter(Objects::nonNull)
                .filter(o -> o.getStatus() == Parser.OrderStatus.PROCESSED)
                .max(Comparator.comparingDouble(Parser.Order::getComputedFinalTotal));
    }

    public static double[] computeStatisticalMoments(List<Parser.Order> processedOrders) {
        if (processedOrders == null || processedOrders.isEmpty()) {
            return new double[]{0.0, 0.0, 0.0, 0.0};
        }
        double[] values = new double[processedOrders.size()];
        for (int i = 0; i < processedOrders.size(); i++) {
            values[i] = processedOrders.get(i).getComputedFinalTotal();
        }
        Arrays.sort(values);
        double sum = 0.0;
        for (double v : values) sum += v;
        double mean = sum / values.length;
        double median = (values.length % 2 == 1)
                ? values[values.length / 2]
                : (values[(values.length / 2) - 1] + values[values.length / 2]) / 2.0;

        double varianceAcc = 0.0;
        for (double v : values) {
            double diff = v - mean;
            varianceAcc += diff * diff;
        }
        double stdDev = Math.sqrt(varianceAcc / values.length);
        double maxVal = values[values.length - 1];

        return new double[]{
                Math.round(mean * 100.0) / 100.0,
                Math.round(median * 100.0) / 100.0,
                Math.round(stdDev * 100.0) / 100.0,
                Math.round(maxVal * 100.0) / 100.0
        };
    }
}
