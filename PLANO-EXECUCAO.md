# SRM Credit Engine — Plano até 18/09/2026

## Diretório de trabalho vigente

Todo desenvolvimento segue em `C:\Users\Arthur\Documents\GitHub\Desafio_tecnico_SRM`, branch `feat/project-setup`. A antiga cópia em outputs/implementation é histórica; não implementar nela.

## Nível da vaga e critério de fechamento

Alvo informado: **Fullstack Júnior**. O fechamento prioriza corretude do motor, golden cases, API/frontend reproduzíveis, persistência relacional, validação, idempotência, README executável, SPEC, REVIEW do Anexo A e testes. CI, métricas, C4, integração cambial externa, optimistic locking e arquitetura de escala são diferenciais de níveis superiores e permanecem fora do escopo deliberadamente, conforme `DECISIONS.md`.

## Situação vigente em 16/09

Atualização vigente em 16/09, 04:46 -03:00: lote implementado no frontend com envio individual sequencial, resultados por título, totais por moeda, reconfirmação e recuperação. 32 testes frontend e build aprovados. MCP Playwright confirmou sucesso parcial, anterior/atual desmarcado, reconfirmação, perda de resposta após commit, recarga e replay exato sem duplicação. Backend inalterado neste incremento; 98 testes sem banco + 22 integrações validados pelo candidato às 04:37:42, relatório conferido. Próximo passo: revisão documental final e preparação da entrega. Registros e limitações no README/AI_USAGE; atualizações anteriores abaixo são históricas.

O checklist manual de ponta a ponta foi concluído pelo candidato em 16/09: cadastro, edição, CSV, lote, extrato e recarga. Os parágrafos seguintes registram o histórico dos incrementos anteriores.

### Histórico de incrementos

Histórico — edição (23:12 -03:00): edição restrita de PENDING implementada com bloqueio compartilhado com liquidação. 98 testes sem banco, 26 frontend e build aprovados. Playwright confirmou edição dos quatro campos, nova revisão sem autorização e rejeição de alteração em SETTLED. CSV validado pelo candidato com 20 integrações aprovadas às 22:50:57; relatório conferido. Dois testes de edição adicionados: executar `verify` com 22 integrações antes de encerrar esta validação. Próximo incremento funcional: seleção e liquidação em lote.

Histórico — CSV (22:46 -03:00): CSV implementado e testado pelo MCP Playwright contra a API real. Arquivo misto mostrou erros e duplicados; confirmação cadastrou somente a linha válida como PENDING; reenvio detectou duplicado e não criou liquidação. 95 testes sem banco, 23 frontend e build aprovados. `verify` da IA estava bloqueado pelo pipe Docker, sem validar integrações naquele momento. Próximo passo histórico: candidato executar `mvnw.cmd verify`; depois edição restrita de pendentes.

Fluxo individual conferido pelo Playwright: cadastro USD, alteração de cotação 5,50 → 6,00 sem atualizar a prévia, comparativo anterior/atual, autorização desmarcada e reconfirmação obrigatória. Perda de resposta após commit, recarga sem envio automático e repetição manual com mesma chave/JSON retornaram uma única liquidação. BRL já havia sido confirmado pelo usuário. Registros de teste preservados, detalhes no AI_USAGE. Os parágrafos seguintes registram o histórico anterior.

Liquidação individual implementada e validada nos cenários automatizados: cadastro, simulação, reconfirmação, replay, snapshot, rollback e concorrência. Em 14/09/2026 às 17:42:03 -03:00, o usuário concluiu `mvnw verify` com BUILD SUCCESS. Relatórios locais conferidos: 73 testes de domínio/serviço/API/OpenAPI e 16 de integração PostgreSQL, total de 89, sem falhas, erros ou testes ignorados. Os 16 já incluem os testes de integração anteriores; não somar os 6 antigos novamente.

