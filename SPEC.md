# SRM Credit Engine — SPEC

## Premissas

O título é em BRL e o pagamento pode ser BRL ou USD. O operador informa vencimento. O prazo é o menor inteiro n >= 0 cujo aniversário `dataReferencia.plusMonths(n)` alcance o vencimento. Todos os aniversários partem da data original, ajustados para o último dia disponível. De 31/01/2027 até 28/02 resulta em um mês; até 30/03, dois. Vence hoje: zero; vencido: rejeitar. A data de referência é a data de negócio da simulação/liquidação em America/Sao_Paulo. Recalcular o prazo na liquidação. Limite inicial configurável: 120 meses.

A taxa base é persistida e configurável, inicialmente 1% a.m. A taxa efetiva é a soma exata da base com o spread da Strategy: duplicata 1,5% a.m., cheque 2,5% a.m. Nesta versão, taxas não negativas e valores de face positivos.

Usar a última cotação USD/BRL cuja vigência começou, expressa em BRL por USD. Sua validade inicial é 24 horas corridas desde a vigência, configurável. Aceitar `vigencia <= instante < vigencia + validade`; cotação futura, inexistente ou expirada não autoriza pagamento USD. BRL dispensa câmbio. Novas cotações são inseridas, com par/vigência únicos.

Na confirmação, recalcular e comparar prazo, taxa efetiva, valor cambial, dados do título e valores monetários com a simulação exibida. Divergências exigem nova confirmação, sem liquidar. Novo ID de cotação de mesmo valor não exige reconfirmação se estiver válido. As condições enviadas pelo frontend são apenas referência; o backend calcula o pagamento.

## Precisão e integridade

Java BigDecimal em toda a cadeia, sem passagem por float/double. Taxas são frações decimais, como `0.01`. Decimais trafegam como strings. Banco: NUMERIC(19,2) para dinheiro, NUMERIC(19,10) para taxas e NUMERIC(24,10) para cotações. Rejeitar excesso significativo de escala e overflow, inclusive no resultado.

Calcular `VP = VF / (1 + base + spread)^n` com soma e potência exatas, sem MathContext finito. Divisão final diretamente com escala 2 e HALF_EVEN. Deságio BRL = VF − VP arredondado. Para USD, dividir o VP BRL já arredondado pela cotação e arredondar novamente com HALF_EVEN, escala 2. Exemplos de empate: 1.005 → 1.00; 1.015 → 1.02.

Liquidação integral, uma por título, com inserção, estado SETTLED e chave de idempotência na mesma transação ACID. Preservar snapshot dos parâmetros, valores, data de referência, cotação e timestamps UTC. Registro liquidado não é editável. Repetição da mesma chave/pedido concluído retorna o resultado existente antes de recálculo. Conflitos não criam outra liquidação.

## Fluxo e escopo

Etapa 1: cadastro e liquidação individuais, simulação automática, reconfirmação, extrato e painel. Etapa 2: CSV UTF-8 brasileiro, até 100 títulos, prévia e confirmação de cadastro somente das linhas válidas. Cedente por código interno; título único por cedente/código. Lote sequencial, transação por item, sucesso parcial. Erros de negócio não param o lote; falhas técnicas interrompem novas tentativas. Condições alteradas são reconfirmadas por seleção explícita.

Pendentes permitem editar valor, vencimento, tipo e moeda, exigindo nova simulação. Tentativas incertas ficam no navegador e podem ser repetidas com mesma chave/payload. Sem histórico de CSV, estorno, pagamento bancário real ou autenticação na demonstração local.

## Critérios de aceite

- C1: VP BRL 92.859,94; C2: 23.337,77; C3: USD 17.094,67 com cotação 5,4321. Testes aferem valor e escala sem arredondar o retorno novamente.
- Testar datas de fim de mês/bissexto, expiração, erros de entrada, rollback e repetição. Integração com PostgreSQL via Testcontainers.
- Formulário simula após 400 ms de pausa, ignora respostas antigas e bloqueia confirmação desatualizada. Erros claros por campo/linha.
- Queries parametrizadas, ordenação por lista permitida e API sem detalhes internos em erros. OpenAPI e extrato por período, cedente e moeda.
- Meta local: p95 de simulação <= 300 ms, com ambiente, carga e amostra documentados. Amostra sequencial BRL aferida em docs/PERFORMANCE.md; não equivale a teste de carga concorrente. Listagens limitadas; testes de componentes e checklist manual ponta a ponta.

## Perguntas ao negócio

O arredondamento de meses para cima e o bloqueio de vencidos são aceitáveis? Quais limites comerciais substituem os do case? A validade de 24 horas atende fins de semana? Deve haver cotação contratada? Quem mantém taxas e cedentes? Como evoluir para estorno, liquidação parcial e transferência real?

Detalhes e justificativas: DECISIONS.md. Este texto é a versão curta de trabalho; conferir paginação de 1–2 páginas no formato final de entrega.
