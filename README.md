# SRM Credit Engine

Motor decimal, cadastro, simulação, liquidação individual/em lote, extrato, CSV e edição de pendentes implementados. Backend: 98 testes sem banco e 22 integrações aprovados no `verify` do candidato em 16/09 às 04:37:42 -03:00 (120 no total, relatório conferido). Lote implementado somente no frontend, sem mudanças no backend: 32 testes frontend, TypeScript e build aprovados. Fluxos reais de reconfirmação, recuperação, CSV, edição e lote foram conferidos pelo MCP Playwright. O checklist manual foi concluído; revisão humana final, setup limpo e publicação da entrega continuam pendentes.

## Liquidação em lote

Documentos: [SPEC](SPEC.md), [decisões](DECISIONS.md), [ER](docs/ER.md), [review do Anexo A](REVIEW.md), [uso de IA](AI_USAGE.md), [aferição local](docs/PERFORMANCE.md) e [checklist de entrega](docs/CHECKLIST-ENTREGA.md). Os documentos não substituem revisão e defesa do candidato.

Na lista de pendentes, use **Adicionar ao lote** nos títulos desejados (até 100, entre páginas). Clique em **Revisar lote**, confira as condições e marque **Autorizo** em cada título que deseja liquidar; nenhum é marcado automaticamente. O botão **Liquidar selecionados** envia um título por vez. Os totais selecionados são separados por moeda, somados em centavos inteiros com BigInt; não existe conversão nem precificação no navegador.

Cada envio reaproveita `POST /settlements`, transação individual e chave única. Sucessos anteriores permanecem registrados. Erros de negócio e condições alteradas não param os demais títulos. No item alterado, confira **Anterior / Atual**, marque novamente e clique em **Reconfirmar selecionados**: nova intenção, nova chave. A nova tentativa ainda passa pela conferência do servidor.

Falha técnica interrompe novos envios: o item enviado fica com resultado a confirmar e os seguintes selecionados como não processados. Falha de armazenamento antes do envio não envia pagamento. Use **Consultar ou concluir tentativa** para repetir a mesma chave/JSON. Não há retry automático. Após recuperar, selecione explicitamente os itens restantes. Fechar/recarregar interrompe o lote, mas não prova rollback da requisição em voo.

Limites: cesta, resultados e seleções do lote não são persistidos; após recarga, somente tentativas incertas são recuperadas e as liquidações concluídas aparecem no extrato. Refaça a seleção dos pendentes restantes. Limpar/refazer o lote ou abrir revisão/edição individual descarta a revisão coletiva da tela, sem apagar tentativas locais. Múltiplas abas não compartilham uma fila global; a proteção transacional/idempotente final continua no backend.

Teste real de 16/09 às 04:45 -03:00: três títulos fictícios `AUTO-LOTE-20260916-0444-1/2/3`. Os itens 1 e 3 liquidaram BRL 92,86 cada; item 2 teve face alterada de 100 para 120 após revisão, exigiu reconfirmação de USD 15,48 → 18,57. Perda de resposta após commit, recarga e repetição exata retornaram HTTP 200; consulta encontrou exatamente três liquidações, sem duplicação. Registros mantidos para consulta. Cotação não foi alterada neste teste.

## Editar pendentes

Em **Liquidar um pendente**, clique em **Editar**. A tela consulta o cadastro atual e permite alterar valor, vencimento, tipo e moeda. Cedente e código permanecem fixos. Abrir a edição descarta a simulação e autorização anteriores; depois de salvar, clique em Revisar para obter novas condições. Editar não liquida e USD não exige cotação disponível para salvar.

`PUT /receivables/{id}` recebe os quatro campos obrigatórios, no formato do schema Simulate da OpenAPI. Reutiliza validação do cadastro e bloqueio `FOR UPDATE NOWAIT` da liquidação. Título liquidado ou em processamento retorna 409. Nenhuma alteração de schema foi necessária. Não há versionamento entre duas edições: a última edição aceita prevalece; isso não permite editar um título liquidado. Em resposta incerta, fechar e abrir Editar consulta o estado atual antes de repetir; não há retry automático.

Teste real: `AUTO-CSV-20260915-2245` alterado para face BRL 1250,50, cheque pré-datado, vencimento 20/12/2026, pagamento BRL. Permaneceu PENDING; identidade preservada e nova autorização desmarcada. Tentativa de editar `AUTO-RECONF-20260915-2217` liquidado retornou ALREADY_SETTLED, sem alteração. Nenhuma nova liquidação foi feita neste incremento.

