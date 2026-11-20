# Vector index benchmark: exact scan vs HNSW (jvector)

Machine: 8 cores, Java 25, Mac OS X aarch64. HNSW settings: degree 16, build beam 100, search beam 100. Latency is per query, after warm-up. Recall@k = share of the exact top k that HNSW also returned.

| Dataset | Vectors | Dim | Queries | Exact p50 | Exact p95 | HNSW p50 | HNSW p95 | Speed-up (p50) | Recall@k | HNSW build |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| real code embeddings (nomic-embed-text:latest) | 3,134 | 768 | 200 | 1.63 ms | 1.68 ms | 0.17 ms | 0.25 ms | 9.6× | 100.0% (k=10) | 0.3 s |
| synthetic clustered | 10,000 | 768 | 200 | 5.24 ms | 5.30 ms | 0.19 ms | 0.25 ms | 26.9× | 100.0% (k=10) | 1.0 s |
| synthetic clustered | 50,000 | 768 | 200 | 26.56 ms | 27.10 ms | 0.35 ms | 0.48 ms | 75.4× | 100.0% (k=10) | 8.0 s |
| synthetic clustered | 100,000 | 768 | 200 | 53.53 ms | 54.69 ms | 0.44 ms | 0.64 ms | 120.9× | 100.0% (k=10) | 19.6 s |
| worst case: random, no clusters | 50,000 | 768 | 200 | 27.35 ms | 27.45 ms | 1.00 ms | 1.17 ms | 27.4× | 9.7% (k=10) | 12.4 s |
