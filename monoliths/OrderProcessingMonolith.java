import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * AXENIX JAVA BLUEPRINTS - BENCHMARK MONOLITH #1 (~1000 LINES)
 * ============================================================================
 * Монолитная система обработки заказов, складского учёта и аналитики рисков.
 * Содержит намеренные архитектурные и алгоритмические дефекты для диагностики:
 *   1. DEF-01 [Высокая] O(n^2) Вложенный цикл в processOrders (дублирование и поиск)
 *   2. DEF-02 [Высокая] O(n^2) Повторная попарная сверка элементов в reconcileInventory (a.id().equals(b.id()))
 *   3. DEF-03 [Средняя] O(n^2) Поиск клиентов линейным перебором в joinCustomerProfiles
 *   4. DEF-04 [Средняя] O(n^2) Конкатенация строк через '+=' внутри цикла в buildCsvAuditLog
 *   5. DEF-05 [Высокая] Длинный метод и смешение ответственностей (Парсинг + Валидация + Аналитика + Вывод)
 *   6. DEF-06 [Средняя] Дублирование логики проверки валидности заказа в processOrders и validateBatch
 * ============================================================================
 */
public class OrderProcessingMonolith {

    // --- Статические константы и конфигурация ---
    public static final String SYSTEM_VERSION = "2.4.0-MONOLITH";
    public static final double VIP_THRESHOLD = 50000.0;
    public static final double STANDARD_TAX_RATE = 0.20;
    public static final double REDUCED_TAX_RATE = 0.10;
    public static final int MAX_LINE_ITEMS = 250;
    public static final Set<String> SUPPORTED_CURRENCIES = Set.of("RUB", "USD", "EUR", "CNY");
    public static final List<String> PRIORITY_REGIONS = List.of("MOSCOW", "SPB", "EKATERINBURG", "KAZAN", "NOVOSIBIRSK");
    public static final Map<String, Double> CURRENCY_RATES = Map.of(
            "RUB", 1.0,
            "USD", 92.5,
            "EUR", 100.0,
            "CNY", 12.8
    );

    private static int totalProcessedRuns = 0;

    // --- Бонусный уровень: Enum ---
    public enum OrderStatus {
        NEW("Новый"),
        VALIDATED("Проверен"),
        PROCESSED("Обработан"),
        MERGED("Объединён"),
        REJECTED("Отклонён"),
        ARCHIVED("В архиве");

        private final String russianLabel;

        OrderStatus(String russianLabel) {
            this.russianLabel = russianLabel;
        }

        public String getRussianLabel() {
            return russianLabel;
        }
    }

    public enum CustomerTier {
        STANDARD(0.00),
        SILVER(0.03),
        GOLD(0.07),
        PLATINUM(0.12);

        private final double discountRate;

        CustomerTier(double discountRate) {
            this.discountRate = discountRate;
        }

        public double getDiscountRate() {
            return discountRate;
        }
    }

    // --- Бонусный уровень: Record ---
    public record AuditEntry(String code, String message, long timestampOrder, int severity) {
        public String formatEntry() {
            return String.format(Locale.US, "[SEV-%d #%04d] %s: %s", severity, timestampOrder, code, message);
        }
    }

    public record MetricSnapshot(
            int totalOrders,
            int validOrders,
            int rejectedOrders,
            int mergedItems,
            double grossRevenueRub,
            double netRevenueRub,
            double taxTotalRub,
            long executionMicros
    ) {}

    // --- Раздел 4: Собственные проверяемые и непроверяемые исключения ---
    public static class OrderProcessingException extends Exception {
        private final String errorCode;

        public OrderProcessingException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public OrderProcessingException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    public static class InvalidOrderSyntaxException extends OrderProcessingException {
        private final int lineNumber;

        public InvalidOrderSyntaxException(int lineNumber, String message) {
            super("SYNTAX_ERR", "Строка " + lineNumber + ": " + message);
            this.lineNumber = lineNumber;
        }

        public int getLineNumber() {
            return lineNumber;
        }
    }

    public static class DuplicateOrderStateException extends RuntimeException {
        public DuplicateOrderStateException(String message) {
            super(message);
        }
    }

    // --- Раздел 1: Пользовательские интерфейсы и дженерики ---
    public interface Identifiable<K extends Comparable<K>> {
        K id();
    }

    public interface Validatable {
        boolean isValid();
        List<String> validationErrors();
    }

