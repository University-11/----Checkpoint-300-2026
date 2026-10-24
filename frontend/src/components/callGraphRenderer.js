/**
 * frontend/src/components/callGraphRenderer.js
 * Renders the Inter-Method Call Graph and 4-Module Single Responsibility Principle (SRP)
 * Decomposition Matrix (Parser.java -> Analyzer.java -> Optimizer.java -> Main.java).
 */
export const SRP_ARCHITECTURE_MATRIX = [
  { module: "Parser.java", role: "Потоковый ввод UTF-8, Валидация и Доменные модели", methods: ["parseMonolithInput", "parseTierSafe", "parseIntChecked", "parseDoubleChecked", "Order.validationErrors"], lines: 348, color: "sky" },
  { module: "Analyzer.java", role: "Статистические моменты, Регионы и Категории", methods: ["computeCategoryTotals", "findDistinctActiveRegions", "findHighestValueOrder", "computeStatisticalMoments"], lines: 96, color: "purple" },
  { module: "Optimizer.java", role: "Алгоритмическое ядро O(n) / O(n log n) с HashMap/TreeMap", methods: ["enrichOrdersWithCustomers", "processOrders", "processSingleOrder", "reconcileInventoryFast", "buildAuditDigest"], lines: 192, color: "indigo" },
  { module: "Main.java", role: "Оркестрация конвейера и генерация отчёта Formatter", methods: ["executeDecomposedPipeline", "renderComprehensiveReport", "getDefaultDataset", "main"], lines: 161, color: "orange" }
];