O Docker responde no terminal do usuário. A restrição ao pipe permanece específica do ambiente da IA; não é falha do engine nem pendência de validação da liquidação anterior. O extrato foi implementado e validado em 15/09: `verify` do candidato às 00:09:54 -03:00, com 88 testes sem banco e 19 integrações aprovadas (107 no total). Relatórios locais conferidos. Frontend individual implementado com cadastro, simulação, liquidação, reconfirmação e recuperação local; validação manual ponta a ponta, CSV e lote continuam pendentes. A etapa 1 ainda não está concluída.

## Extrato concluído — próximo incremento: painel mínimo

1. Implementar `GET /settlements` com filtros combináveis por período de liquidação, cedente e moeda.
2. Aplicar início inclusivo e fim exclusivo, paginação base zero (20 por padrão, máximo 100) e ordenação estável por instante/ID.
3. Retornar snapshots persistidos, sem recalcular valores históricos; validar parâmetros e preservar os erros estruturados.
4. Testar filtros isolados/combinados, limites temporais, paginação e valores retornados; atualizar OpenAPI e README.
5. Após validar o extrato, implementar o painel mínimo de cadastro, simulação, reconfirmação, liquidação e consulta.

CSV, edição de pendentes e lote permanecem nas etapas seguintes, sem cortes de escopo. A recuperação local da tentativa individual foi antecipada; sua integração ao lote segue pendente.

Histórico — extrato (15/09): endpoint, validações e OpenAPI implementados e validados. Os 15 casos novos sem banco e 3 testes novos PostgreSQL passaram na execução do candidato. Próxima ação histórica: painel React/TypeScript/Vite/Material UI, começando por estrutura e extrato; depois cadastro, simulação automática e confirmação segura.

A tentativa de `verify` da IA em 15/09 falhou na inicialização do Docker/Testcontainers (19 erros de integração, antes de validar os cenários). Os 88 testes sem banco passaram nessa mesma execução. Esse bloqueio foi superado pela execução posterior do candidato, com os 19 testes de integração aprovados.

## Histórico inicial do ambiente

As constatações abaixo são históricas, anteriores à integração no repositório:

- Workspace atual contém documentos e arquivos de trabalho, sem aplicação.
- Downloads contém `credit-engine.zip`. Inspeção somente de leitura identificou um scaffold Spring Boot 4.1.1/Java 21, Maven Wrapper, classe inicial e teste de contexto. Não foram identificadas classes de regras de negócio na listagem examinada.
- Java Temurin 21.0.12.1, Node 24.19.0, npm 11.17.0 e Git 2.53.0 respondem no terminal.
- Docker CLI 29.7.2 e Compose 5.5.1 estão instalados, mas o engine `desktop-linux` não responde: pipe `dockerDesktopLinuxEngine` ausente. Ainda não está determinado se basta iniciar Docker Desktop ou se há falha de configuração.
- Não foram executados builds, testes de aplicação ou downloads de dependências. Versões de bibliotecas ainda precisam ser conferidas.
- Não foi localizado um `pom.xml` extraído na busca em Documents/Codex. Isso não exclui um repositório em outra pasta.

## Referências vigentes

[SPEC.md](SPEC.md) resume premissas. [DECISIONS.md](DECISIONS.md) registra todas as decisões aprovadas e substitui escolhas conflitantes do consolidado anterior. [REQUISITOS-v2.md](REQUISITOS-v2.md) orienta os incrementos funcionais. Não usar SPEC-revisado ou o consolidado anterior isoladamente para gerar código.

## 14/09 — Documentação, ambiente e motor

O scaffold inicialmente trabalhado em cópia isolada foi integrado ao repositório principal. Motor, datas, API e persistência foram implementados em incrementos. A validação daquela versão foi de 89 testes aprovados; o histórico de 27 testes e bloqueios iniciais está registrado no AI_USAGE.

