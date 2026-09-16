# SRM Credit Engine — Decisões aprovadas

Atualizado em 14/09/2026. Prazo informado pelo candidato: 18/09/2026, horário ainda não confirmado. Este documento registra as escolhas feitas na conversa e substitui decisões conflitantes do consolidado anterior. As decisões são de planejamento; não representam funcionalidades já implementadas.

## 1. Prazo por vencimento

O operador informa vencimento, não um prazo independente. O backend calcula o menor inteiro n >= 0 tal que `dataReferencia.plusMonths(n) >= vencimento`. Cada aniversário parte da data original. Quando o dia não existir, usa o último dia disponível. Exemplos: 31/01/2027 → 28/02/2027 equivale a um mês; → 30/03/2027 equivale a dois. Em ano bissexto, 31/01 → 29/02 equivale a um mês. Vence hoje: zero. Vencido: rejeitar.

Data de referência: data de negócio da simulação ou liquidação em `America/Sao_Paulo`. Instantes persistidos em UTC. Recalcular o prazo na liquidação e registrar data de referência e prazo efetivos. Limite inicial de 120 meses permanece uma premissa do documento anterior. Não somar meses sucessivamente a datas já ajustadas e não dividir dias por 30.

Benefício: aceita datas variadas com motor de meses inteiros. Limitação aceita: um dia após um aniversário pode acrescentar um mês de desconto. Golden cases testam o motor diretamente com meses explícitos, e datas são testadas separadamente.

## 2. Condições e reconfirmação

Recalcular no backend antes de liquidar. Comparar dados do título, prazo, taxa efetiva, valor da cotação e valores monetários com as condições exibidas enviadas pelo frontend. Divergência impede a liquidação e devolve nova simulação. Alteração apenas do ID de cotação de mesmo valor não exige reconfirmação, desde que a cotação esteja válida. Registrar a cotação efetivamente usada.

O cliente envia `receivableId` e `expectedConditions`; valores recebidos são apenas referência de comparação. O servidor determina o pagamento. Não persistir simulações nem usar tokens assinados. Essa escolha não comprova a origem das condições enviadas pelo navegador.

Na etapa 1, reconfirmação individual. Na etapa 2, mostrar antes/depois por título, sem seleção automática, com ação para reconfirmar somente os selecionados. Totais separados por moeda. A nova tentativa continua sujeita à conferência das condições atuais.

## 3. Câmbio com expiração

Selecionar a última cotação com vigência iniciada. Validade configurável, inicialmente 24 horas corridas desde a vigência, inclusive fins de semana. Intervalo aceito: `validFrom <= instante < validFrom + validade`. No instante exato de expiração, bloquear USD. Não recorrer a uma cotação mais antiga para contornar a expiração. BRL dispensa cotação.

Cotação positiva em BRL por USD. Converter o VP em BRL já arredondado por divisão e arredondar o USD com HALF_EVEN em duas casas. Manter cadastros cambiais por inserção e impedir par/vigência duplicados.

## 4. CSV e identificação

Entrada do lote por CSV UTF-8, delimitador ponto e vírgula, valor com vírgula decimal sem separador de milhar e data `DD/MM/AAAA`. Cabeçalhos: `cedente_codigo;titulo_codigo;tipo;valor_face;vencimento;moeda_pagamento`. Usar parser CSV, sem separação ingênua por delimitador. Modelo de arquivo integra a entrega da etapa 2.

Até 100 linhas de títulos por arquivo, limite configurável. Cabeçalho não conta; linhas inválidas contam. Acima do limite: rejeitar o arquivo inteiro, sem truncar. Cabeçalhos obrigatórios ausentes ou arquivo ilegível também bloqueiam o arquivo inteiro.

Cedente identificado por código interno, como `CED-001`, disponível na tela e em seed fictício. Código desconhecido invalida a linha. A combinação de cedente e código de título é única no banco. Reenvio não cria outro título: sinalizar duplicado; se os dados divergem, conflito sem sobrescrita. Detectar também repetições dentro do próprio arquivo antes de confirmar o cadastro.

## 5. Importação e persistência

Mostrar todas as linhas e seus erros. Permitir continuar apenas com válidas mediante confirmação explícita. Corrigir linhas inválidas fora do sistema e reenviar depois. Nenhuma liquidação automática na importação.

Primeiro confirmar cadastro das linhas válidas como PENDING; depois simular e liquidar os selecionados. Revalidar duplicidade na confirmação. Erros por linha devem ser visíveis. Não presumir que a prévia garante cadastro posterior. Os títulos que falharem na liquidação continuam pendentes.

Sem histórico de importação e sem guardar CSV original. Depois de fechar a prévia, consultar somente os recebíveis cadastrados. Erros das linhas não cadastradas exigem reenviar o arquivo. Essa limitação foi escolhida para conter o escopo.