    public interface Priceable {
        double calculateRawTotal();
        double calculateFinalTotal();
    }

    // --- Пользовательские классы предметной области ---
    public static class Item implements Identifiable<String>, Comparable<Item> {
        private final String itemId;
        private final String skuName;
        private final String category;
        private int quantity;
        private double unitPrice;
        private double weightKg;

        public Item(String itemId, String skuName, String category, int quantity, double unitPrice, double weightKg) {
            this.itemId = Objects.requireNonNull(itemId, "itemId не может быть null");
            this.skuName = Objects.requireNonNull(skuName, "skuName не может быть null");
            this.category = category == null ? "GENERAL" : category.toUpperCase(Locale.ROOT);
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.weightKg = weightKg;
        }

        @Override
        public String id() {
            return itemId;
        }

        public String getSkuName() {
            return skuName;
        }

        public String getCategory() {
            return category;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public double getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(double unitPrice) {
            this.unitPrice = unitPrice;
        }

        public double getWeightKg() {
            return weightKg;
        }

        public double lineTotal() {
            return quantity * unitPrice;
        }

        public Item copy() {
            return new Item(itemId, skuName, category, quantity, unitPrice, weightKg);
        }

        @Override
        public int compareTo(Item other) {
            int cmp = this.itemId.compareTo(other.itemId);
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(this.quantity, other.quantity);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Item item)) return false;
            return quantity == item.quantity
                    && Double.compare(item.unitPrice, unitPrice) == 0
                    && Objects.equals(itemId, item.itemId)
                    && Objects.equals(skuName, item.skuName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemId, skuName, quantity, unitPrice);
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "Item[%s, %s, qty=%d, price=%.2f]", itemId, skuName, quantity, unitPrice);
        }
    }

    public static class CustomerProfile implements Identifiable<String> {
        private final String customerId;
        private final String fullName;
        private final String region;
        private final CustomerTier tier;
        private final boolean active;

        public CustomerProfile(String customerId, String fullName, String region, CustomerTier tier, boolean active) {
            this.customerId = customerId;
            this.fullName = fullName;
            this.region = region;
            this.tier = tier;
            this.active = active;
        }

        @Override
        public String id() {
            return customerId;
        }

        public String getFullName() {
            return fullName;
        }

        public String getRegion() {
            return region;
        }

        public CustomerTier getTier() {
            return tier;
        }

        public boolean isActive() {
            return active;
        }
    }

    public static class Order implements Identifiable<String>, Validatable, Priceable {
        private final String orderId;
        private final String customerId;
        private final String currency;
        private final String region;
        private final List<Item> items;
        private OrderStatus status;
        private double discountPercent;
        private double computedSubtotal;
        private double computedTax;
        private double computedFinalTotal;
        private String assignedCustomerName;

        public Order(String orderId, String customerId, String currency, String region) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.currency = currency;
            this.region = region;
            this.items = new ArrayList<>();
            this.status = OrderStatus.NEW;
            this.discountPercent = 0.0;
            this.assignedCustomerName = "UNKNOWN";
        }

        @Override
        public String id() {
            return orderId;
        }

        public String getCustomerId() {
            return customerId;
        }

        public String getCurrency() {
            return currency;
        }

        public String getRegion() {
            return region;
        }

        public List<Item> getItems() {
            return items;
        }

        public void addItem(Item item) {
            if (item != null) {
                this.items.add(item);
            }
        }

        public OrderStatus getStatus() {
            return status;
        }

        public void setStatus(OrderStatus status) {
            this.status = status;
        }

        public double getDiscountPercent() {
            return discountPercent;
        }

        public void setDiscountPercent(double discountPercent) {
            this.discountPercent = discountPercent;
        }

        public double getComputedSubtotal() {
            return computedSubtotal;
        }

        public void setComputedSubtotal(double computedSubtotal) {
            this.computedSubtotal = computedSubtotal;
        }

        public double getComputedTax() {
            return computedTax;
        }

        public void setComputedTax(double computedTax) {
            this.computedTax = computedTax;
        }

        public double getComputedFinalTotal() {
            return computedFinalTotal;
        }

        public void setComputedFinalTotal(double computedFinalTotal) {
            this.computedFinalTotal = computedFinalTotal;
        }

        public String getAssignedCustomerName() {
            return assignedCustomerName;
        }

        public void setAssignedCustomerName(String assignedCustomerName) {
            this.assignedCustomerName = assignedCustomerName;
        }

        @Override
        public boolean isValid() {
            return validationErrors().isEmpty();
        }

        @Override
        public List<String> validationErrors() {
            List<String> errs = new ArrayList<>();
            if (orderId == null || orderId.trim().isEmpty()) {
                errs.add("Пустой идентификатор заказа");
            }
            if (customerId == null || customerId.trim().isEmpty()) {
                errs.add("Пустой идентификатор клиента");
            }
            if (currency == null || !SUPPORTED_CURRENCIES.contains(currency)) {
                errs.add("Неподдерживаемая валюта: " + currency);
            }
            if (items.isEmpty()) {
                errs.add("Заказ не содержит позиций");
            }
            if (items.size() > MAX_LINE_ITEMS) {
                errs.add("Превышен лимит позиций заказа");
            }
            for (Item item : items) {
                if (item.getQuantity() <= 0) {
                    errs.add("Некорректное количество для SKU " + item.id());
                }
                if (item.getUnitPrice() < 0.0 || Double.isNaN(item.getUnitPrice())) {
                    errs.add("Некорректная цена для SKU " + item.id());
                }
            }
            return errs;
        }

        @Override
        public double calculateRawTotal() {
            double sum = 0.0;
            for (Item item : items) {
                sum += item.lineTotal();
            }
            return sum;
        }

        @Override
        public double calculateFinalTotal() {
            return computedFinalTotal;
        }

        public Order deepCopy() {
            Order copy = new Order(orderId, customerId, currency, region);
            copy.setStatus(status);
            copy.setDiscountPercent(discountPercent);
            copy.setComputedSubtotal(computedSubtotal);
            copy.setComputedTax(computedTax);
            copy.setComputedFinalTotal(computedFinalTotal);
            copy.setAssignedCustomerName(assignedCustomerName);
            for (Item it : items) {
                copy.addItem(it.copy());
            }
            return copy;
        }
    }

    // =========================================================================
    // Секция 1: Парсинг входного потока (StandardCharsets.UTF_8, Scanner, try/catch)
    // =========================================================================
    public static class ParsedInputBundle {
        private final List<CustomerProfile> customers = new ArrayList<>();
        private final List<Order> orders = new ArrayList<>();
        private final List<Item> warehouseItems = new ArrayList<>();
        private final List<AuditEntry> auditLog = new ArrayList<>();

        public List<CustomerProfile> getCustomers() {
            return customers;
        }

        public List<Order> getOrders() {
            return orders;
        }

        public List<Item> getWarehouseItems() {
            return warehouseItems;
        }

        public List<AuditEntry> getAuditLog() {
            return auditLog;
        }
    }

    public static ParsedInputBundle parseMonolithInput(InputStream inputStream) throws InvalidOrderSyntaxException {
        ParsedInputBundle bundle = new ParsedInputBundle();
        Map<String, Order> orderIndex = new LinkedHashMapCompat<>();
        Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8);
        int lineNum = 0;
        long auditCounter = 1L;

        try {
            while (scanner.hasNextLine()) {
                lineNum++;
                String rawLine = scanner.nextLine();
                if (rawLine == null) {
                    continue;
                }
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("\\|");
                String recordType = parts[0].trim().toUpperCase(Locale.ROOT);

                switch (recordType) {
                    case "CUSTOMER": {
                        if (parts.length < 6) {
                            throw new InvalidOrderSyntaxException(lineNum, "Ожидалось 6 полей для CUSTOMER");
                        }
                        String cid = parts[1].trim();
                        String name = parts[2].trim();
                        String reg = parts[3].trim().toUpperCase(Locale.ROOT);
                        CustomerTier tier = parseTierSafe(parts[4].trim());
                        boolean active = Boolean.parseBoolean(parts[5].trim());
                        bundle.getCustomers().add(new CustomerProfile(cid, name, reg, tier, active));
                        break;
                    }
                    case "ORDER": {
                        if (parts.length < 5) {
                            throw new InvalidOrderSyntaxException(lineNum, "Ожидалось 5 полей для ORDER");
                        }
                        String oid = parts[1].trim();
                        String cid = parts[2].trim();
                        String curr = parts[3].trim().toUpperCase(Locale.ROOT);
                        String reg = parts[4].trim().toUpperCase(Locale.ROOT);
                        Order order = new Order(oid, cid, curr, reg);
                        orderIndex.put(oid, order);
                        bundle.getOrders().add(order);
                        break;
                    }
                    case "ITEM": {
                        if (parts.length < 8) {
                            throw new InvalidOrderSyntaxException(lineNum, "Ожидалось 8 полей для ITEM");
                        }
                        String oid = parts[1].trim();
                        String itemId = parts[2].trim();
                        String skuName = parts[3].trim();
                        String category = parts[4].trim();
                        int qty = parseIntChecked(parts[5].trim(), lineNum);
                        double price = parseDoubleChecked(parts[6].trim(), lineNum);
                        double weight = parseDoubleChecked(parts[7].trim(), lineNum);
                        Item item = new Item(itemId, skuName, category, qty, price, weight);
                        Order targetOrder = orderIndex.get(oid);
                        if (targetOrder != null) {
                            targetOrder.addItem(item);
                        }
                        bundle.getWarehouseItems().add(item.copy());
                        break;
                    }
                    default: {
                        bundle.getAuditLog().add(new AuditEntry(
                                "UNKNOWN_REC",
                                "Пропущен неизвестный тип записи: " + recordType,
                                auditCounter++,
                                2
                        ));
                        break;
                    }
                }
            }
        } finally {
            // Завершение чтения потока
            bundle.getAuditLog().add(new AuditEntry(
                    "PARSE_DONE",
                    "Разобрано строк: " + lineNum + ", заказов: " + bundle.getOrders().size(),
                    auditCounter,
                    1
            ));
        }

        return bundle;
    }

    // Вспомогательная совместимая обёртка над HashMap с сохранением порядка ключей
    private static class LinkedHashMapCompat<K, V> extends HashMap<K, V> {
        private final List<K> insertionOrder = new ArrayList<>();

        @Override
        public V put(K key, V value) {
            if (!containsKey(key)) {
                insertionOrder.add(key);
            }
            return super.put(key, value);
        }

        public List<K> keysInOrder() {
            return Collections.unmodifiableList(insertionOrder);
        }
    }

    private static CustomerTier parseTierSafe(String raw) {
        try {
            return CustomerTier.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return CustomerTier.STANDARD;
        }
    }

    private static int parseIntChecked(String raw, int lineNum) throws InvalidOrderSyntaxException {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new InvalidOrderSyntaxException(lineNum, "Ошибка разбора целого числа: " + raw);
        }
    }

    private static double parseDoubleChecked(String raw, int lineNum) throws InvalidOrderSyntaxException {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            throw new InvalidOrderSyntaxException(lineNum, "Ошибка разбора дробного числа: " + raw);
        }
    }

    // =========================================================================
    // Секция 2: ДЕФЕКТ #1 и #6 — Метод processOrders из концепта Axenix (Стр. 4)
    // Вложенный цикл O(n^2) для поиска дубликатов и агрегации + дублирование проверки валидности
    // =========================================================================
    public static List<Order> processOrders(List<Order> orders) {
        List<Order> result = new ArrayList<>();
        if (orders == null || orders.isEmpty()) {
            return result;
        }

        for (Order order : orders) {
            // Дублирующаяся ручная проверка валидности (DEF-06)
            boolean manualValid = order != null
                    && order.id() != null
                    && !order.id().trim().isEmpty()
                    && order.getCustomerId() != null
                    && !order.getCustomerId().trim().isEmpty()
                    && SUPPORTED_CURRENCIES.contains(order.getCurrency())
                    && !order.getItems().isEmpty();

            if (!manualValid || !order.isValid()) {
                if (order != null) {
                    order.setStatus(OrderStatus.REJECTED);
                }
                continue;
            }

            // Вложенный цикл O(n^2) для подсчёта всех заказов того же клиента в пакете (DEF-01)
            int sameCustomerOrderCount = 0;
            double customerBatchVolumeRub = 0.0;
            for (Order candidate : orders) {
                if (candidate != null && candidate.isValid()) {
                    if (order.getCustomerId().equals(candidate.getCustomerId())) {
                        sameCustomerOrderCount++;
                        double rate = CURRENCY_RATES.getOrDefault(candidate.getCurrency(), 1.0);
                        customerBatchVolumeRub += candidate.calculateRawTotal() * rate;
                    }
                }
            }

            Order processed = processSingleOrder(order, sameCustomerOrderCount, customerBatchVolumeRub);
            result.add(processed);
        }

        return result;
    }

    public static Order processSingleOrder(Order order, int sameCustomerOrderCount, double customerBatchVolumeRub) {
        Order copy = order.deepCopy();
        double rate = CURRENCY_RATES.getOrDefault(copy.getCurrency(), 1.0);
        double rawInRub = copy.calculateRawTotal() * rate;

        double loyaltyBonus = 0.0;
        if (sameCustomerOrderCount >= 3) {
            loyaltyBonus += 0.02;
        }
        if (customerBatchVolumeRub >= VIP_THRESHOLD) {
            loyaltyBonus += 0.05;
        }

        double totalDiscountRate = Math.min(0.25, copy.getDiscountPercent() + loyaltyBonus);
        double discountedSubtotal = rawInRub * (1.0 - totalDiscountRate);

        double taxSum = 0.0;
        for (Item item : copy.getItems()) {
            double itemRub = item.lineTotal() * rate * (1.0 - totalDiscountRate);
            if ("FOOD".equals(item.getCategory()) || "MEDICAL".equals(item.getCategory()) || "BOOKS".equals(item.getCategory())) {
                taxSum += itemRub * REDUCED_TAX_RATE;
            } else {
                taxSum += itemRub * STANDARD_TAX_RATE;
            }
        }

        double shippingFee = calculateShippingFee(copy.getRegion(), copy.getItems(), discountedSubtotal);
        double finalTotal = Math.round((discountedSubtotal + taxSum + shippingFee) * 100.0) / 100.0;

        copy.setComputedSubtotal(Math.round(discountedSubtotal * 100.0) / 100.0);
        copy.setComputedTax(Math.round(taxSum * 100.0) / 100.0);
        copy.setComputedFinalTotal(finalTotal);
        copy.setStatus(OrderStatus.PROCESSED);
        return copy;
    }

    public static double calculateShippingFee(String region, List<Item> items, double discountedSubtotalRub) {
        if (discountedSubtotalRub >= VIP_THRESHOLD) {
            return 0.0;
        }
        double totalWeight = 0.0;
        for (Item it : items) {
            totalWeight += it.getWeightKg() * it.getQuantity();
        }
        double baseFee;
        switch (region) {
            case "MOSCOW":
            case "SPB":
                baseFee = 350.0;
                break;
            case "EKATERINBURG":
            case "KAZAN":
                baseFee = 550.0;
                break;
            case "NOVOSIBIRSK":
                baseFee = 750.0;
                break;
            default:
                baseFee = 950.0;
                break;
        }
        return baseFee + Math.ceil(totalWeight) * 45.0;
    }

    // =========================================================================
    // Секция 3: ДЕФЕКТ #2 — Диагностика дефектов и Big O из презентации Axenix (Стр. 5)
    // Повторная обработка одинаковых пар в вложенном цикле O(n^2)
    // =========================================================================
    public static List<Item> reconcileInventorySlow(List<Item> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Item> mergedById = new TreeMap<>();
        int pairMatches = 0;

        // Точный паттерн со слайда 5 презентации Axenix:
        // for (Item a : items) { for (Item b : items) { if (a.id().equals(b.id())) { merge(a, b); } } }
        for (Item a : items) {
            int combinedQty = 0;
            double weightedPriceSum = 0.0;
            for (Item b : items) {
                if (a.id().equals(b.id())) {
                    pairMatches++;
                    combinedQty += b.getQuantity();
                    weightedPriceSum += b.getUnitPrice() * b.getQuantity();
                }
            }
            if (!mergedById.containsKey(a.id())) {
                double avgPrice = combinedQty > 0
                        ? Math.round((weightedPriceSum / combinedQty) * 100.0) / 100.0
                        : a.getUnitPrice();
                Item merged = new Item(
                        a.id(),
                        a.getSkuName(),
                        a.getCategory(),
                        combinedQty,
                        avgPrice,
                        a.getWeightKg()
                );
                mergedById.put(a.id(), merged);
            }
        }

        List<Item> output = new ArrayList<>(mergedById.values());
        output.sort(Comparator.comparing(Item::id));
        return output;
    }

    // =========================================================================
    // Секция 4: ДЕФЕКТ #3 — Линейное связывание заказов и профилей клиентов O(n * m)
    // =========================================================================
    public static void enrichOrdersWithCustomers(List<Order> orders, List<CustomerProfile> customers) {
        if (orders == null || customers == null) {
            return;
        }
        for (Order order : orders) {
            if (order == null) {
                continue;
            }
            // Вложенный цикл поиска профиля клиента O(N * M) вместо индексации Map по customerId
            for (CustomerProfile profile : customers) {
                if (profile != null && profile.id().equals(order.getCustomerId())) {
                    order.setAssignedCustomerName(profile.getFullName());
                    if (profile.isActive()) {
                        order.setDiscountPercent(profile.getTier().getDiscountRate());
                    } else {
                        order.setDiscountPercent(0.0);
                    }
                    break;
                }
            }
        }
    }

    // =========================================================================
    // Секция 5: ДЕФЕКТ #4 — Конкатенация строк в цикле и генерация отчёта
    // =========================================================================
    public static String buildAuditDigest(List<Order> processedOrders, List<Item> reconciledItems) {
        String digest = "";
        for (int i = 0; i < processedOrders.size(); i++) {
            Order ord = processedOrders.get(i);
            digest = digest + ord.id() + ":" + ord.getStatus().name() + "="
                    + String.format(Locale.US, "%.2f", ord.getComputedFinalTotal()) + ";";
        }
        digest = digest + "|ITEMS=";
        for (int j = 0; j < reconciledItems.size(); j++) {
            Item it = reconciledItems.get(j);
            digest = digest + it.id() + "x" + it.getQuantity() + "@";
        }
        return digest;
    }

    // =========================================================================
    // Секция 6: Работа с коллекциями java.util (LinkedList, HashSet, TreeSet,
    // Iterator, Collections, Arrays, Comparator, Optional, Stream API)
    // =========================================================================
    public static Map<String, Double> computeCategoryTotals(Collection<Item> items) {
        Map<String, Double> categoryTotals = new TreeMap<>();
        if (items == null) {
            return categoryTotals;
        }
        Iterator<Item> iterator = items.iterator();
        while (iterator.hasNext()) {
            Item item = iterator.next();
            if (item == null || item.getQuantity() <= 0) {
                continue;
            }
            String cat = item.getCategory();
            double current = categoryTotals.getOrDefault(cat, 0.0);
            categoryTotals.put(cat, Math.round((current + item.lineTotal()) * 100.0) / 100.0);
        }
        return categoryTotals;
    }

    public static Set<String> findDistinctActiveRegions(List<Order> orders) {
        Set<String> hashRegions = new HashSet<>();
        for (Order o : orders) {
            if (o != null && o.getStatus() == OrderStatus.PROCESSED) {
                hashRegions.add(o.getRegion());
            }
        }
        TreeSet<String> sortedRegions = new TreeSet<>(hashRegions);
        return sortedRegions;
    }

    public static Optional<Order> findHighestValueOrder(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return Optional.empty();
        }
        return orders.stream()
                .filter(Objects::nonNull)
                .filter(o -> o.getStatus() == OrderStatus.PROCESSED)
                .max(Comparator.comparingDouble(Order::getComputedFinalTotal));
    }

    public static LinkedList<String> buildPriorityDispatchQueue(List<Order> processedOrders) {
        LinkedList<String> queue = new LinkedList<>();
        if (processedOrders == null) {
            return queue;
        }
        List<Order> sorted = new ArrayList<>(processedOrders);
        sorted.sort(
                Comparator.comparingDouble(Order::getComputedFinalTotal).reversed()
                        .thenComparing(Order::id)
        );
        for (Order ord : sorted) {
            String badge = PRIORITY_REGIONS.contains(ord.getRegion()) ? "[PRIORITY]" : "[REGIONAL]";
            queue.addLast(String.format(Locale.US, "%s %s (%s) -> %.2f RUB",
                    badge, ord.id(), ord.getAssignedCustomerName(), ord.getComputedFinalTotal()));
        }
        return queue;
    }

    public static double[] computeStatisticalMoments(List<Order> processedOrders) {
        if (processedOrders == null || processedOrders.isEmpty()) {
            return new double[]{0.0, 0.0, 0.0, 0.0};
        }
        double[] values = new double[processedOrders.size()];
        for (int i = 0; i < processedOrders.size(); i++) {
            values[i] = processedOrders.get(i).getComputedFinalTotal();
        }
        Arrays.sort(values);
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
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

    // =========================================================================
    // Секция 7: Форматирование отчёта (Formatter, String.format, printf, кириллица)
    // =========================================================================
    public static String renderComprehensiveReport(
            ParsedInputBundle bundle,
            List<Order> processedOrders,
            List<Item> reconciledItems
    ) {
        StringBuilder sb = new StringBuilder();
        try (Formatter fmt = new Formatter(sb, Locale.US)) {
            fmt.format("=== ОТЧЁТ СИСТЕМЫ JAVA BLUEPRINTS (%s) ===%n", SYSTEM_VERSION);
            fmt.format("Всего клиентов: %d | Всего входных заказов: %d | Успешно обработано: %d%n",
                    bundle.getCustomers().size(),
                    bundle.getOrders().size(),
                    processedOrders.size());

            double totalGross = 0.0;
            double totalTax = 0.0;
            double totalFinal = 0.0;
            for (Order ord : processedOrders) {
                totalGross += ord.getComputedSubtotal();
                totalTax += ord.getComputedTax();
                totalFinal += ord.getComputedFinalTotal();
            }

            fmt.format("Финансовые итоги (RUB): Нетто=%.2f | НДС=%.2f | Итого к оплате=%.2f%n",
                    totalGross, totalTax, totalFinal);

            double[] stats = computeStatisticalMoments(processedOrders);
            fmt.format("Статистика чека (RUB): Среднее=%.2f | Медиана=%.2f | СтОткл=%.2f | Макс=%.2f%n",
                    stats[0], stats[1], stats[2], stats[3]);

            Set<String> activeRegions = findDistinctActiveRegions(processedOrders);
            fmt.format("Активные регионы (%d): %s%n", activeRegions.size(), String.join(", ", activeRegions));

            fmt.format("--- ДЕТАЛИЗАЦИЯ ЗАКАЗОВ ---%n");
            for (Order ord : processedOrders) {
                fmt.format("  Заказ %s | Клиент: %s (%s) | Статус: %s | Сумма: %.2f RUB%n",
                        ord.id(),
                        ord.getCustomerId(),
                        ord.getAssignedCustomerName(),
                        ord.getStatus().getRussianLabel(),
                        ord.getComputedFinalTotal());
            }

            fmt.format("--- СВЕРКА СКЛАДСКИХ ПОЗИЦИЙ (RECONCILED SKU) ---%n");
            for (Item item : reconciledItems) {
                fmt.format("  SKU %s (%s) | Категория: %s | Кол-во: %d | Средняя цена: %.2f | Сумма: %.2f%n",
                        item.id(),
                        item.getSkuName(),
                        item.getCategory(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.lineTotal());
            }

            Map<String, Double> catTotals = computeCategoryTotals(reconciledItems);
            fmt.format("--- ОБОРОТ ПО КАТЕГОРИЯМ ---%n");
            for (Map.Entry<String, Double> entry : catTotals.entrySet()) {
                fmt.format("  Категория %-12s : %.2f RUB%n", entry.getKey(), entry.getValue());
            }

            Optional<Order> topOrder = findHighestValueOrder(processedOrders);
            if (topOrder.isPresent()) {
                Order top = topOrder.get();
                fmt.format("Крупнейший заказ: %s на сумму %.2f RUB (%s)%n",
                        top.id(), top.getComputedFinalTotal(), top.getAssignedCustomerName());
            }

            String digest = buildAuditDigest(processedOrders, reconciledItems);
            fmt.format("Контрольный дайджест: %s%n", digest);
        }
        return sb.toString();
    }

    // =========================================================================
    // Секция 8: Встроенный эталонный набор данных и точка входа main
    // =========================================================================
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

    public static String executeFullPipeline(String rawDataset) throws OrderProcessingException {
        totalProcessedRuns++;
        ByteArrayInputStream bais = new ByteArrayInputStream(rawDataset.getBytes(StandardCharsets.UTF_8));
        ParsedInputBundle bundle = parseMonolithInput(bais);
        enrichOrdersWithCustomers(bundle.getOrders(), bundle.getCustomers());
        List<Order> processedOrders = processOrders(bundle.getOrders());
        List<Item> reconciledItems = reconcileInventorySlow(bundle.getWarehouseItems());
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
            String report = executeFullPipeline(inputData);
            utf8Out.print(report);
        } catch (OrderProcessingException ex) {
            System.err.printf(Locale.US, "[ОШИБКА %s] %s%n", ex.getErrorCode(), ex.getMessage());
            System.exit(2);
        } catch (Exception ex) {
            System.err.println("Непредвиденная ошибка выполнения: " + ex.getMessage());
            System.exit(1);
        }
    }
}
