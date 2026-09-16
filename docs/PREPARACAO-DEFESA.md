# Preparação para a defesa: negociação de decisões

Material de apoio pessoal, não faz parte do pacote de avaliação e não é referenciado no README. Reconstituído a partir da sessão de planejamento no Codex (13/09/2026) que precedeu o `DECISIONS.md`, onde cada ambiguidade foi fechada apresentando alternativas com benefício, limitação e impeditivo antes da escolha. Serve para responder rápido, na banca, por que essa opção e não aquela.

Formato: decisão, alternativas descartadas e o motivo, o ponto que um avaliador pode pressionar.

## Ensaio de mudança ao vivo (a parte que mais pesa)

O enunciado (§9.2) cita como exemplos "um novo tipo de recebível, uma nova moeda, ou uma mudança na política de arredondamento" para a alteração ao vivo de 30 a 40 minutos. Travar aqui é eliminatório independente da qualidade do resto do repositório. As três rotas abaixo foram mapeadas direto no código atual (16/09), com arquivo e linha, para não precisar procurar durante a banca.

### 1. Novo tipo de recebível (o caso fácil, ensaiar para ganhar confiança)

1. `ReceivableType.java`: adicionar a constante do enum.
2. Criar `NovoTipoStrategy.java` implementando `PricingStrategy` (`type()` e `spread()`), seguindo `DuplicataStrategy`/`ChequeStrategy` como modelo.
3. `CreditService.java:23`: **não é injeção do Spring**, é uma lista estática `List.of(new DuplicataStrategy(), new ChequeStrategy())`. Esquecer de acrescentar a nova Strategy aqui é o erro mais provável de esquecer ao vivo.
4. Nova migration (`V2__...sql`, nunca editar a V1 já aplicada) alterando os dois `CHECK (type IN (...))`: `receivables.type` (V1 linha 28) e `settlements.type` (V1 linha 46).
5. Frontend: acrescentar `<option>` em `Registration.tsx:78` e `EditReceivable.tsx:44`.
6. Teste: novo caso na `@CsvSource` de `PricingEngineTest`, com valor calculado à mão (mesma fórmula, spread novo).

### 2. Nova moeda (o caso caro, o que realmente separa quem entende do que decorou)

Este é bem mais caro que o de tipo, porque moeda está espalhada em oito lugares, não um:

- `PaymentCurrency.java`: novo valor no enum.
- Três `CHECK` no schema, todos exigindo nova migration: `exchange_rates.currency_pair = 'USD/BRL'` (V1:17, é **igualdade fixa**, não uma lista, o mais restritivo dos três), `receivables.payment_currency IN ('BRL','USD')` (V1:31), `settlements.currency IN ('BRL','USD')` (V1:56).
- O `CHECK` composto de `settlements` (V1:63-66) só sabe tratar dois ramos, `currency = 'BRL'` ou `currency = 'USD'`; precisa virar uma regra geral tipo "BRL não tem câmbio, qualquer outra moeda tem".
- `PricingEngine.calculate()`: `if (currency == PaymentCurrency.USD)` é binário; precisa virar `if (currency != PaymentCurrency.BRL)` ou equivalente.
- `CreditService.java:94-97` e `:127`: o par `"USD/BRL"` está hardcoded como string literal em três pontos (busca da cotação vigente, mensagem de erro, validação de par suportado no cadastro de câmbio).
- `Contracts.java:34` e `CreditController.java:50`: o DTO de cotação valida `@Pattern(regexp = "USD/BRL")` e o endpoint de consulta tem esse valor como default do query param.
- Frontend: os dois `<select>` de moeda (`Registration.tsx:79`, `EditReceivable.tsx:45`) e, fácil de esquecer, `SettlementPanel.tsx:13`: `const sums: Record<string, bigint> = { BRL: 0n, USD: 0n }` — os totais do lote têm as moedas hardcoded também.

Se pedirem esse cenário, a resposta que demonstra domínio não é "eu mudo esses oito lugares": é explicar **por que** está espalhado assim (constraint de banco como fonte de verdade da integridade, não uma tabela de moedas dinâmica, porque o escopo júnior tem exatamente duas moedas) e então mudar metodicamente, migration primeiro, domínio depois, API depois, frontend por último, testando C1/C2/C3 continuam passando a cada etapa.

### 3. Mudança na política de arredondamento

Ponto mais barato de implementar, mais fácil de errar silenciosamente. Os dois lugares literais são `PricingEngine.java`: `face.divide(denominator, 2, RoundingMode.HALF_EVEN)` e `vp.divide(usedRate, 2, RoundingMode.HALF_EVEN)`. Se a escala mudar (2 → 4 casas, por exemplo), as colunas `NUMERIC(19,2)` do banco (dinheiro) também precisam de migration, e `DecimalRules.require(..., scale, ...)` passa a receber outro valor.

**A pegadinha real**: mudar a regra invalida os três golden cases. `92859.94`, `23337.77` e `17094.67` são os resultados da regra *atual*; com `HALF_UP` em vez de `HALF_EVEN`, ou outra escala, esses números mudam. Se pedirem essa mudança ao vivo, o exercício implícito é recalcular o C1 na mão (ou com uma REPL) e atualizar o teste com o valor correto, não só trocar o enum do `RoundingMode` e rodar `mvnw test` torcendo. Vale treinar esse cálculo manual antes da banca: `100000 / (1.025)^3`, arredondar pela nova regra, comparar com o que o código produzir.

