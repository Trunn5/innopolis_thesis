# Research Proposal

**Working title:** Comparative Analysis of Concurrency Models for JVM-based Backend Services

---

## 1. Introduction and Research Problem

JVM backend services handle many requests at once. The **concurrency model** (how parallel work is organized) affects **which resource runs out first**—OS threads, slots on an external API, memory—and **where in the architecture** explicit bounds on parallel work must be set.

All experiments use **one and the same request logic**: CPU work; waiting on a **stub external service** (controlled delay and concurrency cap); a request with **parallel outbound calls** to that service; **cancellation** of the request on timeout. The study compares **models**, not different business algorithms.

**Broad topic:** comparative analysis of JVM concurrency models for backend services. **Concrete focus:** a **property list** used for comparison and **benchmark load scenarios**. The outcome is a **summary table and architecture recommendations**, not a ranking of the “fastest library.”

**Five models** on the shared workflow:

1. Bounded **platform thread pool** and **blocking** code (classic Java service).
2. **Virtual threads** (JDK 21+) and blocking code.
3. Scala **`Future`** / **`ExecutionContext`** over a thread pool.
4. **ZIO** fibers on a small worker pool.
5. **Kotlin coroutines** with a dispatcher over threads.

Public work often reports throughput for one scenario. Teams also need **limits**, **overload behavior**, and **cancellation** (whether occupied dependency slots are released). Many benchmarks **confound** scheduling substrate (platform vs. virtual threads) and abstraction (blocking vs. fiber/coroutine runtime).

**Research problem.** For the fixed workflow above, it is not yet systematically described how the five models differ on a **property list** that matters for backend design—not only speed and resource use, but first bottleneck, overload, and cancellation.

**Property list for comparison:**

- **Resources and limits:** first limiting resource (thread pool vs. external cap vs. runtime workers); OS thread count; memory/CPU overhead at equal load; sustainable in-flight request count before quality drops.
- **Behavior under load:** throughput; typical and tail latency (p95/p99); overload when offered concurrency exceeds a limit (queues, where acceptance slows).
- **Cancellation and request lifetime:** whether cancel reaches parallel outbound calls; whether occupied external slots are released; structured vs. manual fan-out scope.
- **Waiting on external services:** blocking wait vs. explicit non-blocking wait (e.g. ZIO); risk of blocking an async runtime’s worker pool.
- **Errors and engineering:** behavior when one of several parallel calls fails; where parallelism limits are configured; effort to implement the same logic on all five models.

**Measured vs. discussed.** Experiments primarily cover first exhausted resource, throughput, tail latency, overload, cancellation and slot occupancy, and OS thread count. Other property rows are covered in Chapters 1–2 and in the final table with labels *measured / literature / not in scope*.

---

## 2. Research Questions

**Primary research question (RQ0):**

> How does the choice of concurrency model on the JVM affect backend service limits and the architectural placement of parallelism bounds?

**Supporting questions:**

- **RQ1:** When the request workflow is I/O-bound, how do platform thread pools and virtual threads differ in which resource saturates first (thread pool vs. external dependency concurrency limit) for varying ratios of pool size to dependency limit?
- **RQ2:** For CPU-only workflows, do the models converge in throughput, and what residual overhead (CPU, memory, thread count) remains?
- **RQ3:** When in-flight requests exceed the dependency limit, how do models differ in tail latency (p95/p99) at comparable throughput?
- **RQ4:** For a composite request with parallel outbound calls, do models differ in whether cancelled requests release occupied dependency slots and stop sibling calls?
- **RQ5:** Can a simple queuing-theory bound predict which resource saturates first, and how well do measurements match that prediction across models?

**Hypotheses (to be tested, not assumed):**

- H1: With a small platform thread pool and a large dependency limit, the thread pool limits throughput before the dependency does; virtual threads shift the bottleneck to the dependency.
- H2: With CPU-only work, throughput converges across models; differences appear mainly in overhead metrics.
- H3: At saturated dependencies, throughput is similar across models that can reach the limit; tail latency and slot occupancy differ.
- H4: Cancellation semantics differ across models; some retain occupied slots after the client-facing request completes.

---

## 3. Novelty and Contributions

This work does **not** aim to discover a new concurrency primitive. Its contributions are **methodological and empirical**, oriented toward service design:

1. **Factor-separated comparison.** A 2×2-style design separates scheduling substrate (platform vs. virtual threads) from abstraction (blocking vs. ZIO fibers), plus Scala `Future`/`ExecutionContext` and Kotlin coroutines as reference implementations, on **one equivalent request workflow**.

