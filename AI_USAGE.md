# Uso de IA: engenharia da colaboração

Este documento não é um log de sessões. Ele responde três perguntas: como o trabalho foi especificado para a IA, onde a IA errou e como o erro foi detectado, e o que foi deliberadamente mantido fora da delegação.

## 1. Como especifiquei o trabalho

O enunciado tem ambiguidades propositais (prazo, taxa base, arredondamento, cotação vigente). Antes de gerar código, essas ambiguidades foram resolvidas em texto e entregues à IA como restrição, não como pergunta aberta:

- [DECISIONS.md](DECISIONS.md) registrou cada escolha (regra de aniversário mensal, ordem de arredondamento BRL→USD, expiração cambial de 24h, semântica de idempotência) antes de qualquer implementação.
- [REQUISITOS-v2.md](REQUISITOS-v2.md) traduziu essas decisões em requisitos com critério de aceite por item (R01 a R09), para que a IA tivesse um alvo verificável em vez de uma descrição solta.
- As skills em `.agents/skills/` (ponytail para evitar complexidade acidental, test-driven-development, api-and-interface-design) funcionaram como restrição permanente durante toda a implementação, não como prompt único.

Prompts típicos de incremento eram diretivos e curtos ("implemente liquidação com comparação de condições, idempotência e snapshot de auditoria, seguindo a seção 8 do DECISIONS"), não descrições vagas do problema. Geração de massa de dados (CSV de teste, cedentes fictícios) e scaffolding de componentes React seguiram o mesmo padrão: spec curta, execução, revisão do resultado contra os golden cases ou contra o contrato OpenAPI.

As ambiguidades mais difíceis foram fechadas antes do `DECISIONS.md` existir, numa sessão de planejamento em que eu pedia a IA para apresentar alternativas com benefício, limitação e impeditivo, e eu decidia entre elas. Exemplo real, sobre a relação entre vencimento e prazo em meses (a ambiguidade central da §3 do enunciado): a IA apresentou quatro opções (meses de calendário arredondando para cima; dias corridos dividido por 30; prazo fracionário por dias; aceitar só vencimentos exatos), cada uma com seu custo. Minha resposta foi "a primeira opção é a mais adequada, porém como vamos definir esse comportamento de datas como no exemplo?". Aceitei a direção, mas exigi a regra de borda (fim de mês, bissexto) antes de fechar. Só depois desse segundo round chegamos à regra de aniversário mensal com ajuste para o último dia disponível, que está em [DECISIONS.md §1](DECISIONS.md). O mesmo padrão de pergunta com alternativas e decisão explícita se repetiu para reconfirmação, validade cambial, formato do CSV, identificação de duplicidade e tratamento de falha em lote.

## 2. Onde a IA errou e como o processo detectou

**Arredondamento intermediário no cálculo financeiro.** A primeira versão da documentação afirmava que usar `MathContext` finito "não arredondaria intermediários". Isso é falso: `MathContext` limita a escala em cada operação da cadeia, não só no resultado. A revisão técnica do próprio texto pegou o erro antes de virar código; a implementação usa potência exata (sem `MathContext`) e aplica `HALF_EVEN` apenas na divisão final. Os golden cases C1, C2 e C3 travam esse comportamento: qualquer regressão de arredondamento intermediário quebra o teste.

**Idempotência excluída do escopo por leitura errada do enunciado.** Uma versão inicial do plano tratava idempotência como diferencial de nível sênior. Reler a seção 4.1.3 do case mostrou que é exigência explícita do core, para todos os níveis ("o endpoint de liquidação deve ser idempotente"). Corrigido no plano antes de implementar, e coberto depois por teste de concorrência e replay (`SettlementServiceTest`, `CreditEngineApplicationIT`).

**Instante com precisão além do suportado pelo banco.** O caso mais sutil: um `Instant` Java com resolução de nanossegundos, ao ser comparado com `valid_from` no PostgreSQL, podia ser arredondado pelo driver para um microssegundo futuro, fazendo a consulta de cotação vigente rejeitar uma cotação que, na verdade, já estava valendo. Não foi pego por golden case, já que eles não testam limites de precisão temporal; foi pego em revisão do código gerado, antes de qualquer teste falhar. Corrigido truncando a referência de tempo a microssegundos antes da consulta, com teste de regressão específico para esse limite.

**Teste com mock mascarando o próprio erro.** Um teste de conflito HTTP usava `when(...).thenReturn(...)` para reconfigurar um mock que já estava stubado para lançar exceção. O `when` acabava invocando o comportamento antigo e escondendo a falha real do serviço. Corrigido para `doThrow`, sem alterar o serviço para acomodar o teste; o instinto errado seria "consertar" o serviço até o teste malformado passar.

## 3. O que não deleguei

- **A regra de negócio em si**: aniversário mensal partindo da data original, rejeição de título vencido, ordem BRL→USD no arredondamento, expiração cambial no instante exato. Essas são decisões de domínio financeiro documentadas em [DECISIONS.md](DECISIONS.md) antes de pedir implementação: a IA implementa a regra escolhida; não a escolhe.
- **Semântica de idempotência e reconfirmação**: o que conta como "mesmo pedido", quando gerar nova chave, o que acontece em resposta perdida. Decidido em texto, com casos de borda explícitos, antes de qualquer código de concorrência.
- **Execução dos testes de integração contra banco real** e a validação manual ponta a ponta na interface: rodadas no meu terminal, não relatadas de segunda mão.
- **Os cortes de escopo** registrados em [DECISIONS.md §10](DECISIONS.md): o que ficou de fora (estorno, liquidação parcial, autenticação, CI, observabilidade) foi decisão minha diante do prazo e do nível da vaga, não sugestão aceita sem crítica.

## 4. Como validei o que a IA devolveu

Três camadas, nessa ordem: os golden cases C1, C2 e C3 travam o motor de cálculo ao centavo antes de qualquer outra validação fazer sentido; os testes de integração com Testcontainers (PostgreSQL descartável) validam transação, rollback e concorrência com injeção de falha real, não simulada; e o fluxo completo (cadastro, simulação, reconfirmação, CSV, lote, recuperação após falha de rede) foi exercitado contra a API real, parte via MCP Playwright e parte manualmente por mim, antes de qualquer funcionalidade ser considerada pronta. Nenhuma dessas três camadas substitui as outras: um teste automatizado passando não prova, por si, que o fluxo funciona na interface real, e vice-versa.
