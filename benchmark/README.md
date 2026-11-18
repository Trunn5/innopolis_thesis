# Бенчмарк моделей конкурентности

Один и тот же запрос (CPU-цикл + вызов внешнего сервиса) прогоняется под разными моделями конкурентности; сравниваются пропускная способность и задержки (p50, p99).

## Архитектура

- `Workload` — запрос: CPU-цикл и заглушка внешнего сервиса (фиксированная задержка, семафор ограничивает число одновременных вызовов).
- `ConcurrencyModel` — модель: запускает `workers` циклов запросов до дедлайна и возвращает задержки.
- `ExecutorModel` — блокирующий цикл на Java-executor: `platform` (фиксированный пул потоков) и `virtual` (виртуальный поток на воркера).
- `Main` — разбор аргументов, запуск модели, вывод результата.

Следующие модели (Scala Future, ZIO, Kotlin coroutines) реализуют тот же интерфейс.

## Запуск

Нужен JDK 21.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # если java 21 не в PATH
./gradlew run --args="--model platform --workers 64 --pool-size 8"
./gradlew run --args="--model virtual --workers 64"
```

Параметры: `--model`, `--workers`, `--pool-size`, `--duration-sec`, `--cpu-iterations`, `--delay-ms`, `--external-limit`.
