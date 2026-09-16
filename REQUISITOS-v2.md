# SRM Credit Engine — Requisitos vigentes

Esta versão substitui o REQUISITOS-consolidado.md anterior. Escopo júnior em duas etapas, com decisões aprovadas detalhadas em [DECISIONS.md](DECISIONS.md), premissas resumidas em [SPEC.md](SPEC.md) e tarefas em [PLANO-EXECUCAO.md](PLANO-EXECUCAO.md). O enunciado continua sendo a fonte das exigências externas; recursos adicionais não são apresentados como exigências literais da SRM.

## Etapa 1 — Core individual

### R01 — Cadastro e prazo

Cadastrar recebível com cedente existente, código do título, tipo, valor de face positivo em BRL, vencimento e moeda BRL/USD. Código único por cedente. Código interno do cedente é único. Prazo deriva da data conforme SPEC, não é entrada independente. Calcular novamente na liquidação e gravar o prazo e data de referência no evento.

Aceite: rejeitar tipo/moeda desconhecidos, vencido, valor inválido, excesso de escala e prazo acima do limite; prazo zero e aniversários de fim de mês funcionam. Cadastro válido inicia PENDING. Fixture de cedentes disponível na tela.

### R02 — Precificação e câmbio

Strategies fornecem spreads 0.015 e 0.025. Taxa efetiva = base persistida + spread. BigDecimal, potência exata e divisão com HALF_EVEN em escala 2. Câmbio vigente mais recente, positivo, com validade inicial de 24 horas desde a vigência. Deságio sempre em BRL. Converter o VP BRL arredondado para USD por divisão.

Aceite: golden cases C1 92859.94, C2 23337.77, C3 17094.67; deságios 7140.06, 1662.23 e 7140.06. Cotação futura ignorada, expiração no limite bloqueia USD, BRL independe de cotação. Não aceitar par/vigência duplicados.

### R03 — Simulação e reconfirmação

Simulação sem persistência, após 400 ms de pausa, somente com entradas válidas. Exibir moedas e condições financeiras. Confirmar somente com resultado atualizado. Backend recebe condições exibidas, recalcula e compara. Se mudaram, retorna nova simulação sem liquidar. ID cambial diferente com mesmo valor válido não exige confirmação.

Aceite: respostas fora de ordem não substituem resultado atual; alterações de prazo, taxa, câmbio ou valores exigem nova confirmação; parâmetros enviados pelo cliente nunca determinam o cálculo final.

### R04 — Liquidação e auditoria

Uma liquidação integral por recebível. ACID para evento, estado e idempotência; restrições de unicidade e coordenação da mudança de estado no banco. Repetição de chave/pedido concluído retorna registro original antes de recálculo. Chave reaproveitada com payload diferente e outro pedido para SETTLED geram conflito.

Snapshot: cedente, título, tipo, valor de face, vencimento, data de referência, prazo, base, spread, taxa efetiva, VP/deságio BRL, moeda/valor final, cotação ID/valor/vigência se USD e timestamp. Não expor edição/exclusão do evento.

Aceite: uma falha entre insert e atualização deixa zero efeito parcial; retry gera apenas um registro; cadastro já liquidado não recebe segundo pagamento.

### R05 — Extrato, API e painel

Extrato filtra por período de liquidação, cedente e moeda. Intervalo inicial inclusivo/final exclusivo; ordenação estável e paginação básica limitada. Padrão proposto: 20, máximo 100, página inicial zero. OpenAPI obrigatório. Erros 400/404/409/422/500 com semântica e mensagens claras; sem stacktrace ou SQL.

Painel permite cadastrar, simular, confirmar e consultar. Decimais em strings no contrato; cálculos financeiros no backend. Separar apresentação/estado, aplicação, domínio e persistência.

## Etapa 2 — CSV e operações coletivas

### R06 — Importação