## Importação CSV

Na seção **Importar recebíveis por CSV**, baixe o modelo, escolha o arquivo e clique em **Gerar prévia**. Selecione as linhas válidas e confirme o cadastro. Cada título é cadastrado como PENDING por `POST /receivables`, com revalidação e transação individual; nenhuma liquidação automática. Erros de negócio não impedem os demais; falha técnica interrompe os próximos cadastros e sinaliza resultado incerto. Consulte os pendentes ou reenvie o mesmo arquivo, mantendo os códigos, para verificar duplicidade.

Formato: UTF-8 (BOM opcional), `;`, aspas CSV, decimal com vírgula sem milhar, data `DD/MM/AAAA`. Cabeçalhos obrigatórios: `cedente_codigo;titulo_codigo;tipo;valor_face;vencimento;moeda_pagamento`. Ordem pode variar, sem colunas extras ou repetidas. Tipos: `DUPLICATA_MERCANTIL` e `CHEQUE_PRE_DATADO`; moedas BRL/USD. Modelo em `frontend/public/modelo-recebiveis.csv`; ajuste seus códigos e vencimentos antes do uso.

Limites: 100 títulos por padrão (`CSV_MAX_ROWS`, inteiro positivo), 256 KB por arquivo e 300 KB por requisição multipart. Inválidos contam no limite. Arquivo ilegível, estrutura inválida ou excesso rejeitam a prévia inteira. Duplicados no arquivo invalidam todas as ocorrências; no banco, distinguir duplicado de conflito de dados, sem sobrescrever. Não há histórico nem armazenamento do CSV. Fechar a prévia perde seus erros/resultados; somente cadastros efetivados permanecem.

Endpoint de prévia: `POST /receivables/import-preview`, multipart com campo `file`. Contrato em `/openapi.json`. A prévia não reserva títulos nem garante cadastro posterior.

Teste real em 15/09 às 22:46 -03:00: arquivo com quatro linhas, somente uma válida cadastrada; reenvio detectou duplicado, sem novas liquidações. Registro fictício mantido: `AUTO-CSV-20260915-2245`, PENDING, ID `2353eb67-d472-4366-be1b-8d7f7b80877c`.

## Ambiente

### Frontend — fluxo individual

