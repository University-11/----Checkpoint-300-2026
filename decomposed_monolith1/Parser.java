import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;

/**
 * Модуль ответственности #1 (Декомпозиция монолита — слайд 6 Axenix):
 * Отвечает за модель данных, исключения и потоковый парсинг входных данных (UTF-8 / Кириллица).
 */
public class Parser {

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

    public record AuditEntry(String code, String message, long timestampOrder, int severity) {}

    public static class OrderProcessingException extends Exception {
        private final String errorCode;

        public OrderProcessingException(String errorCode, String message) {
            super(message);
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

    public static class Item implements Identifiable<String>, Comparable<Item> {
        private final String itemId;
        private final String skuName;
        private final String category;
        private int quantity;
        private double unitPrice;
        private double weightKg;

        public Item(String itemId, String skuName, String category, int quantity, double unitPrice, double weightKg) {
            this.itemId = Objects.requireNonNull(itemId);
            this.skuName = Objects.requireNonNull(skuName);
            this.category = category == null ? "GENERAL" : category.toUpperCase(Locale.ROOT);
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.weightKg = weightKg;
        }

        @Override
        public String id() { return itemId; }
        public String getSkuName() { return skuName; }
        public String getCategory() { return category; }
        public int getQuantity() { return quantity; }
        public double getUnitPrice() { return unitPrice; }
        public double getWeightKg() { return weightKg; }
        public double lineTotal() { return quantity * unitPrice; }

        public Item copy() {
            return new Item(itemId, skuName, category, quantity, unitPrice, weightKg);
        }

        @Override
        public int compareTo(Item other) {
            int cmp = this.itemId.compareTo(other.itemId);
            return cmp != 0 ? cmp : Integer.compare(this.quantity, other.quantity);
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
        public String id() { return customerId; }
        public String getFullName() { return fullName; }
        public String getRegion() { return region; }
        public CustomerTier getTier() { return tier; }
        public boolean isActive() { return active; }
    }

    public static class Order implements Identifiable<String>, Validatable, Priceable {
        private final String orderId;
        private final String customerId;
        private final String currency;
        private final String region;
        private final List<Item> items = new ArrayList<>();
        private OrderStatus status = OrderStatus.NEW;
        private double discountPercent = 0.0;
        private double computedSubtotal;
        private double computedTax;
        private double computedFinalTotal;
        private String assignedCustomerName = "UNKNOWN";

        public Order(String orderId, String customerId, String currency, String region) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.currency = currency;
            this.region = region;
        }

        @Override
        public String id() { return orderId; }
        public String getCustomerId() { return customerId; }
        public String getCurrency() { return currency; }
        public String getRegion() { return region; }
        public List<Item> getItems() { return items; }
        public void addItem(Item item) { if (item != null) items.add(item); }
        public OrderStatus getStatus() { return status; }
        public void setStatus(OrderStatus status) { this.status = status; }
        public double getDiscountPercent() { return discountPercent; }
        public void setDiscountPercent(double discountPercent) { this.discountPercent = discountPercent; }
        public double getComputedSubtotal() { return computedSubtotal; }
        public void setComputedSubtotal(double computedSubtotal) { this.computedSubtotal = computedSubtotal; }
        public double getComputedTax() { return computedTax; }
        public void setComputedTax(double computedTax) { this.computedTax = computedTax; }
        public double getComputedFinalTotal() { return computedFinalTotal; }
        public void setComputedFinalTotal(double computedFinalTotal) { this.computedFinalTotal = computedFinalTotal; }
        public String getAssignedCustomerName() { return assignedCustomerName; }
        public void setAssignedCustomerName(String assignedCustomerName) { this.assignedCustomerName = assignedCustomerName; }

        @Override
        public boolean isValid() { return validationErrors().isEmpty(); }

        @Override
        public List<String> validationErrors() {
            List<String> errs = new ArrayList<>();
            if (orderId == null || orderId.trim().isEmpty()) errs.add("Пустой идентификатор заказа");
            if (customerId == null || customerId.trim().isEmpty()) errs.add("Пустой идентификатор клиента");
            if (currency == null || !SUPPORTED_CURRENCIES.contains(currency)) errs.add("Неподдерживаемая валюта: " + currency);
            if (items.isEmpty()) errs.add("Заказ не содержит позиций");
            if (items.size() > MAX_LINE_ITEMS) errs.add("Превышен лимит позиций заказа");
            for (Item item : items) {
                if (item.getQuantity() <= 0) errs.add("Некорректное количество для SKU " + item.id());
                if (item.getUnitPrice() < 0.0 || Double.isNaN(item.getUnitPrice())) errs.add("Некорректная цена для SKU " + item.id());
            }
            return errs;
        }

        @Override
        public double calculateRawTotal() {
            double sum = 0.0;
            for (Item item : items) sum += item.lineTotal();
            return sum;
        }

        @Override
        public double calculateFinalTotal() { return computedFinalTotal; }

        public Order deepCopy() {
            Order copy = new Order(orderId, customerId, currency, region);
            copy.setStatus(status);
            copy.setDiscountPercent(discountPercent);
            copy.setComputedSubtotal(computedSubtotal);
            copy.setComputedTax(computedTax);
            copy.setComputedFinalTotal(computedFinalTotal);
            copy.setAssignedCustomerName(assignedCustomerName);
            for (Item it : items) copy.addItem(it.copy());
            return copy;
        }
    }

    public static class ParsedInputBundle {
        private final List<CustomerProfile> customers = new ArrayList<>();
        private final List<Order> orders = new ArrayList<>();
        private final List<Item> warehouseItems = new ArrayList<>();
        private final List<AuditEntry> auditLog = new ArrayList<>();

        public List<CustomerProfile> getCustomers() { return customers; }
        public List<Order> getOrders() { return orders; }
        public List<Item> getWarehouseItems() { return warehouseItems; }
        public List<AuditEntry> getAuditLog() { return auditLog; }
    }

    public static ParsedInputBundle parseMonolithInput(InputStream inputStream) throws InvalidOrderSyntaxException {
        ParsedInputBundle bundle = new ParsedInputBundle();
        Map<String, Order> orderIndex = new HashMap<>();
        Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8);
        int lineNum = 0;
        long auditCounter = 1L;

        try {
            while (scanner.hasNextLine()) {
                lineNum++;
                String rawLine = scanner.nextLine();
                if (rawLine == null) continue;
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\|");
                String recordType = parts[0].trim().toUpperCase(Locale.ROOT);

                switch (recordType) {
                    case "CUSTOMER": {
                        if (parts.length < 6) throw new InvalidOrderSyntaxException(lineNum, "Ожидалось 6 полей для CUSTOMER");
                        CustomerTier tier;
                        try {
                            tier = CustomerTier.valueOf(parts[4].trim().toUpperCase(Locale.ROOT));
                        } catch (IllegalArgumentException ex) {
                            tier = CustomerTier.STANDARD;
                        }
                        bundle.getCustomers().add(new CustomerProfile(
                                parts[1].trim(),
                                parts[2].trim(),
                                parts[3].trim().toUpperCase(Locale.ROOT),
                                tier,
                                Boolean.parseBoolean(parts[5].trim())
                        ));
                        break;
                    }
                    case "ORDER": {
                        if (parts.length < 5) throw new InvalidOrderSyntaxException(lineNum, "Ожидалось 5 полей для ORDER");
                        Order order = new Order(
                                parts[1].trim(),
                                parts[2].trim(),
                                parts[3].trim().toUpperCase(Locale.ROOT),
                                parts[4].trim().toUpperCase(Locale.ROOT)
                        );
                        orderIndex.put(order.id(), order);
                        bundle.getOrders().add(order);
                        break;
                    }
                    case "ITEM": {
                        if (parts.length < 8) throw new InvalidOrderSyntaxException(lineNum, "Ожидалось 8 полей для ITEM");
                        int qty;
                        double price;
                        double weight;
                        try {
                            qty = Integer.parseInt(parts[5].trim());
                            price = Double.parseDouble(parts[6].trim());
                            weight = Double.parseDouble(parts[7].trim());
                        } catch (NumberFormatException ex) {
                            throw new InvalidOrderSyntaxException(lineNum, "Ошибка формата числа");
                        }
                        Item item = new Item(parts[2].trim(), parts[3].trim(), parts[4].trim(), qty, price, weight);
                        Order targetOrder = orderIndex.get(parts[1].trim());
                        if (targetOrder != null) targetOrder.addItem(item);
                        bundle.getWarehouseItems().add(item.copy());
                        break;
                    }
                    default:
                        bundle.getAuditLog().add(new AuditEntry("UNKNOWN_REC", recordType, auditCounter++, 2));
                        break;
                }
            }
        } finally {
            bundle.getAuditLog().add(new AuditEntry("PARSE_DONE", "Разобрано строк: " + lineNum, auditCounter, 1));
        }
        return bundle;
    }
}
