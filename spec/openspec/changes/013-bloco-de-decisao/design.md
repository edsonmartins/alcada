# Design — 013 bloco de decisão

## Sem migration
Usa `pendencia` + `trilha` + `outbox` existentes. Vocabulário da trilha já tem `DOSSIE_MONTADO` e
`DECIDIDA_NO_BLOCO`.

## Módulo assistente
`assistente.port.Bloco` + `assistente.internal.BlocoJdbc` (EntityManager para ler a pendência;
`Trilha` para registrar; `Outbox` para enfileirar; `ModelGateway` para a redação; `ContextoTenant`).

## Dossiê (determinístico)
`montar(org, pendenciaId)` lê a pendência e devolve:
- `dossie: [{rotulo, valor}]` — Quem espera, O que trava, Valor em jogo, Prazo, Cobranças (temperatura).
- `opcoes: [{chave, rotulo, consequencia}]` por classe:
  - DECISAO → Aprovar / Recusar
  - BLOQUEIO → Desbloquear / Manter bloqueio
  - ESTEIRA → Aprovar etapa / Reprovar etapa
- fonte navegável = a trilha do item (o front já a exibe via `/v1/pendencias/{id}/trilha`).
Sem recuperação externa → nunca completa de memória (guardrail).

## Redação (proposta, INV-10)
`redigir(org, pendenciaId, opcao, tom)`:
- monta `contexto` (fatos do dossiê + opção escolhida), `TarefaRedacao(org, INTERNA, pendenciaId,
  contexto, tom)`; chama `ModelGateway.redigir`.
- sucesso → `{rascunho, disponivel:true}`. `FalhasGateway.Indisponivel`/qualquer falha do gateway →
  `{rascunho: esqueleto editável, disponivel:false, aviso}`. Nada é enviado (é rascunho).

## Decidir (código executa)
`decidir(org, pendenciaId, opcao, texto, por)`:
- exige pendência não `FECHADA` (senão 409).
- `UPDATE pendencia SET status='FECHADA', fechada_em=now()`.
- trilha `DECIDIDA_NO_BLOCO` (ator humano; carga `{opcao}`).
- `outbox.publicar("decisao.comunicada", {pendencia_id, opcao, texto}, idem=pendencia:decidida)` —
  o envio ao canal é do despachante (como nos demais fechamentos). Só FECHADA comunica (INV-09).

## API
```
GET  /v1/pendencias/{id}/bloco                 -> {pendenciaId, titulo, classe, dossie[], opcoes[]}
POST /v1/pendencias/{id}/bloco/redigir         {opcao, tom}      -> {rascunho, disponivel, aviso}
POST /v1/pendencias/{id}/decidir               {opcao, texto}    -> 204 (409 se já fechada)
```

## Multi-tenant / reflexão / native
Predicados com `org_id`. DTOs via `Response` → `@RegisterForReflection`. A redação usa `java.net.http`
via o adaptador do gateway (já nativo). Nenhuma chamada de modelo no caminho de decidir (INV-10).

## Fora do design
- Perguntas ao dossiê com recuperação (BM25/embeddings) — pacote próprio (pgvector + ingestão).
- Verificação de consistência factual do rascunho contra o dossiê (RFC-0004) — incremento.