2. **Bottleneck-shift scenarios.** A parameter matrix (thread pool size × dependency concurrency limit × in-flight load) targets the transition from thread-limited to dependency-limited regimes—beyond single-point “who is faster” benchmarks.

3. **Shared benchmark harness.** A reusable load generator with fixed in-flight concurrency, controlled stub dependencies (latency, concurrency cap), and unified metrics (throughput, latency percentiles, JVM thread count, occupied slots) supports fair comparison; initial prototypes validate feasibility.

4. **Cancellation and resource retention.** Measurement of slot occupancy after cancel/timeout on fan-out requests addresses a gap in throughput-only comparisons.

5. **Design-oriented outcomes.** A **models × properties** table, recommendations on **where to bound parallelism**, and explicit scope (stub dependency, single JVM, fixed request logic)—not a “fastest framework” ranking.

---

## 4. State of the Art (Early Literature Review)

**JVM concurrency foundations.** Classic treatment of threads, pools, and safety appears in Goetz et al. (*Java Concurrency in Practice*). The Java Memory Model and happens-before rules underpin all models compared here.

**Virtual threads and structured concurrency.** JEP 444 introduced virtual threads as lightweight JVM threads that unmount from carrier threads during blocking I/O. JEP 525/533 develop structured concurrency via `StructuredTaskScope`, emphasizing scoped lifetimes and cancellation—conceptually related to fan-out in backend requests. Official JDK documentation and OpenJDK materials describe intended use with blocking JDBC and HTTP clients.

**Scala async models.** Scala’s `Future` and `ExecutionContext` decouple task submission from a thread pool; performance depends strongly on pool choice and blocking discipline. ZIO provides a fiber-based runtime with explicit blocking and interruption; documentation and community benchmarks compare ZIO to other effect systems but rarely isolate substrate vs. abstraction under dependency limits.

**Kotlin coroutines.** Kotlin’s suspend functions and dispatchers map coroutines onto thread pools; integration with Loom/virtual threads is documented for recent Kotlin/JDK combinations. Structured concurrency (`coroutineScope`, cancellation) is the idiomatic answer to fan-out lifecycle.

**Backend performance and saturation.** Kleppmann (*Designing Data-Intensive Applications*) and SRE literature emphasize bottlenecks, backpressure, and tail latency. Dean and Barroso (“The Tail at Scale”) explain why saturated systems degrade at high percentiles. Little’s Law relates concurrency, service time, and queueing—usable as a sanity check for stub-limited throughput.

**Existing benchmarks and gaps.** Blog posts and JEP-related benchmarks often compare platform vs. virtual threads on micro-benchmarks or HTTP servers without varying dependency limits or reporting post-saturation tail latency. Effect-system comparisons (ZIO vs. cats-effect vs. futures) focus on runtime overhead, not **architectural limit placement**. **Gap addressed by this thesis:** controlled separation of pool vs. dependency limits, unified workflow across five models, and cancellation/slot-release measurements.

---

## 5. Goal of the Thesis

**Goal:** To compare the five concurrency models **against the property list** and to show **where to bound parallelism** and how **cancellation** behaves.

**Tasks:**

1. Document the property list and the five models.
2. Build a benchmark with a **stub external service** (response delay, concurrency limit, occupied-slot accounting).
3. Run scenarios: CPU-only; I/O **below** and **above** the external limit; composite (parallel outbound) request; cancellation.
4. Produce a summary table and architecture recommendations.

**Object of study:** JVM backend services with parallel request processing.

**Subject of study:** Differences among concurrency models **on the property list**, for **one fixed request structure**.

**Research question (aligned with the thesis text):** How does the choice of concurrency model affect service limits and **where in the architecture** to set bounds on parallel processing?

**Expected result:** The property list; key scenario results for all five models; a comparative table; recommendations; explicit applicability boundaries.

**Out of scope:** Distributed actor systems; comparison of reactive streams (Project Reactor); dedicated study of retry policies; low-level CPU cache effects; carrier pinning in `synchronized`/native code (not a main conclusion); multi-node deployment; production traffic traces; exhaustive comparison of all HTTP/JDBC clients.

---

## 6. Detailed Work Plan and Schedule

