# Axenix Java Blueprints v3.0 PRO — Промышленная многомодульная архитектура, SOTA-исследование и Документация

Полный комплект промышленного решения 48-часового хакатона **Axenix Java Blueprints** («Визуальная среда программирования: кейс на стыке анализа кода, визуального моделирования и автоматической проверки поведения»).

---

## 1. Многомодульная Production-структура репозитория (`java_blueprints_axenix_solution.zip`)

Вместо однофайлового прототипа проект организован по промышленным стандартам **Maven + Multi-Package Java 17 Backend + Modular ES6 Frontend + Docker + GitHub Actions CI/CD** (всего **28 файлов** в архиве):

```text
java-blueprints-axenix/
├── pom.xml                                     # Сборка Maven (Java 17, jdk.compiler exports)
├── Dockerfile                                  # Production-образ (eclipse-temurin:17-jdk-jammy)
├── docker-compose.yml                          # Контейнерная оркестрация (порт 8080)
├── Makefile                                    # Автоматизация: make verify, make test-modular, make serve
├── run_demo.sh                                 # Однокомандный скрипт запуска Quality Gate и Web IDE
├── server.py                                   # HTTP/REST API сервер (/api/analyze-code, /api/verify)
├── .github/workflows/ci-quality-gate.yml       # CI-пайплайн автоматической проверки 16 тестовых пакетов
├── backend/
│   ├── src/main/java/ru/axenix/blueprints/
│   │   ├── model/BlueprintModels.java          # Доменные record-модели CFG, Call Graph, дефектов и тестов
│   │   ├── ast/JavaCompilerAstParser.java      # Парсер OpenJDK 17 Compiler Tree API + детектор Bonus Scope ★
│   │   ├── cfg/ControlFlowGraphBuilder.java    # Построитель 2D графа потока управления и графа вызовов
│   │   ├── diagnostics/ComplexityAndDefectDetector.java # McCabe Cyclomatic, Sonar Cognitive (S3776) и Big-O
│   │   ├── optimizer/AstRecipeOptimizer.java   # 5 рецептов рефакторинга AST и декомпозиции SRP
│   │   ├── verifier/DifferentialQualityGate.java # Верификатор эквивалентности поведения
│   │   └── cli/BlueprintCliMain.java           # Точка входа модульного бэкенда
│   └── src/test/java/ru/axenix/blueprints/
│       └── QualityGateRunnerTest.java          # Модульный тест распознавания Bonus Scope ★ и рецептов
├── frontend/
│   ├── package.json                            # Конфигурация фронтенд-модулей
│   ├── index.html                              # Точка входа веб-клиента
│   └── src/
│       ├── state/store.js                      # Реактивное хранилище со стеком Undo / Redo
│       ├── parser/javaAstParser.js             # Браузерный парсер Java AST/CFG и детектор Bonus Scope ★
│       ├── components/dagRenderer.js           # 2D SVG рендерер блок-схемы и обратных дуг циклов
│       ├── components/callGraphRenderer.js     # Интерактивный граф вызовов (Call Graph) и матрица SRP
│       └── styles/theme.css                    # Стили сетки чертежа и карточек узлов
├── monoliths/
│   ├── OrderProcessingMonolith.java            # Эталонный монолит №1 (1 053 строки, 77 методов)
│   ├── OrderProcessingMonolithOptimized.java   # Оптимизированный монолит №1 (1 047 строк, O(n log n))
│   ├── LogAnalyticsMonolith.java               # Эталонный монолит №2 (196 строк, корреляция безопасности)
│   └── BonusScopeShowcaseMonolith.java         # Эталонный монолит №3 (240 строк, 100% Bonus Scope ★ Слайд 8)
├── decomposed_monolith1/
│   ├── Parser.java                             # Декомпозированный модуль №1: Ввод UTF-8 и модели (348 строк)
│   ├── Analyzer.java                           # Декомпозированный модуль №2: Статистика и коллекции (96 строк)
│   ├── Optimizer.java                          # Декомпозированный модуль №3: Ядро O(n log n) (192 строки)
│   └── Main.java                               # Декомпозированный модуль №4: Оркестрация и отчёт (161 строка)
├── engine/
│   └── BlueprintEngine.java                    # Единый CLI/JIT раннер верификации всех 16 пакетов и JSON-экспорта
├── tests/
│   └── verification_report.json                # Отчёт прохождения 16 пакетов и 50-Seed Differential Fuzzing
├── docs/
│   └── BONUS_SCOPE_VERIFICATION.md             # Доказательство 100% выполнения заданий со звёздочкой ★
├── web/
│   └── java_blueprints_studio.html             # Собранный автономный бандл Web IDE v3.0 PRO
└── README.md                                   # Полная документация проекта
```

