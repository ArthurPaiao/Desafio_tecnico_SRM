# Revisão de liquidação individual

Escopo: ExpectedConditions, SettlementService, entidade/repositórios, API e testes. Não substitui REVIEW.md do Anexo A.

## Correção e concorrência

Revisão independente somente leitura não identificou defeitos bloqueantes. Foram examinados lock da chave antes do replay, fingerprint com valores normalizados, bloqueio NOWAIT do título, uso exclusivo do cálculo do servidor e snapshot persistido na mesma transação da mudança SETTLED. UNIQUE por chave e título preservados. Replay não depende de cotação/configuração atual. Parâmetros SQL são vinculados; erros não expõem SQL.

## Limites da evidência

73 testes sem banco aprovados; 16 integrações compiladas/tentadas, mas bloqueadas por acesso negado ao Docker neste ambiente. A aprovação dos 6 testes anteriores pelo usuário não valida as novas transações. A revisão não autoriza declarar o incremento pronto para merge. Ainda é necessário passar os testes PostgreSQL de rollback após insert, concorrência na mesma chave e entre chaves distintas, expiração, reconfirmação e integridade do snapshot.

Sem novas dependências, filas, tabelas de simulação ou pagamento externo. Advisory lock usa identificador de 64 bits; uma colisão gera contenção conservadora, não compartilhamento de fingerprint/snapshot. Lock de título deve ser respeitado na futura edição de pendentes. O extrato filtrado e a interface permanecem fora deste incremento.

## Ponytail

Lean already. Ship.

Essa conclusão é somente sobre complexidade; não representa aprovação transacional.