CSV UTF-8 com cabeçalho `cedente_codigo;titulo_codigo;tipo;valor_face;vencimento;moeda_pagamento`, decimal brasileiro sem milhar e data DD/MM/AAAA. Até 100 linhas de títulos incluindo inválidas. Acima do limite ou arquivo estruturalmente ilegível: rejeitar inteiro. Parser deve tratar corretamente campos CSV delimitados por aspas.

Aceite: mostrar número da linha, dados e erro; permitir confirmação explícita das válidas; duplicados/conflitos no banco e no arquivo não são sobrescritos; cabeçalho não conta no limite. Cadastrar PENDING sem liquidar, com revalidação no backend. Não salvar CSV nem histórico de importação. Após sair da prévia, retomada pelos títulos cadastrados.

### R07 — Edição

Editar somente PENDING: valor, vencimento, tipo e moeda. Cedente/código fixos. Invalidar simulação e coordenar com liquidação no servidor.

Aceite: SETTLED não pode ser editado, e uma edição não pode fazer o sistema liquidar com dados desatualizados.

### R08 — Lote e reconfirmação coletiva

Processamento síncrono sequencial com resultado e transação por título. Continuar em erros de negócio; parar novas tentativas em falha técnica. Preservar sucessos e distinguir não processado de resultado incerto.

Aceite: erro no terceiro item não impede o quarto quando for de negócio; indisponibilidade técnica interrompe os seguintes. Títulos alterados mostram antes/depois, nenhuma seleção automática, e reconfirmação somente dos selecionados. Já liquidados não entram novamente. Totais separados por moeda.

### R09 — Recuperação

Em rede incerta, ação “Consultar ou concluir tentativa” repete chave e payload exatos, sem retry automático. Persistir tentativas incertas localmente para sobreviver a recarga/fechamento, e remover quando resolvidas. Explicar que a ação pode concluir uma operação ainda não realizada.

Aceite: recarregar não gera nova chave nem envio automático; resultado já confirmado é recuperado sem recálculo; condições alteradas voltam para revisão. Limpeza de dados do navegador perde a referência local; consulta aos recebíveis continua disponível.

## Contratos e modelo — diretrizes atualizadas

Rotas de base: GET assignors; POST/GET exchange-rates; POST simulations; POST/GET receivables; POST/GET settlements. Acrescentar edição de pendente e validação/confirmação CSV na etapa 2. Nomes exatos dessas rotas adicionais serão fechados no OpenAPI da implementação.

Cadastro não recebe termMonths. Liquidação recebe receivableId e expectedConditions, com Idempotency-Key no header. Retornar 409 CONDITIONS_CHANGED e nova simulação quando houver divergência. Replay concluído retorna 200; criação, 201. Definir IDs completos nos exemplos e JSON válido.

Entidades: assignors (código único), pricing_config (base, limite de meses, validade cambial), receivables (FK cedente, código do título, dados e estado, UNIQUE cedente/código), exchange_rates (par/vigência únicos), settlements (recebível e chave únicos, snapshot). Datas DATE, instantes UTC; escalas conforme SPEC. Sem tabela de importações ou simulações. Constraints obrigatórias e migrations Flyway; Hibernate validate.

## Verificação e entrega

Testes unitários do motor/datas/parser; integração PostgreSQL/Testcontainers para persistência e transações; componentes essenciais e checklist manual do plano. Não considerar recalcular golden cases em um script como execução dos testes da aplicação.

Spring Boot/JPA/Maven Wrapper, PostgreSQL em Compose, React/TypeScript/Vite/Material UI gratuito. Versões compatíveis serão verificadas antes do setup. README, SPEC de 1–2 páginas, DECISIONS, ER e OpenAPI. REVIEW deve analisar o Anexo A por severidade, impacto e correção. AI_USAGE deve incluir prompts estratégicos, erro real/processo de detecção e o que não foi delegado. Histórico Git e defesa explicáveis. Prazo: 18/09, horário a confirmar no e-mail.