| Month | Period (example) | Activities | Deliverables |
|-------|------------------|------------|--------------|
| **1** | Oct 2026 | Draft RQs and hypotheses; literature review; fix shared request workflow and property list | Proposal approved; literature matrix; workflow diagram |
| **2** | Nov 2026 | Design benchmark architecture (shared harness + pluggable backends); equivalence criteria; stub dependency spec | Architecture doc; harness MVP (Java platform + virtual) |
| **3** | Dec 2026 | Implement Java backends; scenarios: CPU-only, I/O below/above dependency limit; metrics pipeline (CSV) | Runnable Java experiments; draft Chapter 3 (methods) |
| **4** | Jan 2027 | Scala: blocking on pool, `Future`, ZIO fibers; align with equivalence criteria | Three Scala backends integrated |
| **5** | Feb 2027 | Kotlin coroutines backend; matrix experiments (pool size × dependency limit × in-flight) | Full five-model harness; raw result datasets |
| **6** | Mar 2027 | Composite request + cancellation/slot-release experiments; optional analytical model (Little’s Law) vs. data | Cancellation study; draft results chapter |
| **7** | Apr 2027 | Statistical aggregation, figures, comparative analysis; write Chapters 1–2 and 4 | Complete draft thesis text |
| **8** | May 2027 | Revision, supervisor feedback, reproducibility package (README, scripts), defense preparation | Final thesis + appendix (code, configs) |

**Milestones**

- End of Month 2: harness demonstrates bottleneck shift for two Java models.
- End of Month 5: all five models run the same scenario suite.
- End of Month 6: cancellation experiment complete or hypothesis explicitly rejected with evidence.
- End of Month 8: submission.

**Risks and mitigation**

| Risk | Mitigation |
|------|------------|
| Logic drift between languages | Single written equivalence checklist; shared stub; code review per backend |
| Scala/Kotlin toolchain friction | Start Java-only matrix in Month 3; parallel toolchain setup in Month 4 |
| Obvious results only at saturated stub | Prioritize matrix cells where pool size is below vs. above the external limit |
| Cancel behavior dominated by HTTP client | Use one client per model or in-process stub with explicit slot accounting |

---

## 7. References

1. Goetz, B., Peierls, T., Bloch, J., Bowbeer, J., Holmes, D., & Lea, D. (2006). *Java Concurrency in Practice*. Addison-Wesley. (Standard reference on JVM threads, pools, and safety.)

2. Kleppmann, M. (2017). *Designing Data-Intensive Applications*. O’Reilly Media. https://www.oreilly.com/library/view/designing-data-intensive-applications/9781491903063/

3. OpenJDK. (2023). JEP 444: Virtual Threads. *Status: Final.* https://openjdk.org/jeps/444

4. OpenJDK. (2025). JEP 525: Structured Concurrency (Preview). https://openjdk.org/jeps/525 *(Preview API; cite current JEP number for the JDK version you use in experiments.)*

5. Oracle Corporation. *Virtual Threads* (Java SE 21). https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html

6. Odersky, M., Spoon, L., Venners, B., & Sommers, F. (2021). *Programming in Scala* (5th ed.). Artima Press. https://www.artima.com/shop/programming_in_scala_5ed

7. ZIO Contributors. *ZIO Documentation* (Runtime, fibers, concurrency). https://zio.dev and https://zio.dev/reference/core/runtime/

8. JetBrains. *Coroutines guide* (Kotlin). https://kotlinlang.org/docs/coroutines-guide.html

9. Dean, J., & Barroso, L. A. (2013). The Tail at Scale. *Communications of the ACM*, 56(2), 74–80. https://doi.org/10.1145/2408776.2408794

10. Beyer, B., Jones, C., Petoff, J., & Murphy, N. R. (Eds.). (2016). *Site Reliability Engineering*. O’Reilly Media. https://sre.google/sre-book/table-of-contents/

11. Little, J. D. C. (1961). A Proof for the Queuing Formula: L = λW. *Operations Research*, 9(3), 383–387. https://doi.org/10.1287/opre.9.3.383

12. Oracle Corporation. *StructuredTaskScope* (Java SE 21 API). https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/StructuredTaskScope.html

13. Bonér, J., et al. (2014). *The Reactive Manifesto*. https://www.reactivemanifesto.org *(Background on backpressure and responsiveness; optional context, not a primary technical source for JVM models.)*

14. OpenJDK. *JMH: Java Microbenchmark Harness*. https://github.com/openjdk/jmh and https://openjdk.org/projects/code-tools/jmh

15. HikariCP. *About Pool Sizing* (Wiki). https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
