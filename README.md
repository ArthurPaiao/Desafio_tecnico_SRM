# SRM Credit Engine

Motor de precificação (Strategy), cadastro, simulação, liquidação individual/em lote, extrato, importação CSV e edição de pendentes para uma FIDC operar recebíveis em BRL/USD com precisão decimal e liquidação idempotente. Backend: 120 testes (98 sem banco + 22 integração PostgreSQL). Frontend: 32 testes de componente, TypeScript e build. Evidências de execução e cenários reais (Playwright/manual) em [docs/EVIDENCIAS.md](docs/EVIDENCIAS.md).

## Documentos

[SPEC](SPEC.md) · [decisões](DECISIONS.md) · [ER](docs/ER.md) · [review do Anexo A](REVIEW.md) · [uso de IA](AI_USAGE.md) · [evidências de validação](docs/EVIDENCIAS.md) · [aferição de latência](docs/PERFORMANCE.md) · [checklist de entrega](docs/CHECKLIST-ENTREGA.md).

## Liquidação em lote

Na lista de pendentes, use **Adicionar ao lote** nos títulos desejados (até 100, entre páginas). Clique em **Revisar lote**, confira as condições e marque **Autorizo** em cada título que deseja liquidar; nenhum é marcado automaticamente. O botão **Liquidar selecionados** envia um título por vez. Os totais selecionados são separados por moeda, somados em centavos inteiros com BigInt; não existe conversão nem precificação no navegador.

Cada envio reaproveita `POST /settlements`, transação individual e chave única. Sucessos anteriores permanecem registrados. Erros de negócio e condições alteradas não param os demais títulos. No item alterado, confira **Anterior / Atual**, marque novamente e clique em **Reconfirmar selecionados**: nova intenção, nova chave. A nova tentativa ainda passa pela conferência do servidor.

Falha técnica interrompe novos envios: o item enviado fica com resultado a confirmar e os seguintes selecionados como não processados. Falha de armazenamento antes do envio não envia pagamento. Use **Consultar ou concluir tentativa** para repetir a mesma chave/JSON. Não há retry automático. Após recuperar, selecione explicitamente os itens restantes. Fechar/recarregar interrompe o lote, mas não prova rollback da requisição em voo.

Limites: cesta, resultados e seleções do lote não são persistidos; após recarga, somente tentativas incertas são recuperadas e as liquidações concluídas aparecem no extrato. Refaça a seleção dos pendentes restantes. Limpar/refazer o lote ou abrir revisão/edição individual descarta a revisão coletiva da tela, sem apagar tentativas locais. Múltiplas abas não compartilham uma fila global; a proteção transacional/idempotente final continua no backend.

Cenário real de perda de resposta, recarga e recuperação sem duplicação: [docs/EVIDENCIAS.md](docs/EVIDENCIAS.md).

## Editar pendentes

Em **Liquidar um pendente**, clique em **Editar**. A tela consulta o cadastro atual e permite alterar valor, vencimento, tipo e moeda. Cedente e código permanecem fixos. Abrir a edição descarta a simulação e autorização anteriores; depois de salvar, clique em Revisar para obter novas condições. Editar não liquida e USD não exige cotação disponível para salvar.

`PUT /receivables/{id}` recebe os quatro campos obrigatórios, no formato do schema Simulate da OpenAPI. Reutiliza validação do cadastro e bloqueio `FOR UPDATE NOWAIT` da liquidação. Título liquidado ou em processamento retorna 409. Não há versionamento entre duas edições: a última edição aceita prevalece — isso não permite editar um título liquidado. Em resposta incerta, fechar e abrir Editar consulta o estado atual antes de repetir; não há retry automático.

Cenário real de edição bloqueada em título já liquidado: [docs/EVIDENCIAS.md](docs/EVIDENCIAS.md).

## Importação CSV

Na seção **Importar recebíveis por CSV**, baixe o modelo, escolha o arquivo e clique em **Gerar prévia**. Selecione as linhas válidas e confirme o cadastro. Cada título é cadastrado como PENDING por `POST /receivables`, com revalidação e transação individual; nenhuma liquidação automática. Erros de negócio não impedem os demais; falha técnica interrompe os próximos cadastros e sinaliza resultado incerto. Consulte os pendentes ou reenvie o mesmo arquivo, mantendo os códigos, para verificar duplicidade.

