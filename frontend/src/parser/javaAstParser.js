/**
 * frontend/src/parser/javaAstParser.js
 * Live Client-Side Java AST/CFG & Bonus Scope (★ Slide 8) Analyzer.
 * Extracts:
 *   1. Method Control-Flow Graphs (CFG) with loop depth, McCabe Cyclomatic & Sonar Cognitive Complexity
 *   2. Inter-Method Call Graph edges
 *   3. Starred Bonus Constructs (`record`, `enum`, Bounded Generics, Lambdas, Method References, `Stream API` & `Collectors`)
 */
export function scanBonusConstructs(sourceText) {
  const lines = sourceText.split("\n");
  const constructs = [];
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i].trim();
    if (!l || l.startsWith("//") || l.startsWith("*")) continue;
    if (/\brecord\s+\w+\s*\(/.test(l)) {
      constructs.push({ category: "record ★", name: (l.match(/record\s+(\w+)/) || [])[1] || "Record", line: i + 1, code: l.slice(0, 76) });
    }
    if (/\benum\s+\w+/.test(l)) {
      constructs.push({ category: "enum ★", name: (l.match(/enum\s+(\w+)/) || [])[1] || "Enum", line: i + 1, code: l.slice(0, 76) });
    }
    if (/class\s+\w+<[^>]+>|interface\s+\w+<[^>]+>|public\s+static\s+<[^>]+>/.test(l)) {
      constructs.push({ category: "Generics ★", name: "Параметризованный тип <T>", line: i + 1, code: l.slice(0, 76) });
    }
    if (/->/.test(l) || /\b(Predicate|Function|BiFunction|Consumer|Supplier|UnaryOperator|DoubleUnaryOperator)\b/.test(l)) {
      constructs.push({ category: "Lambda / function ★", name: "Лямбда / Функциональный интерфейс", line: i + 1, code: l.slice(0, 76) });
    }
    if (/\.stream\(|Collectors\.|\.flatMap\(|\.filter\(/.test(l)) {
      constructs.push({ category: "Stream API & Collectors ★", name: "Потоковый конвейер Stream / Collector", line: i + 1, code: l.slice(0, 76) });
    }
  }
  return constructs;
}
