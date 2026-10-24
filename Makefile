# Axenix Java Blueprints v3.0 — Production Build & Verification Makefile

.PHONY: all verify serve analyze-order analyze-log analyze-bonus test-modular clean

all: verify

verify:
	@echo "=== Running Full OpenJDK 17 Quality Gate (16 Test Suites + 50 Fuzz Seeds) ==="
	python3 server.py --verify

test-modular:
	@echo "=== Testing Modular Backend Packages (ru.axenix.blueprints.*) ==="
	mkdir -p /tmp/axenix_pkg_classes
	java -cp backend/src/main/java:backend/src/test/java backend/src/test/java/ru/axenix/blueprints/QualityGateRunnerTest.java || true

analyze-order:
	java engine/BlueprintEngine.java --analyze-json monoliths/OrderProcessingMonolith.java

analyze-log:
	java engine/BlueprintEngine.java --analyze-json monoliths/LogAnalyticsMonolith.java

analyze-bonus:
	java engine/BlueprintEngine.java --analyze-json monoliths/BonusScopeShowcaseMonolith.java

serve:
	python3 server.py
