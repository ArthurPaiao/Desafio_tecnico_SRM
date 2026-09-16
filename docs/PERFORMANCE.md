# Aferição local de simulação

Executada em 16/09/2026 às 04:50:50 -03:00. Endpoint `POST http://127.0.0.1:8080/simulations`, sem cadastro ou liquidação.

Ambiente: Windows NT 10.0.26200, 12 processadores lógicos reportados pelo runtime; aplicação Java 21/Spring Boot 4.1.1 e PostgreSQL 17.11 local via Docker, conforme startup. PowerShell como cliente no mesmo computador; aplicação já aquecida e serviços de desenvolvimento ativos. Não é ambiente dedicado de benchmark.

Carga: 10 aquecimentos descartados + 100 requisições sequenciais, concorrência 1. Payload: duplicata BRL, face 100000.00, vencimento 2026-12-16. Stopwatch inclui chamada HTTP e desserialização no cliente, não mede apenas o motor. Todas as respostas da amostra retornaram valor final; qualquer exceção abortaria a coleta. Percentil pelo método nearest-rank (95ª observação ordenada de 100).

| Medida | Resultado |
|---|---:|
| p50 | 5,1406 ms |
| p95 | 6,9136 ms |
| Máximo | 13,8820 ms |

Meta local do SPEC (p95 <= 300 ms) atendida nesta amostra. Isso não comprova desempenho sob concorrência, carga sustentada, USD, pior prazo, rede remota ou produção.

## Reproduzir no PowerShell

Com Spring e PostgreSQL iniciados, ajuste o vencimento se necessário:

```powershell
$benchmarkBody = '{"faceValue":"100000.00","type":"DUPLICATA_MERCANTIL","dueDate":"2026-12-16","paymentCurrency":"BRL"}'
1..10 | ForEach-Object {
    $null = Invoke-RestMethod 'http://127.0.0.1:8080/simulations' -Method Post -ContentType 'application/json' -Body $benchmarkBody -TimeoutSec 5 -ErrorAction Stop
}
$benchmarkSamples = @(1..100 | ForEach-Object {
    $benchmarkTimer = [Diagnostics.Stopwatch]::StartNew()
    $benchmarkResult = Invoke-RestMethod 'http://127.0.0.1:8080/simulations' -Method Post -ContentType 'application/json' -Body $benchmarkBody -TimeoutSec 5 -ErrorAction Stop
    $benchmarkTimer.Stop()
    if (-not $benchmarkResult.finalAmount) { throw 'Resposta sem valor final' }
    $benchmarkTimer.Elapsed.TotalMilliseconds
})
$benchmarkSorted = @($benchmarkSamples | Sort-Object)
[pscustomobject]@{ samples=$benchmarkSorted.Count; p50ms=$benchmarkSorted[49]; p95ms=$benchmarkSorted[94]; maxms=$benchmarkSorted[99] }
```
