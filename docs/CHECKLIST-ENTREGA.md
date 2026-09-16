# Checklist de entrega (16/09/2026)

## Evidências disponíveis

- [x] Motor Strategy e golden cases C1/C2/C3: testes de PricingEngine.
- [x] Datas, HALF_EVEN, precisão/limites: testes de domínio.
- [x] Idempotência, rollback, concorrência, edição e extrato: 98 testes sem banco + 22 PostgreSQL, verify do candidato em 16/09 às 04:37:42.
- [x] Frontend: 32 testes, TypeScript e build; API mockada nesses testes.
- [x] Playwright real: cadastro USD, liquidação, reconfirmação, resposta perdida/replay, CSV misto/reenvio, edição e lote. BRL também conferido pelo candidato.
- [x] REVIEW.md do Anexo A, por severidade/impacto/correção.
- [x] ER conferido com migration; contrato OpenAPI presente e referências locais testadas.
- [x] SPEC contém premissas, perguntas, precisão e critérios.

## Antes de enviar

- [ ] Candidato ler e defender REVIEW/SPEC/DECISIONS/AI_USAGE: documento gerado com IA não substitui compreensão.
- [x] Medir meta local de p95: 6,9136 ms, 100 requisições sequenciais BRL, após 10 aquecimentos. Ambiente/método/limitações em PERFORMANCE.md; não é carga concorrente.
- [x] Conferir manualmente os cenários do plano com dados fictícios: usuário confirmou cadastro, edição, CSV, lote, extrato e recarga funcionando em 16/09. Testes automatizados e validação manual permanecem evidências distintas.
- [ ] Validar setup do README em ambiente limpo/isolado, sem apagar banco existente. Primeiro download depende de rede.
- [x] Aferir paginação final do SPEC: 2 páginas em A4 retrato, Arial 11pt, margens padrão (renderização independente do GitHub, cujo preview de impressão apresentou bug ao trocar de orientação).
- [x] Revisar arquivos destinados ao Git e segredos antes de stage: sem `.env` rastreado ou em disco, sem chaves/tokens no histórico; credenciais no `application.properties` são só de demonstração local, já documentadas como tal.
- [x] Remoto privado configurado (`origin`, GitHub); `feat/project-setup` é a branch padrão do repositório.
- [ ] Conceder acesso aos avaliadores indicados no e-mail.
- [ ] Documentação restante (AI_USAGE, README, limpeza geral) seguir em branches curtas por assunto com PR e descrição do porquê, conforme o padrão pedido pelo case, sem reescrever a cronologia já publicada nem inventar sequência TDD retroativa.
- [ ] Confirmar senioridade da candidatura e justificar lacunas abaixo; cortes registrados não equivalem a atendimento integral da rubrica.
- [ ] Confirmar horário limite do e-mail (data informada: 18/09), ensaiar defesa e alteração ao vivo.

## Lacunas de rubrica, sem alegação de atendimento

O alvo desta candidatura é Fullstack Júnior; essas lacunas de pleno/sênior não bloqueiam o escopo escolhido. Para Júnior, permanecem bloqueadores: setup reproduzível, golden cases, testes, README/SPEC/REVIEW/AI_USAGE, validação do banco e defesa do próprio código. Compose orquestra PostgreSQL local; não há CI/linter, métricas, C4 ou integração cambial remota porque não são necessários para o nível informado. Isso deve ser apresentado como decisão de escopo, não como recurso existente.

## Roteiro de defesa (ensaio, não realizado)

1. Explicar taxa em fração, potência exata, HALF_EVEN e ordem BRL → USD com C1, C2 e C3.
2. Mostrar transação, UNIQUE e locks; diferenciar dupla liquidação de replay do mesmo resultado.
3. Demonstrar reconfirmação e recuperação sem criar chave nova em resultado incerto.
4. Explicar o snapshot e suas diferenças para consultar cadastros atuais.
5. Exercitar inclusão de novo tipo em enum/Strategy/constraint/contrato/interface e testes, sem alterar migration já aplicada.
6. Defender limitações: câmbio manual, ausência de autenticação, fila no navegador e último editor aceito prevalecendo.