## 6. Edição de pendentes

Permitir editar valor, vencimento, tipo e moeda de recebíveis PENDING. Cedente e código do título permanecem fixos. Edição invalida a simulação anterior e exige novo cálculo. SETTLED não admite edição. Backend deve coordenar edição e liquidação para impedir uso de um cadastro alterado durante o processamento. Não é necessário adotar o mecanismo específico de optimistic locking exigido para nível sênior.

## 7. Lote com sucesso parcial

Processar sequencialmente e com uma transação por título. Continuar após erro de negócio ou necessidade de reconfirmação. Em falha de infraestrutura, interromper novas tentativas, preservar sucessos anteriores e sinalizar itens restantes como não processados.

Resultados de operação: liquidado, aguardando reconfirmação, erro de negócio, não processado, resultado a confirmar. São resultados de apresentação/processamento; não transformar todos em estados persistentes do recebível. O recebível continua PENDING ou SETTLED.

## 8. Atomicidade, idempotência e recuperação

Registrar liquidação, chave e mudança de estado em uma transação. Unicidade por título e chave, com atualização condicional ou bloqueio apropriado. Repetição da mesma chave/pedido concluído retorna a liquidação existente antes de recalcular ou validar expiração. Outra chave para título liquidado ou mesma chave com payload diferente: conflito.

Após falha de rede, mostrar resultado incerto e ação “Consultar ou concluir tentativa”, que repete o pedido com a mesma chave. Explicar que pode concluir a liquidação se ela ainda não ocorreu. Não tentar novamente automaticamente.

Persistir no navegador somente tentativas sem resultado confirmado: identificador, chave e payload exato necessário à repetição. Ao esclarecer o resultado, remover a tentativa local. Isso funciona no mesmo navegador/perfil, não oferece recuperação entre computadores e não substitui a validação do servidor.

Detalhe de implementação proposto: depois de resposta definitiva de condições alteradas, a confirmação das novas condições cria uma nova intenção/chave. Em resultado incerto, nunca trocar a chave nem alterar o payload. Manter o payload da tentativa em curso congelado mesmo que os campos da tela mudem. Após recarregar, não reenviar automaticamente.

## 9. Stack e testes

- Java, Spring Boot, Spring Data JPA; motor independente da persistência.
- Maven com Wrapper. PostgreSQL desde o início.
- Docker Compose somente para PostgreSQL; backend/frontend executados diretamente.
- Flyway com SQL versionado; Hibernate valida a estrutura, sem atualização automática.
- React, TypeScript, Vite e componentes gratuitos do Material UI, sem grid avançada.
- Testes unitários do motor e PostgreSQL temporário via Testcontainers nos testes de integração.
- Componentes essenciais testados automaticamente; roteiro manual para integração ponta a ponta. Não incluir suíte E2E automatizada nesta entrega.
- Simulação após 400 ms sem alteração com campos válidos; ignorar respostas antigas e desabilitar confirmação com resultado desatualizado.

Atualização de execução em 16/09: versões e builds verificados durante os incrementos, conforme README/AI_USAGE; a observação inicial de scaffold não executado foi superada. O escopo operacional continua simplificado: não afirmar cumprimento integral das rubricas pleno/sênior enquanto os itens excluídos permanecerem fora. Compose somente do banco e lock pessimista não equivalem aos requisitos literais de Compose aplicação+banco e optimistic locking.

## 10. Duas etapas e cortes

Etapa 1: cadastro individual, cálculo, câmbio, reconfirmação individual, liquidação segura, extrato, OpenAPI e painel mínimo.

Etapa 2: CSV, cadastro confirmado de válidos, edição de pendentes, seleção e reconfirmação coletiva, lote parcial e recuperação de tentativas no navegador.

Fora: histórico de importação, armazenamento de CSV, cancelamento/substituição, estorno, liquidação parcial, integração de pagamento real, autenticação da demonstração, filas, E2E automatizado amplo, containerização das aplicações, CI/observabilidade exigidos em níveis superiores. CSV e lote são decisões aprovadas, não itens opcionais silenciosamente removíveis. Se o prazo exigir corte, registrar e discutir antes de alterar o compromisso.

## 11. Entrega e autoria

SPEC curto, requisitos, DECISIONS, README executável, ER, OpenAPI, REVIEW do Anexo A em severidade/impacto/correção e AI_USAGE com prompts estratégicos, erro real detectado e decisões não delegadas. Repositório privado, acesso dos avaliadores, branches por funcionalidade e commits explicáveis. Defesa obrigatória.

Esta conversa já contém evidência real para AI_USAGE: a afirmação inicial de cálculo “sem arredondar” com MathContext finito foi corrigida por revisão técnica; requisitos inicialmente excluíam idempotência, mas a leitura do core mostrou sua exigência. Não apresentar essa revisão documental como teste do sistema.
