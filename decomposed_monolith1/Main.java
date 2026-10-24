import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;

/**
 * Модуль ответственности #4 (Декомпозиция монолита — слайд 6 Axenix):
 * Точка входа, координация конвейера и форматированный вывод отчёта.
 */
public class Main {

    public static String renderComprehensiveReport(
            Parser.ParsedInputBundle bundle,
            List<Parser.Order> processedOrders,
            List<Parser.Item> reconciledItems
    ) {
        StringBuilder sb = new StringBuilder();
        try (Formatter fmt = new Formatter(sb, Locale.US)) {
            fmt.format("=== ОТЧЁТ СИСТЕМЫ JAVA BLUEPRINTS (%s) ===%n", Parser.SYSTEM_VERSION);
            fmt.format("Всего клиентов: %d | Всего входных заказов: %d | Успешно обработано: %d%n",
                    bundle.getCustomers().size(),
                    bundle.getOrders().size(),
                    processedOrders.size());

            double totalGross = 0.0;
            double totalTax = 0.0;
            double totalFinal = 0.0;
            for (Parser.Order ord : processedOrders) {
                totalGross += ord.getComputedSubtotal();
                totalTax += ord.getComputedTax();
                totalFinal += ord.getComputedFinalTotal();
            }

            fmt.format("Финансовые итоги (RUB): Нетто=%.2f | НДС=%.2f | Итого к оплате=%.2f%n",
                    totalGross, totalTax, totalFinal);

            double[] stats = Analyzer.computeStatisticalMoments(processedOrders);
            fmt.format("Статистика чека (RUB): Среднее=%.2f | Медиана=%.2f | СтОткл=%.2f | Макс=%.2f%n",
                    stats[0], stats[1], stats[2], stats[3]);

            Set<String> activeRegions = Analyzer.findDistinctActiveRegions(processedOrders);
            fmt.format("Активные регионы (%d): %s%n", activeRegions.size(), String.join(", ", activeRegions));

            fmt.format("--- ДЕТАЛИЗАЦИЯ ЗАКАЗОВ ---%n");
            for (Parser.Order ord : processedOrders) {
                fmt.format("  Заказ %s | Клиент: %s (%s) | Статус: %s | Сумма: %.2f RUB%n",
                        ord.id(),
                        ord.getCustomerId(),
                        ord.getAssignedCustomerName(),
                        ord.getStatus().getRussianLabel(),
                        ord.getComputedFinalTotal());
            }

            fmt.format("--- СВЕРКА СКЛАДСКИХ ПОЗИЦИЙ (RECONCILED SKU) ---%n");
            for (Parser.Item item : reconciledItems) {
                fmt.format("  SKU %s (%s) | Категория: %s | Кол-во: %d | Средняя цена: %.2f | Сумма: %.2f%n",
                        item.id(),
                        item.getSkuName(),
                        item.getCategory(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.lineTotal());
            }

            Map<String, Double> catTotals = Analyzer.computeCategoryTotals(reconciledItems);
            fmt.format("--- ОБОРОТ ПО КАТЕГОРИЯМ ---%n");
            for (Map.Entry<String, Double> entry : catTotals.entrySet()) {
                fmt.format("  Категория %-12s : %.2f RUB%n", entry.getKey(), entry.getValue());
            }

            Optional<Parser.Order> topOrder = Analyzer.findHighestValueOrder(processedOrders);
            if (topOrder.isPresent()) {
                Parser.Order top = topOrder.get();
                fmt.format("Крупнейший заказ: %s на сумму %.2f RUB (%s)%n",
                        top.id(), top.getComputedFinalTotal(), top.getAssignedCustomerName());
            }

            String digest = Optimizer.buildAuditDigest(processedOrders, reconciledItems);
            fmt.format("Контрольный дайджест: %s%n", digest);
        }
        return sb.toString();
    }

    public static String getDefaultDataset() {
        return String.join("\n",
                "# Тестовый пакет корпоративных заказов Axenix Java Blueprints",
                "CUSTOMER|CUST-01|Иванов Иван Иванович|MOSCOW|GOLD|true",
                "CUSTOMER|CUST-02|Петрова Анна Сергеевна|SPB|PLATINUM|true",
                "CUSTOMER|CUST-03|Сидоров Алексей Владимирович|KAZAN|SILVER|true",
                "CUSTOMER|CUST-04|ООО ТехноСнаб|EKATERINBURG|GOLD|true",
                "CUSTOMER|CUST-05|ЗАО СибирьЛогистик|NOVOSIBIRSK|STANDARD|false",
                "ORDER|ORD-1001|CUST-01|RUB|MOSCOW",
                "ORDER|ORD-1002|CUST-01|USD|MOSCOW",
                "ORDER|ORD-1003|CUST-01|RUB|MOSCOW",
                "ORDER|ORD-1004|CUST-02|EUR|SPB",
                "ORDER|ORD-1005|CUST-03|RUB|KAZAN",
                "ORDER|ORD-1006|CUST-04|CNY|EKATERINBURG",
                "ORDER|ORD-1007|CUST-05|RUB|NOVOSIBIRSK",
                "ORDER|ORD-1008|CUST-99|INVALID_CURR|MOSCOW",
                "ITEM|ORD-1001|SKU-10|Серверный модуль А1|HARDWARE|2|18500.00|4.5",
                "ITEM|ORD-1001|SKU-20|Кабель оптический 10м|NETWORK|5|1200.00|0.4",
                "ITEM|ORD-1002|SKU-30|Лицензия СУБД Корпоративная|SOFTWARE|1|450.00|0.1",
                "ITEM|ORD-1003|SKU-10|Серверный модуль А1|HARDWARE|3|18500.00|4.5",
                "ITEM|ORD-1003|SKU-40|Книга Архитектура ПО|BOOKS|4|1500.00|0.8",
                "ITEM|ORD-1004|SKU-50|Медицинский датчик пульса|MEDICAL|10|85.00|0.2",
                "ITEM|ORD-1004|SKU-20|Кабель оптический 10м|NETWORK|3|15.00|0.4",
                "ITEM|ORD-1005|SKU-60|Набор сухого пайка|FOOD|20|650.00|1.2",
                "ITEM|ORD-1006|SKU-10|Серверный модуль А1|HARDWARE|5|1400.00|4.5",
                "ITEM|ORD-1007|SKU-40|Книга Архитектура ПО|BOOKS|2|1500.00|0.8"
        );
    }

    public static String executeDecomposedPipeline(String rawDataset) throws Parser.OrderProcessingException {
        ByteArrayInputStream bais = new ByteArrayInputStream(rawDataset.getBytes(StandardCharsets.UTF_8));
        Parser.ParsedInputBundle bundle = Parser.parseMonolithInput(bais);
        Optimizer.enrichOrdersWithCustomers(bundle.getOrders(), bundle.getCustomers());
        List<Parser.Order> processedOrders = Optimizer.processOrders(bundle.getOrders());
        List<Parser.Item> reconciledItems = Optimizer.reconcileInventoryFast(bundle.getWarehouseItems());
        return renderComprehensiveReport(bundle, processedOrders, reconciledItems);
    }

    public static void main(String[] args) {
        try {
            PrintStream utf8Out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
            String inputData;
            if (args.length > 0 && "--stdin".equals(args[0])) {
                Scanner stdinScanner = new Scanner(System.in, StandardCharsets.UTF_8);
                StringBuilder sb = new StringBuilder();
                while (stdinScanner.hasNextLine()) {
                    sb.append(stdinScanner.nextLine()).append("\n");
                }
                inputData = sb.toString();
            } else {
                inputData = getDefaultDataset();
            }
            utf8Out.print(executeDecomposedPipeline(inputData));
        } catch (Parser.OrderProcessingException ex) {
            System.err.printf(Locale.US, "[ОШИБКА %s] %s%n", ex.getErrorCode(), ex.getMessage());
            System.exit(2);
        } catch (Exception ex) {
            System.err.println("Непредвиденная ошибка выполнения: " + ex.getMessage());
            System.exit(1);
        }
    }
}
