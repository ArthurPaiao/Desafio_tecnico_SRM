# Review do Anexo A — endpoint de liquidação

Escopo: trecho TypeScript do Anexo A do enunciado AI-native v2, não o código Java deste repositório. Parecer: bloquear o merge. A ordem abaixo considera exposição de dados e risco de registro financeiro incorreto/duplicado. O trecho não mostra toda a infraestrutura; controles externos não podem ser presumidos presentes nem ausentes.

## 1. Crítico — SQL construído com entrada do cliente

Local: interpolação de `receivableId` no SELECT/UPDATE e de `currency` no INSERT.

Impacto: manipulação das consultas e possível acesso/alteração indevidos, conforme driver e privilégios da conexão. Validar apenas o formato não substitui parametrização.

Correção: parâmetros vinculados em todas as consultas; validar UUID/identificador e moeda por lista permitida. Usuário do banco com privilégios mínimos. Testar entradas maliciosas como dados, não como SQL executável. Se houver autenticação/autorização em middleware, verificar também se esse operador pode liquidar esse recebível; isso não é demonstrado no trecho.

## 2. Crítico — gravações não atômicas e sucesso falso

Local: INSERT seguido de UPDATE, sem transação explícita, com catch vazio.

Impacto: o INSERT pode persistir e o título continuar pendente; se o INSERT falhar, ainda é retornado `ok: true`. O operador não consegue distinguir sucesso, falha e estado parcial. Erros anteriores ao try (consulta/câmbio) também ficam fora desse tratamento local.

Correção: INSERT e mudança de estado em uma transação na mesma conexão, com rollback integral em falha. Nunca engolir exceção nem devolver sucesso antes do commit. Handler global com erro estruturado e log correlacionável, sem expor SQL/dados internos. Injetar falha entre as duas gravações e comprovar ausência de liquidação e permanência em PENDING.

## 3. Crítico — duplicação por retry ou concorrência

Local: leitura sem bloqueio, sem verificar PENDING; INSERT sem protocolo de idempotência visível.

Impacto: duplo clique, resposta perdida e duas requisições simultâneas podem registrar mais de uma liquidação. Uma transação isoladamente não impede duas transações de ler o mesmo título pendente. O trecho registra valores, mas não mostra transferência bancária; pagamento externo duplicado é um risco de integração, não um fato provado pelo anexo.

Correção: chave idempotente vinculada ao pedido normalizado, unicidade da chave e do recebível liquidado no banco e coordenação concorrente do título (lock/versionamento com conflito tratado). Registrar chave, snapshot e SETTLED atomicamente. Mesma chave/pedido concluído retorna resultado anterior antes de consultar novo câmbio; chave reutilizada com pedido diferente retorna conflito. Testar concorrência por mesma chave e por chaves diferentes, replay após perda de resposta e mesma chave com payload alterado. Não usar retry cego com nova chave.

## 4. Crítico — percentuais tratados como números inteiros

Local: `BASE_RATE = 1.0`, spreads `1.5`/`2.5` no denominador.

Impacto: 1% deveria entrar como 0.01, e 1,5% como 0.015. Para C1, o código usa `(1 + 1 + 1.5)^3`, não `(1 + 0.01 + 0.015)^3`; o VP é drasticamente subestimado. O erro é de unidade, não apenas de arredondamento.

Correção: representar taxas como frações decimais exatas e documentar unidade mensal; parametrizar a taxa base conforme premissa de negócio. Testar C1 = 92859.94, C2 = 23337.77 e C3 = 17094.67, incluindo deságios. Não alterar os valores esperados para acomodar o código.

## 5. Alto — precisão e ordem de arredondamento incorretas

Local: Number/Math.pow e `toFixed(2)`; conversão antes do arredondamento do VP BRL.

Impacto: ponto flutuante binário não preserva decimais monetários e `toFixed` não implementa o contrato decimal HALF_EVEN exigido. Arredondar apenas o pagamento USD diverge da regra que primeiro arredonda o VP BRL. Corrigir os percentuais não corrige esses problemas.

