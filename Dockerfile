FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends python3 && rm -rf /var/lib/apt/lists/*

COPY . /app

# Run compile-time Quality Gate (16 test suites + 50-seed Differential Fuzzing)
RUN python3 server.py --verify

EXPOSE 8080
CMD ["python3", "server.py"]
