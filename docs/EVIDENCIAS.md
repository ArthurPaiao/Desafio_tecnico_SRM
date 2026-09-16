# Evidências de validação real

Registros de execuções concretas — datas, contagens de teste e títulos fictícios usados nos testes manuais e via MCP Playwright contra a API real. Separado do README para manter ali só o que descreve o sistema; aqui fica o que comprova que ele foi exercitado.

## Backend — execuções de `verify`

| Data | Resultado | Cobertura |
|---|---|---|
| 14/09, 17:42:03 -03:00 | BUILD SUCCESS — 89 testes (73 sem banco + 16 integração) | cadastro, simulação, liquidação, reconfirmação, snapshot, replay, rollback, concorrência |
| 15/09, 00:09:54 -03:00 | BUILD SUCCESS — 107 testes (88 sem banco + 19 integração) | acrescenta extrato: filtros, limites temporais, paginação, preservação de histórico |
| 15/09, 22:50:57 -03:00 | BUILD SUCCESS — 115 testes (95 sem banco + 20 integração) | acrescenta CSV: prévia sem persistência, cadastro, duplicidade |
| 16/09, 04:37:42 -03:00 | BUILD SUCCESS — 120 testes (98 sem banco + 22 integração) | acrescenta edição de pendentes: invalidação de simulação, bloqueio compartilhado com liquidação |

Todas as execuções de integração usam PostgreSQL descartável via Testcontainers. Os totais não são cumulativos entre si — cada linha é uma execução completa da suíte, não um incremento sobre a anterior.

## Frontend — testes e build

32 testes de componente/apresentação (respostas de API simuladas), checagem TypeScript e build aprovados em 16/09, cobrindo cadastro, simulação, liquidação individual, reconfirmação, recuperação local, edição, CSV e lote.

## Cenários reais exercitados (MCP Playwright / manual, contra API e banco reais)

**Cadastro, reconfirmação e recuperação (título `AUTO-RECONF-20260915-2217`, BRL 1000, pagamento USD).** Cotação alterada de 5,50 para 6,00 após a revisão: retornou `CONDITIONS_CHANGED`, comparativo anterior/atual (USD 168,84 → 154,77), autorização desmarcada. Na reconfirmação, a resposta do servidor foi descartada propositalmente após o commit (simulando perda de rede); a tentativa foi preservada no navegador, sobreviveu à recarga, e a repetição manual da mesma chave/JSON retornou HTTP 200 com uma única liquidação (`bd736f62-04e9-43e1-b220-ab64a320a635`).

**Importação CSV.** Arquivo com quatro linhas (uma válida, um cedente desconhecido, duas duplicadas): nenhuma pré-selecionada, inválidas bloqueadas. Confirmação cadastrou somente `AUTO-CSV-20260915-2245` (PENDING, `2353eb67-d472-4366-be1b-8d7f7b80877c`). Reenvio do mesmo arquivo sinalizou duplicado sem gerar novo cadastro ou liquidação.

**Edição de pendentes.** `AUTO-CSV-20260915-2245` editado para face BRL 1250,50, cheque pré-datado, vencimento 20/12/2026, pagamento BRL — permaneceu PENDING, identidade preservada, nova revisão exigida. Tentativa de editar o já liquidado `AUTO-RECONF-20260915-2217` retornou `409 ALREADY_SETTLED` sem alterar nada.

**Liquidação em lote (três títulos fictícios `AUTO-LOTE-20260916-0444-1/2/3`).** Itens 1 e 3 liquidaram BRL 92,86 cada; item 2 teve a face alterada de 100 para 120 entre a revisão e o envio, exigindo reconfirmação de USD 15,48 para 18,57. Resposta descartada após o commit da reconfirmação; recarga preservou a chave, repetição manual retornou HTTP 200. Consulta ao extrato encontrou exatamente três liquidações, sem duplicação.

## Latência

Aferição local (não é teste de carga concorrente): p50 5,1406 ms, p95 6,9136 ms, máximo 13,882 ms em 100 chamadas sequenciais de `POST /simulations` BRL, após 10 aquecimentos, 16/09 às 04:50:50 -03:00. Método completo e limitações em [PERFORMANCE.md](PERFORMANCE.md).

## Checklist manual ponta a ponta

Executado no navegador real em 16/09: cadastro, edição, importação CSV, lote, extrato e recuperação após atualização de página — todos conforme o comportamento especificado no [SPEC.md](../SPEC.md).