Formato: UTF-8 (BOM opcional), `;`, aspas CSV, decimal com vírgula sem milhar, data `DD/MM/AAAA`. Cabeçalhos obrigatórios: `cedente_codigo;titulo_codigo;tipo;valor_face;vencimento;moeda_pagamento`. Ordem pode variar, sem colunas extras ou repetidas. Tipos: `DUPLICATA_MERCANTIL` e `CHEQUE_PRE_DATADO`; moedas BRL/USD. Modelo em `frontend/public/modelo-recebiveis.csv`; ajuste seus códigos e vencimentos antes do uso.

Limites: 100 títulos por padrão (`CSV_MAX_ROWS`, inteiro positivo), 256 KB por arquivo e 300 KB por requisição multipart. Inválidos contam no limite. Arquivo ilegível, estrutura inválida ou excesso rejeitam a prévia inteira. Duplicados no arquivo invalidam todas as ocorrências; no banco, distinguir duplicado de conflito de dados, sem sobrescrever. Não há histórico nem armazenamento do CSV. Fechar a prévia perde seus erros/resultados; somente cadastros efetivados permanecem.

Endpoint de prévia: `POST /receivables/import-preview`, multipart com campo `file`. Contrato em `/openapi.json`. A prévia não reserva títulos nem garante cadastro posterior.

Cenário real com arquivo misto (válida/desconhecida/duplicadas) e reenvio: [docs/EVIDENCIAS.md](docs/EVIDENCIAS.md).

## Ambiente

### Frontend — fluxo individual