- [x] Consolidar decisões aprovadas e definir duas etapas.
- [x] Inspecionar scaffold e disponibilidade das ferramentas.
- [x] Confirmar repositório principal: `C:\Users\Arthur\Documents\GitHub\Desafio_tecnico_SRM`; código integrado nele.
- [x] Confirmar Docker Engine no terminal do usuário e validar sintaxe do Compose. Restrição do ambiente da IA identificada, sem reinstalação.
- [x] Conferir dependências, inicializar estrutura e branch apropriada, preparar banco e validar migration inicial em PostgreSQL.
- [x] Implementar Strategies e composição aditiva das taxas, cálculo exato e HALF_EVEN.
- [x] Testar C1 92859.94, C2 23337.77 e C3 17094.67, deságios e bordas de arredondamento.
- [x] Implementar conversão de datas com relógio injetável e testar virada de mês/ano, bissexto, hoje e vencido.

Conclusão: motor e backend individual testados, incluindo integração PostgreSQL executada pelo usuário.

## 15/09 — Core individual, primeira versão utilizável

- [x] Validar em PostgreSQL as migrations e os seeds já escritos (execução anterior do usuário, 6 testes aprovados).
- [x] Implementar cadastro individual, simulação e seleção de cotação por vigência/expiração; testes sem banco aprovados.
- [x] Validar cadastro/simulação pela API com PostgreSQL real via Testcontainers (incremento anterior).
- [x] Implementar liquidação com comparação de condições, idempotência e snapshot de auditoria.
- [x] Validar os novos testes PostgreSQL de liquidação, rollback e concorrência (16 integrações aprovadas em 14/09).
- [x] Testcontainers: unicidade, rollback, repetição e valores persistidos.
- [x] Documentar no OpenAPI os endpoints implementados de cadastro, simulação e liquidação.
- [x] Implementar e documentar extrato filtrado por período, cedente e moeda; testes HTTP sem banco aprovados.
- [x] Validar os novos testes PostgreSQL do extrato: filtros, limites temporais, paginação e snapshots históricos (19 integrações aprovadas em 15/09).
- [x] Painel mínimo: cadastrar, simular, reconfirmar, liquidar e consultar.
  - [x] Iniciar React/TypeScript/Vite/Material UI e consulta do extrato com filtros, paginação e detalhes.
  - [x] Verificar tipos, build e 6 testes do frontend (com respostas simuladas da API).
  - [x] Conferir visualmente e testar a consulta contra a API real; bloqueio inicial superado com MCP Playwright.
  - [x] Cadastro individual como PENDING e simulação informativa após 400 ms; descartar respostas antigas e invalidar prévia ao editar.
  - [x] Verificar o incremento com 11 testes totais de frontend, checagem TypeScript e build.
  - [x] Selecionar pendente e obter condições para liquidação; reconfirmação explícita antes de pagamento.
  - [x] Liquidação pela interface, mesma chave/payload em resultado incerto e confirmação de sucesso.
  - [x] Validar 20 testes totais de frontend, TypeScript e build; respostas da API simuladas nos testes.
  - [x] Validar fluxo BRL/USD contra a API real: BRL pelo usuário; USD, condições alteradas e resultado incerto pelo MCP Playwright.

Conclusão da etapa 1: fluxo BRL e USD funcionando pela interface, sem duplicação por repetição; README provisório permite rodar. Não declarar concluída apenas porque endpoints funcionam isoladamente.

## 16/09 — CSV e gestão dos pendentes

- [x] Disponibilizar modelo CSV e lista de códigos de cedentes.
- [x] Parser/validação de arquivo e de linha, limite 100, duplicidade no arquivo e banco.
- [x] Prévia com erros, confirmação explícita das válidas, cadastro sem liquidar.
- [x] Listagem de pendentes e edição restrita, com invalidação de simulação.
- [x] Testes de parser e componente CSV: erros, seleção, invalidação ao trocar arquivo e interrupção técnica. Troca de arquivo bloqueada durante requisições.
- [x] Executar as 20 integrações PostgreSQL, incluindo CSV: candidato, 15/09 às 22:50:57 -03:00, sem falhas/erros/ignorados.
- [x] Executar as 22 integrações: candidato em 16/09 às 04:37:42, sem falhas/erros/ignorados; inclui edição e bloqueio compartilhado.