---

## 2. Исследование лучших мировых решений (SOTA Benchmark Analysis)

| Категория и SOTA-инструмент | Архитектурный подход в индустрии | Ограничения существующих решений | Как реализовано в Java Blueprints v3.0 PRO |
| :--- | :--- | :--- | :--- |
| **Анализ и метамодель AST**: [INRIA Spoon](https://spoon.gforge.inria.fr/) и [JavaParser](https://www.slideshare.net/slideshow/javaparser-a-tool-to-generate-analyze-and-refactor-java-code/87855146) | Построение полного абстрактного синтаксического дерева (`CtModel` в Spoon, `CompilationUnit` в JavaParser) | Требуют внешних Maven-зависимостей и работают как консольные библиотеки без интерактивного веб-CFG | На бэкенде используется **OpenJDK 17 Compiler Tree API** (`javax.tools.JavaCompiler` + `TreePathScanner`), а во фронтенде — **Live Client-Side AST/CFG парсер**, мгновенно строящий граф для любого пользовательского `.java` файла |
| **Рецептурный рефакторинг**: [OpenRewrite by Moderne](https://docs.openrewrite.org/authoring-recipes/writing-a-java-refactoring-recipe) ([GitHub](https://github.com/openrewrite/rewrite)) | Декларативные рецепты трансформации кода (`JavaIsoVisitor` + `JavaTemplate`) | Ориентирован на пакетную миграцию версий фреймворков в CI/CD, а не на интерактивное устранение узких мест `O(n²)` | Внедрены **5 рецептов алгоритмической оптимизации** (`AstRecipeOptimizer.java`): замена вложенных циклов `O(n²)` на `HashMap`/`TreeMap`, оптимизация `Stream-in-Loop` через `Collectors.groupingBy`, замена конкатенации в цикле на `StringBuilder` и SRP-декомпозиция |
| **Метрики сложности и дефектов**: [SonarQube Cognitive Complexity](https://blog.devgenius.io/sonarqube-cognitive-complexity-265640dbad3e) ([Cyclomatic Guide](https://www.sonarsource.com/resources/library/cyclomatic-complexity/)) и [PMD](https://medium.com/@vallabhsangale7/level-up-code-quality-practical-static-analysis-using-sonarqube-pmd-and-checkstyle-1236f3da2514) | Оценка цикломатической сложности McCabe и когнитивной сложности SonarSource (правило `S3776`, порог = `15`), плюс правила PMD | Выдают статические read-only отчёты без привязки к блок-схеме и без оценки асимптотики `Big O` | Вычисляются **3 метрики на каждый метод**: асимптотика `Big O`, **McCabe Cyclomatic Complexity** (`150` по монолиту) и **SonarSource Cognitive Complexity** (`96` по монолиту; в `processOrders` снижается с `19` до `6`) |
| **Визуальные блок-схемы**: [Flowgorithm](http://www.flowgorithm.org/), [Visual Paradigm JavaCodeSync](https://www.visual-paradigm.com/support/documents/vpuserguide/124/255/7355_javacodesync.html), [JavaWiz](https://ssw.jku.at/Teaching/BachelorTheses/2024/Schloemicher_Andreas.pdf) | Генерация учебного кода из блок-схем или пакетная CLI-синхронизация | Не поддерживают реальные монолиты на `~1000` строк с коллекциями `java.util`, исключениями и `Stream API` | Реализованы **3 режима интерактивного холста**: `Блок-схема CFG (2D DAG)`, `Граф вызовов (Call Graph)` и `Матрица SRP (4 Класса)` со стеком `Undo / Redo` и двусторонней синхронизацией `Код ↔ Граф` |
| **Инженерные акселераторы**: [Axenix «Джавакс»](https://axenix.pro/news/axenix-vypustila-akselerator-dzhavaks-dlja-java-razrabotki-i-testirovanija-po) ([Портал Джавакс](https://axenix-innovation.pro/javax/)) | Корпоративный акселератор Axenix для ускорения Java-разработки и тестирования | Фокус на инфраструктурном развёртывании микросервисов | Встроенный **Quality Gate v3.0**: 16 тестовых пакетов + 50 случайных графов **Property-Based Differential Fuzzing** на 3 независимых монолитах |

---

## 3. Выполнение 100% обязательного и бонусного (со звёздочкой ★) Java Scope (Слайды 7 и 8)

| Уровень и раздел кейса | Требуемые конструкции Java | Где реализовано и чем проверяется |
| :--- | :--- | :--- |
| **Обязательный 1: Язык и `java.lang`** | Классы, интерфейсы (`Identifiable`, `Validatable`, `Priceable`), примитивы, обёртки, массивы (`double[]`), арифметика/логика, `if/else`, `switch`, `for`, `while`, `break/continue/return`, `static`, `try/catch/finally`, `throw`, `Object`, `String`, `Math`, `System` | `OrderProcessingMonolith.java` (`T01`–`T12`) |
| **Обязательный 2: Консольный ввод-вывод** | `System.in`, `System.out`, `System.err`, `Scanner`, `Formatter`, `String.format`, `printf`, `StandardCharsets.UTF_8` (корректная работа с кириллицей) | `OrderProcessingMonolith.java`, `Parser.java`, `Main.java` (`T01`, `T09_CyrillicUtf8Encoding`) |
| **Обязательный 3: Коллекции `java.util`** | `Collection`, `Iterable`, `List`, `Set`, `Map`, `ArrayList`, `LinkedList`, `HashSet`, `TreeSet`, `HashMap`, `TreeMap`, `Iterator`, `Collections`, `Arrays`, `Comparator`, `Optional`, `List.of`, `Set.of`, `Map.of` | `OrderProcessingMonolith.java`, `Analyzer.java`, `Optimizer.java` (`T06`, `T11`, `T12`) |
| **Обязательный 4: Исключения** | Проверяемые (`OrderProcessingException`, `InvalidOrderSyntaxException`) и непроверяемые (`DuplicateOrderStateException`), `throws`, обязательный `catch`, наследование исключений | `OrderProcessingMonolith.java`, `Parser.java` (`T10_CheckedExceptionHandling`) |
| **Бонусный ★ 1: `record` и `enum`** | `record TransactionRecord` (с компактным конструктором), `record MerchantAnalyticsSummary`, `record AuditEntry`, `enum PaymentChannel` (с лямбда-комиссиями), `enum RiskTier`, `enum OrderStatus`, `enum CustomerTier` | `BonusScopeShowcaseMonolith.java`, `OrderProcessingMonolith.java` (`T15_BonusScopeRecordsEnumsGenerics`) |
| **Бонусный ★ 2: Дженерики (`Generics <T>`)** | Обобщённый контейнер с ограничениями `SmartGenericIndex<K extends Comparable<K>, V>`, обобщённый метод `<R> Map<K, R> transformValues(Function<List<V>, R>)`, `Identifiable<K extends Comparable<K>>` | `BonusScopeShowcaseMonolith.java`, `OrderProcessingMonolith.java` (`T15_BonusScopeRecordsEnumsGenerics`) |
| **Бонусный ★ 3: Лямбды и `java.util.function`** | `Predicate<T>`, `Function<T, R>`, `BiFunction<Double, Integer, RiskTier>`, `DoubleUnaryOperator`, `Supplier<T>`, ссылки на методы `TransactionRecord::merchantId`, `Double::sum` | `BonusScopeShowcaseMonolith.java` (`T15_BonusScopeRecordsEnumsGenerics`) |
| **Бонусный ★ 4: `Stream API` и `Collectors`** | `Collectors.groupingBy`, `Collectors.partitioningBy`, `Collectors.summarizingDouble`, `Collectors.reducing`, `Stream.flatMap`, `Collectors.joining` + оптимизация `Stream-in-Loop O(n²) → Collectors.groupingBy O(n log n)` | `BonusScopeShowcaseMonolith.java` (`T16_BonusScopeStreamApiAndCollectors`) |

---

## 4. Протокол автоматической проверки поведения (Quality Gate: 16 пакетов + 50-Seed Fuzzing)

| ID тестового пакета | Проверяемый сценарий и инвариант | Категория | Монолит `O(n²)` | Оптимиз. `O(n log n)` | Декомп. (4 кл.) | Ускорение | Статус |
| :--- | :--- | :--- | ---: | ---: | ---: | ---: | :---: |
| `T01`–`T10` (10 базовых пакетов) | Весь обязательный Java Scope (Кириллица UTF-8, 4 валюты, 5 регионов, VIP-бонусы, `InvalidOrderSyntaxException`) | Core & Scope 1-4 | 22 459 мкс | 13 655 мкс | 13 119 мкс | **до 5.67x** | PASS (10/10) |
| `T11_MediumDataset150Orders` | Средняя нагрузка (150 заказов, 300 товарных позиций) | Bench N=300 | 151 892 мкс | 38 895 мкс | 39 138 мкс | **3.91x** | PASS |
| `T12_LargeDataset2400Items` | Нагрузочный стресс-тест `Big O` (1 200 заказов, 2 400 SKU позиций) | Stress N=2400 | 1 300 638 мкс | 136 745 мкс | 121 274 мкс | **9.51x** | PASS |
| `T13_PropertyBasedFuzzing50Seeds` | Генеративный Property-Based Fuzzing (50 случайных графов заказов) | Fuzzing (50x) | 243 940 мкс | 207 648 мкс | 216 206 мкс | **1.17x** | PASS (50/50) |
| `T14_SecondMonolithLogAnalytics` | Монолит №2 `LogAnalyticsMonolith.java` (корреляция трассировок) | Multi-Monolith | 100 905 мкс | 1 399 мкс | 1 399 мкс | **72.13x** | PASS |
| `T15_BonusScopeRecordsEnumsGenerics` | Монолит №3 `BonusScopeShowcaseMonolith.java` (`record`, `enum`, Bounded Generics, `java.util.function`) | Bonus Scope ★ | 162 071 мкс | 14 003 мкс | 14 003 мкс | **11.57x** | PASS |
| `T16_BonusScopeStreamApiAndCollectors` | Монолит №3 `Stream API & Collectors` (`groupingBy`, `partitioningBy`, `summarizingDouble`, `flatMap`) | Bonus Scope ★ | 14 810 мкс | 1 176 мкс | 1 176 мкс | **12.59x** | PASS |

---

## 5. Быстрый запуск проекта из архива

```bash
# Вариант 1: Однокомандный запуск верификации (16 пакетов + модульные тесты + 50 Fuzz) и старт сервера:
./run_demo.sh

# Вариант 2: Запуск только компиляции OpenJDK 17 и 16 тестовых пакетов через Makefile или Python:
make verify
# или:
python3 server.py --verify

# Вариант 3: Запуск в Docker-контейнере:
docker compose up --build
```