Correção: biblioteca decimal no TypeScript ou BigDecimal em Java, construída de strings; NUMERIC no banco e strings na API. Soma/potência exatas dentro de limites de prazo, divisão final em escala 2/HALF_EVEN; depois conversão do VP arredondado e novo HALF_EVEN. Validar overflow/escala e evitar MathContext que arredonde intermediários. Testar empates, grandes valores e casos que distinguem as duas ordens de conversão.

## 6. Alto — contrato cambial e consentimento indefinidos

Local: `fxService.getLatestRate("USD")` sem par, instante, validade ou verificação de retorno explícitos.

Impacto: taxa futura/expirada, orientação invertida ou valor zero/inválido pode gerar pagamento incorreto ou falha. Cotação que muda entre simulação e liquidação pode surpreender o operador. A consulta externa também não tem timeout/tratamento de indisponibilidade demonstrados.

Correção: contrato BRL por USD, positivo e decimal, com vigência e política de validade; escolher taxa válida no instante definido pelo negócio e gravar a efetivamente usada. Bloquear USD sem taxa válida, sem fallback silencioso. Comparar condições atuais com as revisadas e exigir reconfirmação quando divergirem. Timeout e retries limitados apenas na leitura cambial, sem repetir liquidação cegamente; não manter transação aberta esperando rede externa. Testar futura, vencida, zero, indisponibilidade, mudança e replay de operação já concluída.

## 7. Alto — entrada e tipos aceitos implicitamente

Local: acesso a `receivable.type` sem verificar existência; ternário trata qualquer tipo não DUPLICATA como cheque; toda moeda não USD segue como pagamento doméstico.

Impacto: título inexistente causa exceção; tipos e moedas desconhecidos passam por regras erradas. Prazo negativo, unidade ambígua ou face inválida podem produzir valores fora do domínio financeiro. O cliente pode pedir uma moeda diferente sem regra explícita de autorização/comparação.

Correção: validar existência, estado, enums, face positiva, escala, prazo inteiro limitado e unidade documentada. Se a interface usa vencimento, derivar prazo com política de calendário explícita. Strategy por tipo conhecido, erro para tipo não suportado. Definir moeda no contrato do título/intenção e comparar no servidor. Testar 400 para entrada inválida, 404 para inexistência e 409 para estado incompatível.

## 8. Alto — auditoria insuficiente

Local: INSERT registra apenas recebível, montante e moeda.

Impacto: não é possível reconstruir de forma confiável por que aquele valor foi pago se título, taxa base ou câmbio mudarem. Consultar valores atuais não reproduz necessariamente o passado. O trecho não demonstra imutabilidade do registro.

Correção: snapshot com face, tipo, prazo/data de referência, vencimento, base/spread, VP/deságio, moeda, valor final, cotação/identificador/vigência e instante de liquidação; chave e fingerprint do pedido para replay. Sem rota de edição de liquidação; permissões de banco adequadas em produção. Testar que alteração posterior de configurações não muda extrato/replay. Imutabilidade na API não equivale a proteção contra administrador do banco.

## 9. Médio — resposta não distingue criação de repetição

Local: resposta final sempre 200 e sem identificação do registro.

Impacto: o cliente não recebe referência auditável da operação criada e não consegue correlacionar o resultado com o extrato. O problema de sucesso falso é crítico e está no item 2; HTTP 200 por si não causa duplicidade.

Correção: 201 + Location e snapshot para criação, 200 para replay concluído; erros semânticos estruturados. Logar identificadores/correlação e métricas de sucesso, conflito e falha, sem payload financeiro completo por padrão. Testar status e referência retornada, não apenas valor numérico.

## Ordem de correção e prevenção

Antes de liberar: parametrização, transação/erros, protocolo idempotente e fórmula decimal; depois validar câmbio, domínio e snapshot como parte do mesmo fluxo seguro. Golden cases, teste de rollback e concorrência precisam bloquear regressões. Na defesa, demonstrar que uma resposta perdida não prova rollback e que uma constraint isolada não define a semântica de replay. Este review propõe correções para o anexo; não afirma que todos os controles operacionais propostos estão implementados no case.
