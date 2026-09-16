# Preparação para a defesa — negociação de decisões

Material de apoio pessoal, não faz parte do pacote de avaliação e não é referenciado no README. Reconstituído a partir da sessão de planejamento no Codex (13/09/2026) que precedeu o `DECISIONS.md`, onde cada ambiguidade foi fechada apresentando alternativas com benefício/limitação/impeditivo antes da escolha. Serve para responder rápido, na banca, "por que essa opção e não aquela".

Formato: decisão → alternativas descartadas e o motivo → o ponto que um avaliador pode pressionar.

## Prazo por vencimento

- **Escolhida:** aniversário mensal a partir da data original, com ajuste para o último dia disponível, arredondando para cima.
- **Descartadas:** dias corridos ÷ 30 (meses reais têm durações diferentes — 3 meses de calendário podem virar 4 períodos de cobrança); prazo fracionário por dias (exigiria potência fracionária, fora do escopo de `BigDecimal.pow(int)`); aceitar só vencimentos que caiam exatamente em meses inteiros (rejeitaria a maioria dos recebíveis reais).
- **Pressão esperada:** "um dia após o aniversário cobra um mês inteiro a mais — isso é aceitável comercialmente?" Resposta: é a limitação assumida da opção 1, documentada como premissa, não como acidente.

## Reconfirmação quando a simulação muda

- **Escolhida:** recalcular na confirmação; se prazo, taxa, cotação ou valores mudarem, devolver nova simulação sem liquidar.
- **Descartadas:** recalcular e concluir direto (operador pode ser surpreendido pelo valor); garantir a simulação por um período fixo (exige "congelar" uma proposta financeira — mais escopo e risco de negócio do que o case pede).
- **Pressão esperada:** "por que uma cotação nova com o mesmo valor não pede reconfirmação?" Resposta: a comparação é por valor econômico, não por identidade do registro; evita reconfirmações artificiais quando a cotação só foi renovada.

## Lote: granularidade de falha

- **Escolhida:** processamento sequencial; erro de negócio isola o título, falha técnica interrompe os seguintes.
- **Descartadas:** parar na primeira falha (desperdiça sucessos válidos); tentar todos mesmo com infraestrutura fora (gera tentativas inúteis sem controle de timeout).
- **Pressão esperada:** distinguir ao vivo um exemplo de erro de negócio (cotação expirada) de um técnico (banco indisponível) e mostrar onde isso é decidido no código.

## Validade da cotação cambial

- **Escolhida:** última cotação vigente, válida por uma janela configurável (24h corridas como valor inicial).
- **Descartadas:** sem expiração (cotação de sexta-feira valendo na segunda, sem controle); válida só no dia de negócio (trava toda operação sem quem atualize a cotação diariamente).
- **Pressão esperada:** "por que 24h e não outro valor?" É premissa nossa, não exigência do enunciado — está declarada como tal no SPEC.

## Entrada do lote

- **Escolhida:** CSV brasileiro (`;`, vírgula decimal, `DD/MM/AAAA`), até 100 títulos, rejeição do arquivo inteiro acima do limite.
- **Descartadas:** cadastro manual linha a linha (não representa "receber um lote", como pede o enunciado); aceitar dois formatos de arquivo (duplica validação e teste sem necessidade clara).
- **Pressão esperada:** trocar o delimitador ou o formato de data ao vivo — é mudança isolada no parser, não no motor.

## Duplicidade de título

- **Escolhida:** identificador único por `cedente + código do título`, informado no CSV.
- **Descartada:** comparar dados do título (valor/vencimento) — títulos legítimos podem coincidir nesses campos; não é uma chave confiável.
- **Pressão esperada:** o que acontece se o mesmo código vier com dados diferentes (conflito, sem sobrescrever) vs. idêntico (duplicado, ignorado).

## Linhas inválidas no CSV

- **Escolhida:** mostrar todas as linhas com erro, confirmar explicitamente só as válidas.
- **Descartadas:** rejeitar o arquivo inteiro por qualquer erro (descarta trabalho válido); permitir edição inline na prévia (aumenta muito o escopo de frontend para o prazo).

## Retomada após fechar a tela de importação

- **Escolhida:** sem histórico de importação — retomar pelos recebíveis já cadastrados.
- **Descartada:** guardar histórico resumido por importação (útil, mas escopo extra não pedido pelo enunciado); guardar o CSV original (amplia ainda mais sem necessidade).
- **Pressão esperada:** é um corte documentado em `DECISIONS.md §10`, não uma omissão — souber apontar isso rápido importa mais que a funcionalidade em si.

## Edição de pendentes

- **Escolhida:** editar valor/vencimento/tipo/moeda só em PENDING; cedente e código fixos; invalida simulação anterior.
- **Descartadas:** cancelar e recadastrar (introduz estado `CANCELLED` e problema de reuso de código, fora de escopo); não permitir edição alguma (obriga reenviar CSV para qualquer erro de digitação).

## Como o backend reconhece as condições confirmadas

- **Escolhida:** frontend envia os valores exibidos só como referência de comparação; backend sempre recalcula e decide.
- **Descartadas:** identificador de simulação persistida (contradiz a decisão de simular sem gravar); token assinado das condições (segurança adicional que não se justifica no escopo júnior e exige gestão de chave).
- **Pressão esperada:** "isso não autentica a origem da simulação no navegador" — correto, e está registrado como limitação; o ponto central é que o servidor sempre é a fonte da verdade do valor pago.

## Falha de rede após confirmar

- **Escolhida:** resultado incerto na tela + ação explícita "Consultar ou concluir tentativa", reenviando a mesma chave/payload.
- **Descartadas:** retry automático (mascara falhas e complica limite de tentativas); endpoint de consulta separado sem tentar concluir (não resolve o caso em que a liquidação não ocorreu).
- **Pressão esperada:** explicar por que a mesma ação "concluir" pode, na prática, executar a liquidação que nunca saiu — é intencional, não bug.

## Stack

Spring Boot + Spring Data JPA (motor isolado do framework) · PostgreSQL desde o início, com Docker Compose só para o banco · Flyway com Hibernate em modo `validate` · Testcontainers para integração · React + TypeScript + Vite · Material UI gratuito · Maven com Wrapper. Cada escolha foi feita comparando contra pelo menos uma alternativa (JDBC explícito, H2, Angular, Gradle, Liquibase) — se pedirem para justificar uma trend contrária, a resposta padrão é: a alternativa não seria mais defensável para alguém com meu repertório, no prazo do case.
