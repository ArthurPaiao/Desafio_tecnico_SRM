# Uso de IA — registro incremental

## Auditoria documental final — 16/09/2026

Prompt: “ok podemos continuar”. A IA conferiu o estado do Git, as referências atuais do README, plano, checklist e AI_USAGE, e os documentos de entrega. Corrigiu somente textos desatualizados: o README agora registra o checklist manual concluído e separa as pendências de revisão/publicação; o plano identifica a situação vigente de 16/09, rotula os incrementos antigos como histórico e não aponta o checklist como próximo passo; o checklist separa SPEC concluída da aferição visual de paginação. Nenhum código, teste, endpoint ou regra financeira foi alterado. O Git continua sem commits/remoto; permanecem pendentes revisão humana, setup limpo e publicação autorizada pelo candidato.

## Checklist manual concluído — 16/09/2026

O candidato informou que executou o checklist no navegador real e que tudo funcionou conforme planejado: cadastro PENDING, edição com nova revisão, importação CSV, lote, extrato e atualização da página. Essa é uma validação manual do candidato, separada dos testes automatizados e dos cenários Playwright registrados anteriormente. Nenhum novo código foi alterado nesta conferência.

## Critério de senioridade — 16/09/2026

O candidato informou que a vaga é Fullstack Júnior. O plano passou a priorizar corretude, fluxo completo reproduzível, testes, documentação e capacidade de defesa. Não foram adicionados CI, observabilidade, C4, optimistic locking ou integração cambial externa apenas para preencher critérios de níveis superiores; as limitações permanecem registradas e não são apresentadas como implementadas.

## Revisão de entrega — 16/09/2026

Prompt: “pode prosseguir”, após lote concluído. A IA releu o enunciado local (incluindo Anexo A), SPEC, decisões, README e migration. Criou REVIEW.md exclusivamente sobre o anexo, ordenando SQL injection, atomicidade/sucesso falso, duplicidade, unidade das taxas, precisão, câmbio, validação, auditoria e HTTP. Criou ER a partir da migration, checklist de pendências e justificativa de stack/organização; corrigiu avisos antigos de navegador indisponível e contagens desatualizadas. Não alterou código financeiro ou banco. Ponytail evitou acrescentar infraestrutura fora das decisões aprovadas; a revisão explicitou que os cortes não equivalem ao atendimento integral de pleno/sênior.

Aferição real não mutante: 10 aquecimentos + 100 POST /simulations BRL sequenciais via PowerShell/Stopwatch, em 16/09 às 04:50:50 -03:00. p50 5,1406 ms, p95 6,9136 ms, máximo 13,882 ms, sem exceções. Documentadas condições/reprodução em docs/PERFORMANCE.md; não é teste de carga concorrente. Nenhum cadastro/liquidação criado por essa medição.

Git inspecionado: branch feat/project-setup sem commits e sem remoto configurado. Nenhum stage/commit/push/convite foi realizado. Naquele momento, antes da confirmação manual registrada acima, ainda pendiam revisão humana dos documentos, setup limpo e checklist completo, além da paginação final da SPEC; a IA não declarou entrega pronta. Não houve nova execução das suítes neste incremento documental; permanecem as evidências anteriores de 120 backend e 32 frontend.

## Liquidação em lote — 16/09/2026, 04:46 -03:00

Prompt: “pode iniciar”, após validação da edição com 22 integrações. A IA conferiu failsafe-summary (22 aprovados, zero falhas/erros/ignorados), compatível com screenshot do verify às 04:37:42. Implementou lote no frontend, reutilizando o envio individual existente e a mesma recuperação local, sem novo endpoint/transação coletiva. Seleção até 100 entre páginas, revisão explícita sem pré-seleção, execução sequencial, condições alteradas com anterior/atual e nova chave somente após autorização; erros de negócio permitem continuar, técnicos interrompem próximos envios. Totais por BRL/USD usam BigInt em centavos, sem ponto flutuante financeiro.

Ponytail orientou compartilhar a função de envio para individual/lote/recuperação e não criar infraestrutura de jobs. Ponytail-review preservou validações e não indicou abstrações novas. Cesta e resultados não são persistidos; recarga recupera só pedidos incertos, sem retomar a fila. Próximos títulos exigem seleção manual. Fechar/desmontar impede iniciar outros envios; requisição já enviada pode concluir e exige recuperação. Limitação registrada no README, sem alteração silenciosa das regras financeiras.

