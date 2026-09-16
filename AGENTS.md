# Trabalho neste repositório

Este é o diretório principal do SRM Credit Engine. Não implementar em cópias paralelas em Documents/Codex.

Antes de alterar código, ler `.agents/skills/ponytail/SKILL.md`. Para a revisão de complexidade, usar `.agents/skills/ponytail-review/SKILL.md`. Requisitos explícitos do case, validação financeira e os testes aprovados prevalecem sobre sugestões de simplificação.

Referências vigentes: SPEC.md, DECISIONS.md, REQUISITOS-v2.md e PLANO-EXECUCAO.md. Não substituir decisões aprovadas por heurísticas da skill.

Trabalhar na branch de funcionalidade existente e preservar alterações locais. Testes unitários: Maven Wrapper test. Integração: verify, com PostgreSQL via Testcontainers. Uma falha de acesso ao Docker não significa que migrations ou transações foram validadas.

Atualizar AI_USAGE com intervenções reais. `REVIEW.md` é reservado ao review do Anexo A do desafio; revisões do próprio projeto ficam em docs/reviews.
