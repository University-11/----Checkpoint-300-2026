import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Модуль ответственности #3 (Декомпозиция монолита — слайд 6 Axenix):
 * Оптимизированная бизнес-логика расчёта заказов O(n) и сверки склада O(n log n).
 */
public class Optimizer {

    public static void enrichOrdersWithCustomers(List<Parser.Order> orders, List<Parser.CustomerProfile> customers) {
        if (orders == null || customers == null) return;
        Map<String, Parser.CustomerProfile> customerMap = new HashMap<>(customers.size() * 2);
        for (Parser.CustomerProfile profile : customers) {
            if (profile != null) customerMap.putIfAbsent(profile.id(), profile);
        }
        for (Parser.Order order : orders) {
            if (order == null) continue;
            Parser.CustomerProfile profile = customerMap.get(order.getCustomerId());
            if (profile != null) {
                order.setAssignedCustomerName(profile.getFullName());
                order.setDiscountPercent(profile.isActive() ? profile.getTier().getDiscountRate() : 0.0);
            }
        }
    }

    public static List<Parser.Order> processOrders(List<Parser.Order> orders) {
        List<Parser.Order> result = new ArrayList<>();
        if (orders == null || orders.isEmpty()) return result;

        Map<String, Integer> countByCustomer = new HashMap<>();
        Map<String, Double> volumeByCustomer = new HashMap<>();
        for (Parser.Order candidate : orders) {
            if (candidate != null && candidate.isValid()) {
                String cid = candidate.getCustomerId();
                countByCustomer.put(cid, countByCustomer.getOrDefault(cid, 0) + 1);
                double rate = Parser.CURRENCY_RATES.getOrDefault(candidate.getCurrency(), 1.0);
                volumeByCustomer.put(cid, volumeByCustomer.getOrDefault(cid, 0.0) + candidate.calculateRawTotal() * rate);
            }
        }

        for (Parser.Order order : orders) {
            if (order == null || !order.isValid()) {
                if (order != null) order.setStatus(Parser.OrderStatus.REJECTED);
                continue;
            }
            int sameCustomerOrderCount = countByCustomer.getOrDefault(order.getCustomerId(), 0);
            double customerBatchVolumeRub = volumeByCustomer.getOrDefault(order.getCustomerId(), 0.0);
            result.add(processSingleOrder(order, sameCustomerOrderCount, customerBatchVolumeRub));
        }
        return result;
    }

    public static Parser.Order processSingleOrder(Parser.Order order, int sameCustomerOrderCount, double customerBatchVolumeRub) {
        Parser.Order copy = order.deepCopy();
        double rate = Parser.CURRENCY_RATES.getOrDefault(copy.getCurrency(), 1.0);
        double rawInRub = copy.calculateRawTotal() * rate;

        double loyaltyBonus = 0.0;
        if (sameCustomerOrderCount >= 3) loyaltyBonus += 0.02;
        if (customerBatchVolumeRub >= Parser.VIP_THRESHOLD) loyaltyBonus += 0.05;

        double totalDiscountRate = Math.min(0.25, copy.getDiscountPercent() + loyaltyBonus);
        double discountedSubtotal = rawInRub * (1.0 - totalDiscountRate);

        double taxSum = 0.0;
        for (Parser.Item item : copy.getItems()) {
            double itemRub = item.lineTotal() * rate * (1.0 - totalDiscountRate);
            if ("FOOD".equals(item.getCategory()) || "MEDICAL".equals(item.getCategory()) || "BOOKS".equals(item.getCategory())) {
                taxSum += itemRub * Parser.REDUCED_TAX_RATE;
            } else {
                taxSum += itemRub * Parser.STANDARD_TAX_RATE;
            }
        }

        double shippingFee = calculateShippingFee(copy.getRegion(), copy.getItems(), discountedSubtotal);
        double finalTotal = Math.round((discountedSubtotal + taxSum + shippingFee) * 100.0) / 100.0;

        copy.setComputedSubtotal(Math.round(discountedSubtotal * 100.0) / 100.0);
        copy.setComputedTax(Math.round(taxSum * 100.0) / 100.0);
        copy.setComputedFinalTotal(finalTotal);
        copy.setStatus(Parser.OrderStatus.PROCESSED);
        return copy;
    }

    public static double calculateShippingFee(String region, List<Parser.Item> items, double discountedSubtotalRub) {
        if (discountedSubtotalRub >= Parser.VIP_THRESHOLD) return 0.0;
        double totalWeight = 0.0;
        for (Parser.Item it : items) totalWeight += it.getWeightKg() * it.getQuantity();
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

    public static List<Parser.Item> reconcileInventoryFast(List<Parser.Item> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        Map<String, Integer> qtyById = new HashMap<>();
        Map<String, Double> weightedSumById = new HashMap<>();
        Map<String, Parser.Item> firstSeenById = new TreeMap<>();

        for (Parser.Item item : items) {
            String id = item.id();
            qtyById.put(id, qtyById.getOrDefault(id, 0) + item.getQuantity());
            weightedSumById.put(id, weightedSumById.getOrDefault(id, 0.0) + item.getUnitPrice() * item.getQuantity());
            firstSeenById.putIfAbsent(id, item);
        }

        List<Parser.Item> output = new ArrayList<>(firstSeenById.size());
        for (Map.Entry<String, Parser.Item> entry : firstSeenById.entrySet()) {
            String id = entry.getKey();
            Parser.Item proto = entry.getValue();
            int combinedQty = qtyById.getOrDefault(id, 0);
            double weightedPriceSum = weightedSumById.getOrDefault(id, 0.0);
            double avgPrice = combinedQty > 0
                    ? Math.round((weightedPriceSum / combinedQty) * 100.0) / 100.0
                    : proto.getUnitPrice();
            output.add(new Parser.Item(id, proto.getSkuName(), proto.getCategory(), combinedQty, avgPrice, proto.getWeightKg()));
        }
        return output;
    }

    public static String buildAuditDigest(List<Parser.Order> processedOrders, List<Parser.Item> reconciledItems) {
        StringBuilder sb = new StringBuilder(processedOrders.size() * 32 + reconciledItems.size() * 16 + 16);
        for (Parser.Order ord : processedOrders) {
            sb.append(ord.id())
              .append(':')
              .append(ord.getStatus().name())
              .append('=')
              .append(String.format(Locale.US, "%.2f", ord.getComputedFinalTotal()))
              .append(';');
        }
        sb.append("|ITEMS=");
        for (Parser.Item it : reconciledItems) {
            sb.append(it.id()).append('x').append(it.getQuantity()).append('@');
        }
        return sb.toString();
    }
}