Testes: 32 frontend (26 existentes + 6 novos), TypeScript/build aprovados. Novos casos: centavos exatos acima do limite de Number, consentimento explícito/totais por moeda, continuidade/reconfirmação com chave nova, falha técnica/replay exato após remontagem, armazenamento indisponível, clique duplo/desmontagem e falha técnica na simulação. Backend e suas 120 verificações não foram modificados/reexecutados neste incremento.

Playwright real: frontend/Spring inicialmente desligados; iniciados sem recriar banco ou volumes. Criados somente títulos fictícios AUTO-LOTE-20260916-0444-1/2/3. Após revisão, item 2 editado via API para face 120.00; execução liquidou itens 1 e 3 (BRL 92.86 cada) e devolveu item 2 para reconfirmação (USD 15.48 → 18.57), desmarcado. Em seguida, descartada propositalmente a resposta após commit da reconfirmação; recarga preservou chave/JSON. Recuperação manual retornou HTTP 200 com ambos idênticos. Consulta mostrou exatamente três liquidações: 07010eb2-535b-4cc0-8cf9-8f4204bb4397, 9436ddc6-d46b-4209-adcc-428514aab9e5 e cadd10f3-e397-4217-bc2e-9e768045dcaa. Registros mantidos; nenhuma alteração dos títulos do usuário, nem da cotação. Sem commit/push.

## Edição de pendentes — 15/09/2026, 23:12 -03:00

Prompt: “podemos prosseguir”, após confirmar próximo passo de edição restrita. A IA adicionou PUT /receivables/{id}, DTO apenas com valor/tipo/vencimento/moeda, validação compartilhada do cadastro e bloqueio FOR UPDATE NOWAIT já usado na liquidação. Identidade preservada, SETTLED rejeitado; sem migration ou dependência nova. Duas edições não têm versionamento; última aceita prevalece, limitação documentada. A comparação de condições na liquidação continua usando dados atuais do servidor.

Frontend: botão Editar consulta cadastro atual, remove prévia/autorização, permite salvar os quatro campos e exige nova revisão. Resultado técnico incerto bloqueia novo envio até fechar/reabrir para consultar o estado. Recuperação de liquidação não foi modificada; edição fica bloqueada quando há tentativa pendente. Ponytail orientou reaproveitar o lock, validação e formulário nativo; Ponytail-review não identificou camadas ou dependências dispensáveis. Não houve cortes nas decisões aprovadas.

Verificação: 98 testes sem banco (3 novos), 26 frontend (3 novos), TypeScript/build aprovados. OpenAPI 0.5.0, PUT documentado e testado. Dois testes PostgreSQL adicionados, ainda não executados: edição invalida condições/SETTLED permanece imutável e edição/liquidação respeitam o mesmo lock. A execução anterior do candidato foi conferida no failsafe-summary: 20 testes aprovados, compatível com screenshot BUILD SUCCESS às 22:50:57. Esses 20 não cobrem as novas alterações.

MCP Playwright contra banco real: abriu revisão de AUTO-CSV-20260915-2245, marcou autorização, abriu edição e descartou a confirmação anterior. Salvou face 1250.50, CHEQUE_PRE_DATADO, vencimento 2026-12-20 e BRL. GET confirmou identidade/createdAt preservados e PENDING; nova revisão sem autorização marcada. Tentativa PUT no título fictício já liquidado AUTO-RECONF-20260915-2217 retornou 409 ALREADY_SETTLED, valor original 1000.00 preservado. Nenhuma nova liquidação. Uma interação Playwright inicialmente falhou ao localizar select por label exato; ajustada para combobox pelo nome acessível, sem alterar regras do produto. Registros fictícios preservados; nenhum commit/push.

## Importação CSV e testes reais — 15/09/2026, 22:46 -03:00

