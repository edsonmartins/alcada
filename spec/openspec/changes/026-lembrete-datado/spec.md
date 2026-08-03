# Cenários — Lembrete datado

## Motor e modelo (F2.1)

## C1 — resolver com lembrete fecha o item e agenda o compromisso
- **WHEN** o gestor resolve um item deixando `{quando, texto}`
- **THEN** a pendência vai a `FECHADA` e nasce **uma pendência nova** em `DORMINDO`, com `volta_em = quando`, `origem = LEMBRETE` e `origem_pendencia_id` apontando para o item resolvido.
- *Teste:* `LembreteDatadoTest.resolver_com_lembrete_fecha_o_item_e_cria_o_lembrete_dormindo`

## C2 — a trilha liga origem → lembrete (INV-11)
- **WHEN** o lembrete é criado
- **THEN** a pendência de origem registra `RESOLVIDA` e `LEMBRETE_CRIADO` (ator `HUMANO`), com o id do lembrete e o `quando` na carga.
- *Teste:* `LembreteDatadoTest.trilha_registra_resolvida_e_lembrete_criado_na_origem`

## C3 — no dia, o lembrete volta pela Entrada (INV-03)
- **WHEN** chega a data
- **THEN** o `DESPERTAR` traz o lembrete para `ENTRADA` com `DESPERTADA` — mesma fila, mesmas quatro saídas.
- *Teste:* `LembreteDatadoTest.lembrete_desperta_na_entrada_no_dia`

## C4 — resolver sem lembrete continua igual
- **WHEN** o gestor resolve sem lembrete
- **THEN** nada de novo é criado (regressão do caminho de sempre).
- *Teste:* `LembreteDatadoTest.resolver_sem_lembrete_nao_cria_nada`

## C5 — data inútil é recusada e o item **não** fecha
- **WHEN** o lembrete vem sem data/texto, no passado, ou a mais de 12 meses (quase sempre data mal interpretada — INV-10)
- **THEN** a operação é recusada e a pendência **continua em `ENTRADA`**: nada de item meio resolvido.
- *Testes:* `LembreteDatadoTest.lembrete_no_passado_recusa_e_nao_resolve`, `.lembrete_a_mais_de_doze_meses_recusa`

## C6 — o horizonte sai da distância até a data (ADR-0008)
- **WHEN** o compromisso é hoje / dentro de 7 dias / além disso
- **THEN** o lembrete nasce `HOJE` / `SEMANA` / `TRIMESTRE`, no fuso do tenant.
- *Teste:* `LembreteDatadoTest.horizonte_deriva_da_data_do_compromisso`

## C7 — o lembrete não é captura (INV-01)
- **WHEN** um lembrete é criado
- **THEN** nenhum `CAPTADA` é emitido: ele não conta como item que "entrou" no encolhimento — senão a métrica de encolhimento mentiria.
- *Teste:* `LembreteDatadoTest.lembrete_nao_conta_como_item_captado`

## C8 — isolamento por organização (INV-15)
- **WHEN** um lembrete é criado em A
- **THEN** B não o enxerga.
- *Teste:* `LembreteDatadoTest.lembrete_fica_na_organizacao_do_item`

## C9 — pelo endpoint
- **WHEN** `POST /v1/pendencias/{id}/resolver { nota, lembrete }`
- **THEN** `204` e o lembrete criado; data no passado → `422 alcada:lembrete.invalido` em problem+json, com o item intacto.
- *Teste:* `LembreteDatadoTest.endpoint_resolve_com_lembrete_e_recusa_data_passada`

## Comando móvel e voz (F2.2) — pendentes

## C10 — "resolvi, mas marquei reunião quinta" vira RESOLVER + lembrete
- **WHEN** o gestor dita a decisão com um compromisso
- **THEN** o assistente **propõe** as duas coisas numa frase só e confirma antes de despachar (ADR-0014 §2).

## C11 — data ambígua o assistente pergunta
- **WHEN** o modelo não resolve a data com segurança ("quinta" sem semana)
- **THEN** o assistente pergunta ("quinta que vem, dia 6?") — nunca chuta (INV-10).

## C12 — offline
- **WHEN** o comando é ditado sem rede
- **THEN** `RESOLVER` + lembrete ficam na fila local e sincronizam depois, idempotentes por `comandoId` (INV-13).

## Calendário (F2.3/F2.4) — pendentes

## C13 — o compromisso entra no calendário do gestor
- **WHEN** o gestor conectou o calendário e pediu o evento
- **THEN** o evento é criado **depois da janela**, pelo outbox, e a trilha registra `COMPROMISSO_AGENDADO`.

## C14 — desfazer na janela: o calendário nunca soube
- **WHEN** o gestor desfaz dentro da janela
- **THEN** o `EVENTO_CALENDARIO` é descartado no outbox e nenhum evento existe (INV-14).

## C15 — falha de entrega não some
- **WHEN** o provedor recusa/está fora
- **THEN** retry; falha definitiva ⇒ `FALHA_COMPROMISSO` e o gestor fica sabendo (INV-13).

## Web (F2.5) — pendente

## C16 — conectar e revogar o calendário
- **WHEN** o gestor abre a sessão/config
- **THEN** conecta o Google/Outlook (OAuth) e pode revogar; sem conta conectada, o lembrete funciona igual — só não vira evento.