Conclusão: importar arquivo misto, cadastrar apenas os escolhidos válidos e reenviar sem duplicar títulos.

## 17/09 — Lote, recuperação e fechamento

- [x] Orquestração sequencial, transação por título, sucesso parcial e interrupção técnica.
- [x] Mostrar valores anteriores/atuais e reconfirmar selecionados, sem pré-seleção.
- [x] Persistir tentativas incertas individuais no navegador e recuperar por mesma chave/payload (antecipado no painel individual; testes de componente aprovados).
- [x] Integrar a recuperação ao lote: Playwright confirmou recarga/replay após perda de resposta; teste de componente confirmou interrupção ao desmontar. Não há persistência da cesta ou fila automática após recarga.
- [x] Executar checklist manual completo com dados fictícios e testar atualizações de página; usuário confirmou em 16/09 que cadastro, edição, CSV, lote, extrato e recarga funcionaram conforme planejado.
- [ ] Finalizar REVIEW, AI_USAGE, DECISIONS, README, ER e conferir tamanho final do SPEC.
  - [x] Criar REVIEW do Anexo A, ER e checklist; corrigir informações antigas de execução no README/DECISIONS.
  - [x] Aferir meta local de latência: p95 6,9136 ms em 100 chamadas sequenciais BRL; método e limites em docs/PERFORMANCE.md.
- [ ] Revisão humana final dos documentos e paginação final do SPEC. Git ainda sem commits/remoto; entrega não publicada.
- [ ] Congelar novas funcionalidades ao fim do dia. Reservar bloco de 2–3 horas para correções e documentação, não preencher todo o tempo com funcionalidades.

Conclusão: etapa 2 funcional e decisões consistentes com o código. Itens ainda incompletos devem aparecer como pendência real, não como teste passado ou corte previamente aprovado.

## 18/09 — Entrega

- [ ] Rodar a partir de configuração limpa e do README, sem apagar dados pessoais ou banco existente.
- [ ] Executar testes necessários e corrigir bloqueadores.
- [ ] Conferir documentos, segredos fora do Git, branch de funcionalidade e acesso dos avaliadores.
- [ ] Ensaiar explicação do cálculo, prazo, ACID, idempotência, fluxo frontend e uma mudança pequena.
- [ ] Enviar antes do horário limite informado no e-mail. O horário ainda não foi fornecido nesta conversa; não presumir 23h59.

## Critérios manuais mínimos

1. Cadastrar/simular/liquidar BRL e USD; comparar os valores com a API e persistência.
2. Vencimento hoje, 31/01 para fevereiro e março; título vencido rejeitado.
3. Cotação futura ignorada, vigente aceita, expirada bloqueada; BRL independente.
4. Mudar condições após simular: nenhuma liquidação até nova confirmação.
5. Repetir a mesma tentativa: mesmo resultado, uma liquidação.
6. CSV com válidas, inválidas e duplicadas: prévia, confirmação e resultados claros.
7. Falha de negócio em um título permite os seguintes; falha técnica interrompe.
8. Atualizar a página após resultado incerto: recuperar e repetir a mesma intenção sem novo pagamento.
9. Editar PENDING e invalidar a simulação; SETTLED não editável.
10. Extrato com filtros combinados, limites temporais e moedas explícitas.

## Dependências e risco de prazo

A validação PostgreSQL está disponível no terminal do usuário; novas mudanças que dependam do banco ainda precisam de nova execução de verify. Não contornar a restrição ao pipe do ambiente da IA nem confundir uma execução antiga com validação de código novo. O cronograma é uma meta, não estimativa validada de capacidade. Se o core não estiver utilizável pela interface ao fim de 15/09, revisar a ordem da etapa 2 e comunicar o impacto. Não suprimir integridade, testes financeiros ou documentação para encaixar funcionalidades.