Prompt: “pode prosseguir”, após proposta de iniciar CSV. Implementados parser Apache Commons CSV 1.14.1 (versão conferida no repositório oficial https://github.com/apache/commons-csv), UTF-8 estrito/BOM, cabeçalhos, limite configurável de 100 títulos e 256 KB, validação por linha, repetidos no arquivo e duplicado/conflito no banco. Endpoint de prévia sem persistência, modelo CSV e interface com seleção explícita. Confirmação sequencial reutiliza POST /receivables e validação compartilhada do cadastro, sem liquidação nem sobrescrita. Falha técnica interrompe os próximos itens; correção e reenvio externos. Sem histórico/importação persistida, conforme decisões aprovadas.

Ponytail orientou reutilizar o cadastro individual e sua transação, sem criar outro endpoint de confirmação ou parser artesanal. Ponytail-review aplicado à complexidade: sem camadas/abstrações extras; preservadas validações, parser e testes. Revisão visual detectou que as mensagens ficavam fora da área inicial da tabela; campos foram agrupados para manter os resultados visíveis no desktop. Não houve mudança nas decisões financeiras do candidato.

Verificação: 95 testes sem banco aprovados (7 novos), 23 testes frontend (3 novos), TypeScript/build aprovados. OpenAPI atualizado para 0.4.0, 10 caminhos. Adicionado teste PostgreSQL de prévia sem persistência, cadastro e duplicidade na confirmação/reenvio. `verify` falhou na inicialização do Docker/Testcontainers por AccessDenied no pipe: 20 erros antes dos cenários de integração. Não declarar essas integrações aprovadas; execução no terminal do candidato pendente.

MCP Playwright contra aplicação real: arquivo misto de quatro linhas (válida, cedente desconhecido, duas duplicadas); nenhum item pré-selecionado, inválidas bloqueadas. Confirmação cadastrou só `AUTO-CSV-20260915-2245`, ID `2353eb67-d472-4366-be1b-8d7f7b80877c`, como PENDING. Reenvio sinalizou duplicado; exatamente um título e número de liquidações permaneceu 3. Registro de teste preservado. Spring reiniciado para carregar a nova dependência; banco e registros anteriores preservados. Nenhum commit ou push.

## Validação real da reconfirmação e recuperação — 15/09/2026, 22:34 -03:00

MCP Playwright: título fictício `AUTO-RECONF-20260915-2217`, face BRL 1000, vencimento 15/12/2026, pagamento USD. Cotação alterada de 5,50 para 6,00 após revisão: tentativa retornou CONDITIONS_CHANGED, comparativo anterior/atual (USD 168,84 → 154,77), autorização desmarcada e botão Reconfirmar desabilitado até nova marcação. Nenhuma liquidação na primeira tentativa.

Na reconfirmação, Playwright aguardou o servidor concluir a requisição e descartou a resposta para simular perda de rede. A tentativa foi preservada no localStorage; recarga não fez reenvio automático. Ação manual repetiu exatamente a chave e o JSON, retornou HTTP 200 e removeu a tentativa. Consulta confirmou somente uma liquidação, ID `bd736f62-04e9-43e1-b220-ab64a320a635`, USD 154,77. Cotação de teste 6,00 e título/liquidação mantidos. Isso valida esses cenários reais, não todo o roteiro E2E ou testes de concorrência futuros.

## Liquidação individual no frontend — 15/09/2026

Prompt: “pode implementar”, após proposta de seleção de pendentes e liquidação com reconfirmação segura. A IA implementou listagem PENDING paginada, revisão das condições retornadas pela API, autorização explícita, POST com chave de idempotência, comparação anterior/atual em CONDITIONS_CHANGED e nova autorização desmarcada. Respostas antigas de simulação são ignoradas; clique duplo é bloqueado por referência síncrona. Cadastro e sucesso de liquidação atualizam as consultas.

A recuperação local individual foi antecipada: gravação da chave e JSON exato antes de enviar, timeout de 15 segundos tratado como resultado a confirmar, repetição exclusivamente manual, retomada após recarga sem POST automático e remoção somente após resposta definitiva. Cada tentativa usa sua própria chave local; nenhuma tentativa é sobrescrita pela criação de outra. Falha de armazenamento antes do POST impede o envio. A recuperação depende do navegador/perfil/origem; integridade financeira entre abas continua sob responsabilidade do backend. Não há transferência bancária real.

Ponytail orientou reutilizar fetch, localStorage e os componentes existentes, sem novas dependências ou cálculos financeiros no navegador. A revisão de complexidade preservou as validações e a separação do componente de liquidação; não identificou abstrações dispensáveis. Foram adicionados 9 casos automatizados: consentimento/clique duplo, reconfirmação/nova chave, replay exato após remontagem, três status incertos, cotação expirada, armazenamento indisponível e resposta antiga. Total: 20 testes frontend aprovados, TypeScript e build aprovados. Nenhuma liquidação/cadastro real foi feita por estes testes, nenhum código backend foi alterado e sua suíte não foi reexecutada. Validação manual ponta a ponta permanece pendente. Nenhum commit ou push foi realizado.

## Cadastro e simulação no painel — 15/09/2026

Prompt: “vamos continuar com o plano de acao”. A IA acrescentou formulário de cadastro como PENDING, simulação informativa após 400 ms com dados válidos, descarte de respostas antigas, bloqueio síncrono de submissão repetida e mensagens de erro por campo. Valores permanecem strings; ponto/vírgula são normalizados sem aritmética financeira no navegador. O cadastro independe da disponibilidade da cotação para USD. Falhas técnicas de cadastro são apresentadas como resultado incerto, sem retry automático.

A Ponytail orientou reaproveitar o carregamento de cedentes, o formatador decimal e componentes nativos/Material UI, sem novas dependências. Na conferência do contrato, o tipo de cheque foi alinhado a CHEQUE_PRE_DATADO e os erros de campo ao array field/message retornado pela API. O teste inicial de clique duplo falhou porque procurava o texto do estado intermediário antes de o React concluir o lote de atualizações; corrigido para clicar duas vezes no mesmo botão e verificar uma única requisição.

Verificação: 11 testes de frontend aprovados (6 existentes e 5 novos), TypeScript e build aprovados com as opções nativas já documentadas. Nenhum teste fez cadastro ou liquidação no banco real, e nenhum código de backend foi alterado. Revisão de complexidade manteve as validações e não identificou dependências ou abstrações novas dispensáveis. Confirmação de pagamento, reconfirmação, seleção de pendentes e recuperação continuam como próximos incrementos; não declarar etapa 1 concluída. Nenhum commit ou push foi realizado.

## Validação do extrato e início do painel — 15/09/2026

Prompt: “atualize as informacoes do plano de execucao, readme e aiusage apos isso comece o proximo passo”. O candidato compartilhou BUILD SUCCESS de `verify` às 00:09:54 -03:00. A IA conferiu os relatórios locais Surefire e Failsafe: 88 testes sem banco e 19 integrações PostgreSQL aprovados, total de 107, sem falhas, erros ou ignorados. O extrato está validado nos cenários automatizados implementados; isso não valida a futura interface.

A skill documentation-and-adrs orientou a atualização do status sem apagar os bloqueios históricos. Próximo trabalho autorizado: painel React/TypeScript/Vite/Material UI, iniciando pela estrutura e consulta do extrato. Cadastro, simulação automática e confirmação segura serão incrementos seguintes dentro da etapa 1; CSV e lote continuam no plano. Ponytail orienta reutilizar a API e manter cálculos financeiros exclusivamente no backend.

Implementado o primeiro incremento em `frontend`: setup com versões exatas/lockfile, proxy local, consulta do extrato, filtros, paginação, snapshot, carregamento, vazio, erro e descarte de respostas antigas. Seis testes de apresentação/componente passaram com respostas simuladas; checagem TypeScript e build também passaram. Nenhuma alteração no backend. A evidência de 107 testes de backend é da execução do candidato; os 6 testes do frontend foram executados pela IA separadamente.

Erros detectados na primeira verificação: faltava declaração de tipos para CSS, e propriedades de alinhamento do Stack não eram aceitas pelo Material UI instalado; corrigidos com `vite/client` e `sx`. A execução padrão também encontrou `spawn EPERM` no carregamento da configuração Vite; opções suportadas `--configLoader native` e `--pool threads` permitiram testes e build, sem modificar código de terceiros. O cache npm foi mantido em diretório gravável do repositório, ignorado pelo Git; instalação/auditoria reportou zero vulnerabilidades conhecidas naquele momento, sem equivaler a uma auditoria de segurança completa.

Ponytail favoreceu componentes gratuitos, fetch/AbortController/URLSearchParams nativos e formatação decimal sem Number. Não foram adicionados gerenciador global de estado, biblioteca de cálculo financeiro, grid paga ou suíte E2E. A tentativa de abrir o navegador integrado falhou ao preparar os recursos da ferramenta; não houve inspeção visual nem validação do frontend contra a API real. Cadastro e confirmação continuam pendentes. Nenhum commit ou push foi realizado.

## Extrato de liquidações — 15/09/2026

Prompt: “ok, continue o plano de execucao”. A IA implementou `GET /settlements` com filtros opcionais combináveis por instante, cedente e moeda, paginação limitada e ordenação por instante/ID decrescentes. A consulta retorna snapshots sem recalcular os dados históricos. Limites temporais usam início inclusivo/fim exclusivo, offset obrigatório e precisão compatível com PostgreSQL.

As skills locais incremental-implementation, test-driven-development e api-and-interface-design orientaram o incremento. O teste inicial falhou na compilação por ausência da consulta Specification no repositório; depois da implementação, os testes focados passaram. A suíte completa sem banco passou com 88 casos. Três testes de integração foram adicionados para filtros, limites, paginação e preservação de snapshots: a suíte ampliada prevê 19 integrações e ainda depende de validação com Docker acessível. Os 89 testes aprovados em 14/09 são evidência da versão anterior, não desta mudança.

Ponytail orientou o uso de Spring Data Specification e a extração da validação de paginação já existente para PageRequests, sem novas dependências, migrations ou motor de consulta próprio. A revisão de complexidade manteve as validações e os testes. A skill documentation-and-adrs orientou a atualização de OpenAPI, README e plano com as limitações reais: paginação não congela a coleção, filtros não recalculam valores, frontend permanece pendente. Nenhum commit ou push foi realizado.

Nova tentativa de `verify` às 00:06:34 -03:00 em 15/09: os 88 testes sem banco passaram novamente, mas os 19 de integração terminaram com erro de inicialização porque Testcontainers não encontrou ambiente Docker válido; o log informa que o pipe não está respondendo. Nenhum cenário PostgreSQL novo foi executado com sucesso. A tentativa substituiu os relatórios locais anteriores, cujo resultado histórico está registrado abaixo. A IA não tentou contornar as permissões do ambiente.

## Escopo delegado

A IA ajudou a revisar requisitos, explicitar decisões escolhidas pelo candidato e implementar motor decimal, datas, cadastros, simulação, liquidação individual, testes e documentação. A orientação foi manter o domínio independente de banco e preservar o cálculo composto mensal, a ordem de arredondamento e as decisões aprovadas de integridade.

## Erros e verificação

A revisão documental inicial afirmava que usar MathContext finito não arredondaria intermediários. Isso foi corrigido durante a conferência técnica: o motor usa potência exata e arredonda diretamente na divisão final. Os testes C1–C3 e o caso de conversão do VP previamente arredondado verificam o comportamento implementado.

O documento inicial também excluía idempotência do escopo júnior. A leitura da seção core do enunciado mostrou sua exigência; foi restaurada no plano e posteriormente implementada e testada no incremento de liquidação descrito abaixo.

## Decisões do candidato

O candidato escolheu a regra de aniversários mensais, reconfirmação, lote parcial, CSV, tecnologia e estratégia de testes entre alternativas discutidas. Também executou verify no próprio terminal e compartilhou a evidência. Essa execução não equivale à aprovação humana de todo o código: entendimento, revisão, defesa técnica, cortes de escopo e envio continuam sob responsabilidade do candidato.

## Histórico da integração inicial

Na integração ao repositório correto, foram aplicadas as skills locais ponytail e ponytail-review. A revisão identificou Lombok sem uso e 54 linhas de configuração/metadados dispensáveis no POM importado. Os trechos foram removidos e os 27 testes unitários passaram novamente com clean test. O teste de contexto fornecido pelo candidato foi preservado na fase de integração com Testcontainers. Registro em docs/reviews/ponytail-2026-09-14.md.

Naquela etapa, 27 testes unitários passaram e o Compose foi validado sintaticamente. O Docker estava inacessível ao ambiente da IA; ainda não havia evidência de testes de banco aprovados. O build usa o scaffold fornecido em credit-engine.zip e foi executado com Java 21. As validações posteriores estão registradas abaixo.

## Incremento de cadastro e simulação — 14/09/2026

Prompt de continuidade: “ok, pode iniciar, tendo como base tudo oque te passei ja”; retomada: “continue de onde paramos”. A IA implementou persistência JPA para cadastros/configuração, endpoints de cadastro/consulta/simulação, contrato decimal em strings, erros HTTP e OpenAPI estático, seguindo as decisões previamente escolhidas pelo candidato.

A revisão detectou um risco de precisão temporal: um instante Java com nanos poderia ser arredondado pelo PostgreSQL para um microssegundo futuro na consulta cambial. A referência foi truncada antes da consulta e um teste de regressão foi adicionado. Isso não substitui a verificação do driver com banco real.

Ponytail orientou o reaproveitamento de DecimalRules, das Strategies existentes e da paginação Spring Data, sem novas dependências ou abstrações genéricas. Os 61 testes de domínio/serviço/API e checagem estrutural do OpenAPI passaram. Naquele momento o verify foi bloqueado pelo Docker. Posteriormente, o usuário executou os 6 testes de integração com BUILD SUCCESS; a IA conferiu também o failsafe-summary local com zero erros antes de iniciar a liquidação.

## Liquidação individual — 14/09/2026

Prompt: “pode iniciar, use a skill agente skills”. Usadas using-agent-skills, incremental-implementation, test-driven-development, api-and-interface-design e code-review-and-quality, junto da Ponytail. As comparações e testes de serviço/HTTP foram escritos antes das respectivas implementações, com falhas observadas na fase RED. A checagem OpenAPI também falhou antes de atualizar as rotas. O teste de conflitos HTTP revelou erro no próprio mock: restubbing com when invocava o comportamento que já lançava exceção; corrigido para doThrow, sem mudar o serviço para acomodar o teste.

A IA implementou snapshot JPA, comparação normalizada, fingerprint SHA-256, locks PostgreSQL por chave/título, replay anterior ao recálculo e API de liquidação. Nenhuma migration existente foi reescrita. Revisão independente somente leitura não identificou defeitos bloqueantes e reforçou a necessidade de testes reais de rollback e concorrência. As condições enviadas continuam não autenticadas; o backend calcula o pagamento. Não foram implementadas transferências reais ou autenticação, conforme o escopo aprovado.

73 testes sem banco passaram no ambiente da IA. A primeira tentativa de verify com 16 integrações foi bloqueada pela permissão do pipe Docker, antes do contexto iniciar. Naquele momento, os 6 testes do incremento anterior não validavam a liquidação nova. Não foram feitos commits: o repositório já continha arquivos não rastreados do usuário e a integração ainda aguardava verificação. Nenhum push foi realizado.

## Validação pelo candidato e atualização documental — 14/09/2026

O candidato executou `.\mvnw.cmd -B -ntp verify` no próprio terminal, com Docker acessível, e compartilhou o resultado: BUILD SUCCESS às 17:42:03 -03:00, 16 testes de integração sem falhas, erros ou testes ignorados. Na atualização documental, a IA conferiu `target/failsafe-reports/failsafe-summary.xml` e os relatórios Surefire: 16 integrações e 73 testes sem banco aprovados, total de 89. Os 16 já incluem os testes de integração antigos; os 6 anteriores não são somados novamente.

A execução valida os cenários automatizados de cadastro, simulação, liquidação, reconfirmação, snapshot, replay, rollback e concorrência. O Docker não foi reparado pela IA: a restrição era de acesso no ambiente da ferramenta. A execução bem-sucedida foi feita pelo candidato e a conferência documental, pela IA. A revisão humana do código e a validação futura da interface não são apresentadas como concluídas.

Prompt desta atualização: “seguinte, atualiza o plano de execucao, readme e o aiusage”. A skill documentation-and-adrs orientou a separação entre histórico e status atual. Foram atualizados somente esses três documentos, sem mudanças na aplicação nem nova execução de testes. Próximo incremento registrado: extrato com filtros por período, cedente e moeda, paginação e ordenação estável; depois painel mínimo. CSV, edição de pendentes, lote e recuperação no navegador permanecem pendentes.