A pasta `frontend` contém React 19.3, TypeScript 7.0.2, Vite 8.3 e Material UI 9.4, com versões exatas e `package-lock.json`. Node 24.x (verificado com 24.19.0). Compatibilidade conferida no registro npm e nas documentações oficiais de [Vite](https://vite.dev/guide/) e [Material UI](https://mui.com/material-ui/getting-started/installation/).

Disponível: cadastro individual como PENDING, simulação informativa após 400 ms e extrato com filtros, paginação e detalhes. A prévia é invalidada ao editar e respostas antigas são ignoradas. Dinheiro trafega e é formatado como string, sem conversão para Number. O cadastro aceita ponto ou vírgula decimal sem milhar; USD pode ser cadastrado mesmo quando a simulação não encontra cotação. Após sucesso, o formulário fica bloqueado até escolher outro cadastro. Em falha técnica, o aviso informa que o resultado pode ser incerto e orienta manter cedente/código ao repetir. A unicidade do backend impede duplicação dessa identificação.

O cadastro não liquida. Em **Liquidar um pendente**, a lista paginada mostra PENDING. Clique em Revisar para obter as condições do título no servidor; marque a autorização e confirme. Se as condições mudarem, a tela apresenta anterior/atual, desmarca a autorização e exige nova confirmação com outra chave. Após sucesso, atualiza pendentes e extrato. A prévia do cadastro não é reaproveitada como autorização de pagamento.

Antes do envio, a chave e o JSON exato da tentativa são salvos no localStorage. Falha de rede, timeout de 15 segundos, resposta técnica ou operação ocupada preservam a tentativa. **Consultar ou concluir tentativa** repete somente esse pedido/chave e pode efetivar a liquidação ainda não realizada. Não há reenvio automático, inclusive após recarga. Resposta definitiva remove a tentativa; condições alteradas voltam à revisão. Se o armazenamento falhar antes do envio, nada é enviado. Tentativas pendentes bloqueiam novas confirmações nesta tela até serem esclarecidas.

A recuperação vale apenas no mesmo navegador/perfil e origem (use sempre `http://127.0.0.1:5173`; localhost é outra origem). Não limpar dados do navegador enquanto houver tentativas incertas. Múltiplas abas podem iniciar tentativas distintas; a proteção final contra duplicação permanece no backend. Os registros locais usam uma chave por tentativa, sem sobrescrever outras tentativas. A reconfirmação real foi conferida pelo Playwright: cotação 5,50 → 6,00, comparativo anterior/atual, autorização desmarcada e botão bloqueado. Uma resposta foi descartada após o servidor concluir a liquidação; recarga preservou a tentativa e repetição manual retornou HTTP 200 com a mesma chave/JSON e uma única liquidação. Registro fictício `AUTO-RECONF-20260915-2217`; cotação de teste 6,00 permanece no ambiente.

Com o backend iniciado em outro terminal, execute a partir da raiz do repositório:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Abra `http://127.0.0.1:5173`. O Vite é restrito a loopback e encaminha `/api` para `http://127.0.0.1:8080`, sem mudar o CORS do backend. Não há dados financeiros de demonstração embutidos: a tela consulta a API real. Cedentes: até 100 opções nesta primeira tela, com aviso se houver mais; o seed contém dois. Sem backend disponível, a tela apresenta erro.

```powershell
npm.cmd test
npm.cmd run build
```

No PowerShell, use npm.cmd para não depender da política de execução do npm.ps1. No ambiente restrito da IA, o carregador padrão do Vite encontrou `spawn EPERM`. As alternativas suportadas abaixo passaram (32 testes e build), sem alteração de permissões do sistema:

```powershell
npm.cmd test -- --configLoader native --pool threads
npm.cmd run build -- --configLoader native
npm.cmd run dev -- --configLoader native
```

O build gera `frontend/dist`; o proxy descrito acima é de desenvolvimento, não configuração de publicação. Testes de componentes usam respostas simuladas. O bloqueio inicial do navegador foi superado: os cenários reais executados com MCP Playwright estão registrados no AI_USAGE. O checklist manual completo também foi executado pelo candidato; as duas evidências permanecem documentadas separadamente.

### Backend

Java 21, Maven Wrapper 3.9.16 e Spring Boot 4.1.1. PostgreSQL 17-alpine em Docker Compose; Flyway versiona SQL e Hibernate está configurado com `validate`. Dependências seguem o gerenciamento do Spring Boot. Preservada a porta local 5432 e o serviço postgres do projeto existente. Docker precisa estar acessível para executar integração.

## Testes unitários

No Windows, a partir desta pasta:

```powershell
.\mvnw.cmd -B -ntp test
```

No Linux/macOS: `./mvnw -B -ntp test` (pode ser necessário conceder permissão de execução ao arquivo).

O primeiro uso baixa Maven e dependências. Neste ambiente houve falha no download do Wrapper pelo PowerShell; foi usado o ZIP Maven 3.9.16 já disponível, carregado no cache local do Wrapper. A execução via Wrapper com esse cache foi verificada. Essa condição local não foi contornada alterando o script distribuído pelo Maven.

Os testes cobrem os três golden cases, deságio, escala, HALF_EVEN, ordem de conversão, limites de entrada e aniversários de calendário. O teste de contexto genérico do scaffold foi transferido para a fase de integração com PostgreSQL isolado, para que testes de domínio não exijam banco.

## Banco e integração

```powershell
docker compose up -d postgres
.\mvnw.cmd -B -ntp verify
```

`verify` executa `DatabaseIT` e `CreditEngineApplicationIT`, com PostgreSQL descartável via Testcontainers, sem usar os dados do Compose. Cobrem migrations, seed, JPA, cadastro, câmbio, liquidação, reconfirmação, replay, rollback e concorrência. A injeção de falha usa um trigger somente no banco descartável do teste, removido em finally. Falta de Docker causa falha, não um teste ignorado silenciosamente.

Credenciais padrão exclusivas da demonstração local: usuário `credit_engine`, senha `credit_engine`, banco `credit_engine`. Porta vinculada a loopback para uso local. Para mudar senha, configurar `DB_PASSWORD` tanto no Compose quanto na aplicação. Não usar essas credenciais fora do ambiente local.

```powershell
.\mvnw.cmd spring-boot:run
```

Esse comando inicia a API e deve aplicar migrations quando houver banco acessível. Variáveis disponíveis: `DB_URL`, `DB_USER`, `DB_PASSWORD`. Flyway cria dois cedentes fictícios e configuração de taxa base 1%, prazo máximo 120 meses e câmbio válido por 24 horas; nenhuma cotação é criada automaticamente. A API não tem autenticação nesta demonstração: não expor à internet.

## Endpoints deste incremento

- `GET /assignors`: cedentes por código crescente.
- `POST /receivables`: cadastro PENDING; não simula nem liquida, inclusive em USD.
- `GET /receivables`: consulta paginada; filtro opcional `status=PENDING` ou `SETTLED`.
- `GET /receivables/{id}`: consulta individual.
- `PUT /receivables/{id}`: edita valor, tipo, vencimento e moeda de PENDING; preserva identidade.
- `POST /receivables/import-preview`: prévia CSV multipart (`file`), sem persistência.
- `POST /exchange-rates`: inserção de cotação USD/BRL, sem edição ou exclusão.
- `GET /exchange-rates`: histórico paginado por vigência decrescente; par USD/BRL.
- `GET /exchange-rates/{id}`: consulta individual.
- `POST /simulations`: simula sem cadastrar ou liquidar, usando configuração e câmbio do banco.
- `POST /receivables/{id}/simulations`: simula um PENDING cadastrado e retorna título, cálculo e `expectedConditions`.
- `POST /settlements`: liquidação com header UUID `Idempotency-Key` e corpo `{receivableId,expectedConditions}`.
- `GET /settlements/{id}`: consulta snapshot imutável.
- `GET /settlements`: extrato paginado com filtros opcionais combináveis `from`, `to`, `assignorId` e `currency` (BRL/USD).

Listas usam `page=0&size=20`, tamanho máximo 100 e desempate estável. OpenAPI em `src/main/resources/static/openapi.json`, servido em `/openapi.json`. Documento estático versionado, sem dependência adicional de Swagger UI.

Valores financeiros entram e saem como strings JSON, com ponto decimal, sem expoentes. Números JSON não são aceitos nesses campos. Valor de face positivo, até 17 dígitos inteiros e 2 decimais significativos; cotação positiva, até 14 inteiros e 10 decimais. Não há arredondamento silencioso de entradas. Código do título tem até 100 caracteres, não pode ser vazio, remove espaços nas pontas e é sensível a maiúsculas; é único por cedente.

Datas de vencimento são ISO (`AAAA-MM-DD`). Vigência cambial exige offset (`Z` ou `-03:00`), anos 0001–9999 e precisão máxima de microssegundos. Instantes retornam em UTC. A referência do relógio também é truncada a microssegundos para não consultar uma cotação futura por arredondamento do PostgreSQL.

Erros seguem `{code,message,fieldErrors}`: 400 entrada inválida, 404 cadastro inexistente, 409 duplicidade e 422 câmbio ausente/expirado. Falhas técnicas retornam 500 sem SQL/stack trace na resposta. Métodos e tipos de conteúdo inválidos preservam 405/415.

### Exemplo local em PowerShell

Com banco acessível e aplicação iniciada:

```powershell
$api = 'http://localhost:8080'
Invoke-RestMethod "$api/assignors"
$vencimento = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, 'E. South America Standard Time').Date.AddMonths(3).ToString('yyyy-MM-dd')
$simulacao = @{faceValue='100000.00'; type='DUPLICATA_MERCANTIL'; dueDate=$vencimento; paymentCurrency='BRL'}
Invoke-RestMethod "$api/simulations" -Method Post -ContentType 'application/json' -Body ($simulacao | ConvertTo-Json)

$cadastro = @{assignorId='aebc49b1-1c4a-4f86-bf4e-f0224a9aa007'; titleCode='DEMO-001'; faceValue='100000.00'; type='DUPLICATA_MERCANTIL'; dueDate=$vencimento; paymentCurrency='BRL'}
Invoke-RestMethod "$api/receivables" -Method Post -ContentType 'application/json' -Body ($cadastro | ConvertTo-Json)

$cambio = @{pair='USD/BRL'; rate='5.00'; validFrom=[DateTime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ss'Z'")}
Invoke-RestMethod "$api/exchange-rates" -Method Post -ContentType 'application/json' -Body ($cambio | ConvertTo-Json)
$simulacao.paymentCurrency = 'USD'
Invoke-RestMethod "$api/simulations" -Method Post -ContentType 'application/json' -Body ($simulacao | ConvertTo-Json)
```

Repetir `DEMO-001` para o mesmo cedente gera 409, sem sobrescrever. A simulação é informativa; a liquidação recalcula e compara as condições antes de gravar.

### Liquidação e recuperação de tentativa

Use o ID de um recebível PENDING cadastrado. Não altere a chave nem `$pedidoJson` ao repetir uma tentativa de resultado incerto:

```powershell
$recebivelId = '<UUID do recebível cadastrado>'
$previa = Invoke-RestMethod "$api/receivables/$recebivelId/simulations" -Method Post
$chave = [guid]::NewGuid().ToString()
$pedidoJson = @{receivableId=$recebivelId; expectedConditions=$previa.expectedConditions} | ConvertTo-Json -Depth 8
Invoke-RestMethod "$api/settlements" -Method Post -Headers @{'Idempotency-Key'=$chave} -ContentType 'application/json' -Body $pedidoJson
```

Primeira liquidação: 201 e Location; repetição concluída: 200 e mesmo snapshot, mesmo após expiração do câmbio ou alteração da configuração. A chave é retida junto da liquidação, sem TTL. Mesma chave com outro pedido gera `409 IDEMPOTENCY_CONFLICT`. Outro pedido para título SETTLED gera `409 ALREADY_SETTLED`.

`409 CONDITIONS_CHANGED` contém `current.receivable`, `current.simulation` e `current.expectedConditions`. Nenhum efeito é gravado. Mostrar os novos dados ao operador e, somente após sua confirmação, criar outra intenção/chave. Nunca fazer essa confirmação automaticamente. Mudança apenas de ID/vigência de cotação de mesmo valor não exige reconfirmação; o snapshot registra a cotação efetivamente usada.

`409 OPERATION_IN_PROGRESS` informa contenção: repetir manualmente a mesma chave/pedido, sem trocar a intenção. Um timeout ou falha de rede não prova que nada foi gravado; repita o mesmo pedido para consultar ou concluir. Essa repetição pode efetivar uma liquidação ainda não executada. A recuperação persistida já está disponível no painel individual, conforme descrito acima.

Condições comparadas: cedente/código, tipo, vencimento, prazo, moeda, valor de face, taxa efetiva, VP/deságio, cotação e valor final. O fingerprint SHA-256 cobre o DTO validado, com decimais normalizados: espaços/ordem do JSON e zeros decimais equivalentes não mudam a intenção; mudanças nos valores/campos do contrato mudam. Campos desconhecidos não fazem parte do contrato. A comparação não autentica a origem da simulação no navegador; o servidor sempre calcula os valores.

No PostgreSQL, um advisory lock transacional não bloqueante serializa a chave; `FOR UPDATE NOWAIT` protege o título entre chaves diferentes. UNIQUE em recebível e chave permanecem como proteção final. Uma colisão rara no identificador de lock de 64 bits apenas retorna ocupado, sem confundir resultados. Insert de snapshot, chave e atualização SETTLED pertencem à mesma transação. Nenhuma chamada a banco/pagamento externo faz parte deste case.

## Decisões do motor

### Consulta do extrato

O extrato consulta os snapshots gravados, sem recalcular taxas, câmbio ou nomes históricos. Ordenação fixa: `settledAt DESC, id DESC`. Retorna `content`, `page`, `size`, `totalElements` e `totalPages`; cedente inexistente ou página sem resultados retorna 200 com lista vazia. A paginação por offset tem desempate estável, mas não congela a coleção: novas liquidações entre requisições podem deslocar páginas.

`from` inclui o instante inicial; `to` exclui o final. Ambos são opcionais e aceitam ISO 8601 com horário e offset, anos 0001–9999 e precisão máxima de microssegundos. Quando juntos, início deve ser anterior ao fim. Datas sem horário/fuso e parâmetros inválidos retornam 400. Para o dia 14/09 no fuso -03:00, por exemplo:

```text
GET /settlements?from=2026-09-14T00:00:00-03:00&to=2026-09-15T00:00:00-03:00&currency=USD&page=0&size=20
```

Para offsets positivos, codificar `+` como `%2B` na query. Os valores retornados identificam a moeda; não há soma entre BRL e USD. Os filtros usam o instante da liquidação, não o vencimento do título.

### Cálculo

`domain` não depende de Spring, JPA ou banco. `PricingStrategy` fornece o spread por tipo. `PricingEngine` recebe taxa base, limite e cotação já selecionada pela camada de aplicação. Não decide vigência cambial nem confirma liquidações.

Prazo: `TermCalculator` recebe Clock e fuso de negócio, preserva a data de referência e calcula todos os aniversários a partir da data original. O limite configurável nesta versão pode ser reduzido até zero; aumentar além do teto de 120 requer revisar o custo do cálculo exato e a migration.

Valores muito pequenos podem arredondar a zero segundo a regra financeira atual. O motor permite resultado zero; confirmar a admissibilidade comercial antes de habilitar pagamentos reais. Escalas e overflow são verificados antes de persistir. A API preserva decimais sem double.

## Situação da verificação

Resultado vigente: 120 testes de backend aprovados pelo candidato em 16/09 (98 sem banco + 22 integrações), 32 frontend e build aprovados pela IA. As execuções abaixo são históricas, não devem ser somadas entre si. Aferição local BRL com 100 chamadas sequenciais teve p95 6,9136 ms; condições e limitações em docs/PERFORMANCE.md.

Em 15/09/2026 às 00:09:54 -03:00, o candidato executou `verify` com BUILD SUCCESS. A IA conferiu os relatórios locais: 88 testes sem banco e 19 integrações PostgreSQL, total de 107, sem falhas, erros ou ignorados. Isso inclui filtros do extrato, limites temporais, paginação e preservação do histórico. A tentativa anterior da IA havia sido bloqueada pelo Docker; a execução bem-sucedida foi realizada pelo candidato.

Em 14/09/2026 às 17:42:03 -03:00, o usuário executou `.\mvnw.cmd -B -ntp verify` com BUILD SUCCESS. Relatórios Surefire e Failsafe conferidos: 73 testes de domínio, serviço, API e estrutura OpenAPI, mais 16 testes de integração PostgreSQL — 89 no total, sem falhas, erros ou testes ignorados.

Os testes de integração cobrem migrations, seeds, JPA, cadastros, câmbio, snapshots, reconfirmação, replay, rollback após inserção e concorrência por chave/título. A revisão estática independente não identificou defeitos bloqueantes. Essa evidência valida os cenários automatizados implementados; não substitui revisão humana, a validação manual da interface ou a validação de alterações posteriores.

O Docker funciona no terminal do usuário. O ambiente da IA apresentou `AccessDeniedException` ao acessar o pipe; por isso a execução de integração foi realizada pelo usuário. Para repetir a validação ou conferir mudanças novas, executar `verify` em um terminal com acesso ao engine.

## Próximas etapas

Próximo passo: revisão humana final dos documentos, setup limpo, conferência de segredos e preparação de entrega e defesa. Não tratar a implementação funcional como entrega publicada sem conferir esses itens.

REVIEW do Anexo A e ER agora disponíveis. Pendências reais em docs/CHECKLIST-ENTREGA.md: setup limpo, paginação do SPEC, revisão humana/defesa, Git/remoto privado/acesso e horário limite. O escopo simplificado não atende integralmente às exigências de operação pleno/sênior (Compose da aplicação, optimistic locking, CI, observabilidade, C4); ver DECISIONS antes de apresentar o nível atendido.

### Stack e organização

Monólito para manter transação local entre título e snapshot. Java/BigDecimal preserva precisão decimal; domínio independente de Spring facilita aferir o cálculo. PostgreSQL fornece constraints/locks/transações, Flyway versiona o schema. React/TypeScript separa componentes de cadastro/importação/edição e mantém o fluxo de liquidação no painel, sem store global. Material UI reduz trabalho de controles básicos. O servidor determina valores; o navegador somente apresenta e soma centavos já calculados.

Estratégia Git pretendida: branches curtas por incremento, revisão e merge, sem releases paralelas ou Git Flow completo para um case individual. Estado real: feat/project-setup sem commits/remoto em 16/09. Ainda é necessário revisar os arquivos e criar commits honestos, sem simular cronologia retroativa, antes de publicar em repositório privado.

Código integrado no repositório Desafio_tecnico_SRM, preservando a branch feat/project-setup e a configuração existente. Nenhum commit, remoto ou publicação foi criado nesta integração.