A pasta `frontend` contém React 19.3, TypeScript 7.0.2, Vite 8.3 e Material UI 9.4, com versões exatas e `package-lock.json`. Node 24.x (verificado com 24.19.0). Compatibilidade conferida no registro npm e nas documentações oficiais de [Vite](https://vite.dev/guide/) e [Material UI](https://mui.com/material-ui/getting-started/installation/).

Disponível: cadastro individual como PENDING, simulação informativa após 400 ms e extrato com filtros, paginação e detalhes. A prévia é invalidada ao editar e respostas antigas são ignoradas. Dinheiro trafega e é formatado como string, sem conversão para Number. O cadastro aceita ponto ou vírgula decimal sem milhar; USD pode ser cadastrado mesmo quando a simulação não encontra cotação. Após sucesso, o formulário fica bloqueado até escolher outro cadastro. Em falha técnica, o aviso informa que o resultado pode ser incerto e orienta manter cedente/código ao repetir. A unicidade do backend impede duplicação dessa identificação.

O cadastro não liquida. Em **Liquidar um pendente**, a lista paginada mostra PENDING. Clique em Revisar para obter as condições do título no servidor; marque a autorização e confirme. Se as condições mudarem, a tela apresenta anterior/atual, desmarca a autorização e exige nova confirmação com outra chave. Após sucesso, atualiza pendentes e extrato. A prévia do cadastro não é reaproveitada como autorização de pagamento.

Antes do envio, a chave e o JSON exato da tentativa são salvos no localStorage. Falha de rede, timeout de 15 segundos, resposta técnica ou operação ocupada preservam a tentativa. **Consultar ou concluir tentativa** repete somente esse pedido/chave e pode efetivar a liquidação ainda não realizada. Não há reenvio automático, inclusive após recarga. Resposta definitiva remove a tentativa; condições alteradas voltam à revisão. Se o armazenamento falhar antes do envio, nada é enviado. Tentativas pendentes bloqueiam novas confirmações nesta tela até serem esclarecidas.

A recuperação vale apenas no mesmo navegador/perfil e origem (use sempre `http://127.0.0.1:5173`; localhost é outra origem). Não limpar dados do navegador enquanto houver tentativas incertas. Múltiplas abas podem iniciar tentativas distintas; a proteção final contra duplicação permanece no backend. Os registros locais usam uma chave por tentativa, sem sobrescrever outras tentativas. Cenário real de reconfirmação (cotação alterada) e recuperação (resposta perdida, recarga, replay) em [docs/EVIDENCIAS.md](docs/EVIDENCIAS.md).

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

No PowerShell, use npm.cmd para não depender da política de execução do npm.ps1. Se o carregador padrão do Vite falhar com `spawn EPERM` (comum em ambientes com restrição de permissão a processos filho), use as alternativas abaixo, que não exigem mudar permissões do sistema:

```powershell
npm.cmd test -- --configLoader native --pool threads
npm.cmd run build -- --configLoader native
npm.cmd run dev -- --configLoader native
```

O build gera `frontend/dist`; o proxy descrito acima é de desenvolvimento, não configuração de publicação. Testes de componentes usam respostas simuladas — cenários reais contra a API e o checklist manual ponta a ponta estão em [docs/EVIDENCIAS.md](docs/EVIDENCIAS.md).

### Backend

Java 21, Maven Wrapper 3.9.16 e Spring Boot 4.1.1. PostgreSQL 17-alpine em Docker Compose; Flyway versiona SQL e Hibernate está configurado com `validate`. Dependências seguem o gerenciamento do Spring Boot. Preservada a porta local 5432 e o serviço postgres do projeto existente. Docker precisa estar acessível para executar integração.

## Testes unitários

No Windows, a partir desta pasta:

```powershell
.\mvnw.cmd -B -ntp test
```

No Linux/macOS: `./mvnw -B -ntp test` (pode ser necessário conceder permissão de execução ao arquivo).

O primeiro uso baixa Maven e dependências. Se o download automático do Wrapper falhar (bloqueio de rede/PowerShell), instale Maven 3.9.16 localmente e aponte `MAVEN_HOME` para o cache do Wrapper — sem editar o script distribuído pelo Maven.

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

120 testes de backend (98 sem banco + 22 integração PostgreSQL via Testcontainers) e 32 de frontend, todos aprovados, cobrindo cadastro, câmbio, liquidação, reconfirmação, replay, rollback, concorrência, edição, extrato e CSV. Histórico de execuções e a aferição de latência (p95 6,9 ms local, não é teste de carga) em [docs/EVIDENCIAS.md](docs/EVIDENCIAS.md) e [docs/PERFORMANCE.md](docs/PERFORMANCE.md). Para repetir: `docker compose up -d postgres` seguido de `verify`, em um terminal com acesso ao Docker Engine.

## Limitações conhecidas

Escopo fechado para Fullstack Júnior: sem CI, observabilidade, optimistic locking, integração cambial externa, C4 ou Compose orquestrando a aplicação — cortes documentados em [DECISIONS.md §10](DECISIONS.md). Além disso: câmbio é cadastrado manualmente, sem autenticação na demonstração, sem histórico de importação CSV, e a fila de lote/recuperação de tentativas vive no navegador (localStorage), não no backend. Pendências de entrega (setup em ambiente limpo, acesso dos avaliadores, horário limite) em [docs/CHECKLIST-ENTREGA.md](docs/CHECKLIST-ENTREGA.md).

## Stack e organização

Monólito para manter transação local entre título e snapshot. Java/BigDecimal preserva precisão decimal; domínio independente de Spring facilita aferir o cálculo. PostgreSQL fornece constraints/locks/transações, Flyway versiona o schema. React/TypeScript separa componentes de cadastro/importação/edição e mantém o fluxo de liquidação no painel, sem store global. Material UI reduz trabalho de controles básicos. O servidor determina valores; o navegador somente apresenta e soma centavos já calculados.

Estratégia Git: branches curtas por assunto, revisão e merge, sem releases paralelas ou Git Flow completo para um case individual — GitHub Flow simplificado. O desenvolvimento funcional (motor, API, frontend) foi integrado em um único commit em `feat/project-setup`, branch padrão do repositório privado no GitHub; a partir daqui, ajustes e documentação seguem em branches curtas com PR, sem reescrever a cronologia já publicada nem simular sequência TDD retroativa. Acesso dos avaliadores: pendente, ver docs/CHECKLIST-ENTREGA.md.