- **Escolhida:** aniversário mensal a partir da data original, com ajuste para o último dia disponível, arredondando para cima.
- **Descartadas:** dias corridos dividido por 30 (meses reais têm durações diferentes, e 3 meses de calendário podem virar 4 períodos de cobrança); prazo fracionário por dias (exigiria potência fracionária, fora do escopo de `BigDecimal.pow(int)`); aceitar só vencimentos que caiam exatamente em meses inteiros (rejeitaria a maioria dos recebíveis reais).
- **Pressão esperada:** "um dia após o aniversário cobra um mês inteiro a mais; isso é aceitável comercialmente?" Resposta: é a limitação assumida da opção 1, documentada como premissa, não como acidente.

## Reconfirmação quando a simulação muda

- **Escolhida:** recalcular na confirmação; se prazo, taxa, cotação ou valores mudarem, devolver nova simulação sem liquidar.
- **Descartadas:** recalcular e concluir direto (operador pode ser surpreendido pelo valor); garantir a simulação por um período fixo (exige "congelar" uma proposta financeira, mais escopo e risco de negócio do que o case pede).
- **Pressão esperada:** "por que uma cotação nova com o mesmo valor não pede reconfirmação?" Resposta: a comparação é por valor econômico, não por identidade do registro; evita reconfirmações artificiais quando a cotação só foi renovada.

## Lote: granularidade de falha

- **Escolhida:** processamento sequencial; erro de negócio isola o título, falha técnica interrompe os seguintes.
- **Descartadas:** parar na primeira falha (desperdiça sucessos válidos); tentar todos mesmo com infraestrutura fora (gera tentativas inúteis sem controle de timeout).
- **Pressão esperada:** distinguir ao vivo um exemplo de erro de negócio (cotação expirada) de um técnico (banco indisponível) e mostrar onde isso é decidido no código.

## Validade da cotação cambial

- **Escolhida:** última cotação vigente, válida por uma janela configurável (24h corridas como valor inicial).
- **Descartadas:** sem expiração (cotação de sexta-feira valendo na segunda, sem controle); válida só no dia de negócio (trava toda operação sem quem atualize a cotação diariamente).
- **Pressão esperada:** "por que 24h e não outro valor?" É premissa nossa, não exigência do enunciado; está declarada como tal no SPEC.

## Entrada do lote

- **Escolhida:** CSV brasileiro (ponto e vírgula, vírgula decimal, `DD/MM/AAAA`), até 100 títulos, rejeição do arquivo inteiro acima do limite.
- **Descartadas:** cadastro manual linha a linha (não representa "receber um lote", como pede o enunciado); aceitar dois formatos de arquivo (duplica validação e teste sem necessidade clara).
- **Pressão esperada:** trocar o delimitador ou o formato de data ao vivo é mudança isolada no parser, não no motor.

## Duplicidade de título

- **Escolhida:** identificador único por cedente e código do título, informado no CSV.
- **Descartada:** comparar dados do título, como valor e vencimento; títulos legítimos podem coincidir nesses campos, então não é uma chave confiável.
- **Pressão esperada:** o que acontece se o mesmo código vier com dados diferentes (conflito, sem sobrescrever) contra o mesmo código com dados idênticos (duplicado, ignorado).

## Linhas inválidas no CSV

- **Escolhida:** mostrar todas as linhas com erro, confirmar explicitamente só as válidas.
- **Descartadas:** rejeitar o arquivo inteiro por qualquer erro (descarta trabalho válido); permitir edição inline na prévia (aumenta muito o escopo de frontend para o prazo).

## Retomada após fechar a tela de importação

- **Escolhida:** sem histórico de importação; retomar pelos recebíveis já cadastrados.
- **Descartada:** guardar histórico resumido por importação (útil, mas escopo extra não pedido pelo enunciado); guardar o CSV original (amplia ainda mais sem necessidade).
- **Pressão esperada:** é um corte documentado em `DECISIONS.md §10`, não uma omissão. Apontar isso rápido importa mais que a funcionalidade em si.

## Edição de pendentes

- **Escolhida:** editar valor, vencimento, tipo e moeda só em PENDING; cedente e código fixos; invalida simulação anterior.
- **Descartadas:** cancelar e recadastrar (introduz estado `CANCELLED` e problema de reuso de código, fora de escopo); não permitir edição alguma (obriga reenviar CSV para qualquer erro de digitação).

## Como o backend reconhece as condições confirmadas

- **Escolhida:** frontend envia os valores exibidos só como referência de comparação; backend sempre recalcula e decide.
- **Descartadas:** identificador de simulação persistida (contradiz a decisão de simular sem gravar); token assinado das condições (segurança adicional que não se justifica no escopo júnior e exige gestão de chave).
- **Pressão esperada:** "isso não autentica a origem da simulação no navegador". Correto, e está registrado como limitação; o ponto central é que o servidor sempre é a fonte da verdade do valor pago.

## Falha de rede após confirmar

- **Escolhida:** resultado incerto na tela com ação explícita "Consultar ou concluir tentativa", reenviando a mesma chave e o mesmo payload.
- **Descartadas:** retry automático (mascara falhas e complica limite de tentativas); endpoint de consulta separado sem tentar concluir (não resolve o caso em que a liquidação não ocorreu).
- **Pressão esperada:** explicar por que a mesma ação de concluir pode, na prática, executar a liquidação que nunca saiu. É intencional, não bug.

## Stack

Spring Boot com Spring Data JPA (motor isolado do framework), PostgreSQL desde o início com Docker Compose só para o banco, Flyway com Hibernate em modo `validate`, Testcontainers para integração, React com TypeScript e Vite, Material UI gratuito, Maven com Wrapper. Cada escolha foi comparada contra pelo menos uma alternativa (JDBC explícito, H2, Angular, Gradle, Liquibase). Se pedirem para justificar uma tendência contrária, a resposta padrão é que a alternativa não seria mais defensável para alguém com meu repertório, no prazo do case.
