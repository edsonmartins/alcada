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

## Comando móvel e voz (F2.2)

## C10 — "resolvi, mas marquei reunião quinta" vira RESOLVER + lembrete
- **WHEN** o gestor dita a decisão com um compromisso
- **THEN** o assistente **propõe** as duas coisas numa frase só, com a data falada de volta ("te lembro quinta, 6, às 10h"), e só despacha após o "sim" (ADR-0014 §2); no fim, a fala do resultado diz quando ele será lembrado.
- *Testes:* `InterpretadorVozTest.resolverComLembreteConfirmaComADataFalada` · mobile `test/lembrete_datado_test.dart` ("resolver com lembrete enfileira campos.lembrete", "dataFalada…")

## C11 — data que não dá para resolver: o assistente pergunta
- **WHEN** o modelo não devolve a data, ou devolve uma que não sobrevive à validação (passado, >12 meses)
- **THEN** o assistente **pergunta** ("Para quando eu te lembro de…?") e nada é despachado — nunca chuta (INV-10).
- *Testes:* `InterpretadorVozTest.lembreteSemDataPerguntaEmVezDeChutar`, `.lembreteComDataNoPassadoPergunta` · mobile "lembrete sem data pergunta em vez de despachar"

## C12 — pelo comando (offline-first)
- **WHEN** o comando `RESOLVER` chega com `campos.lembrete`
- **THEN** o item fecha e o lembrete nasce **na mesma transação** (reenvio não duplica, INV-13); data malformada vira `ERRO` do comando e o item **não** fecha.
- *Testes:* `ComandoRepasseExternoTest.comando_resolver_com_lembrete_fecha_e_agenda`, `.comando_com_lembrete_invalido_e_recusado`

## C12b — sem rede, o compromisso não se perde (INV-13)
- **WHEN** o gestor dita "resolvi X, mas marquei reunião quinta 10h" **offline**
- **THEN** o matcher local resolve a data no relógio do aparelho e o comando leva o lembrete; se a data não for legível, o assistente **pergunta** em vez de resolver sem o compromisso.
- *Testes (mobile):* "offline: …guarda o lembrete", "offline: compromisso sem data vira pergunta", "dataDaFala resolve as formas comuns e recusa o que não dá"

## Calendário (F2.3a — porta e entrega; provedor real na F2.3b)

## C13 — o compromisso entra no calendário do gestor
- **WHEN** o lembrete foi criado com `comCalendario`
- **THEN** o evento **não** sai antes da janela; vencida, é criado na agenda **do gestor** (não do tenant), o `evento_calendario_id` fica na pendência-lembrete e a trilha registra `COMPROMISSO_AGENDADO`.
- *Teste:* `CompromissoCalendarioTest.compromisso_entra_no_calendario_depois_da_janela`

## C14 — desfazer na janela: o calendário nunca soube
- **WHEN** o efeito é descartado antes de vencer a janela
- **THEN** nenhum evento é criado e não há `COMPROMISSO_AGENDADO` (INV-14).
- *Teste:* `CompromissoCalendarioTest.descartar_na_janela_impede_o_evento`

## C15 — falha de entrega não some
- **WHEN** o provedor está fora ⇒ a mensagem volta para retentativa (INV-13); **WHEN** o gestor não tem calendário conectado ⇒ `FALHA_COMPROMISSO` e a mensagem é dada por entregue (não adianta repetir), com o lembrete valendo dentro do Alçada.
- *Testes:* `CompromissoCalendarioTest.provedor_indisponivel_reprocessa`, `.sem_conta_conectada_registra_falha_e_nao_repete`, `.reprocesso_nao_duplica_o_evento`, `.lembrete_sem_calendario_nao_publica_efeito`

## C15b — conectar/revogar o calendário (F2.3b) — pendente
- **WHEN** o gestor conecta a conta Google/Outlook por OAuth
- **THEN** o token fica cifrado, escopo mínimo, revogável; sem conta, `comCalendario` só produz `FALHA_COMPROMISSO`.

## Web (F2.5) — pendente

## C16 — conectar e revogar o calendário
- **WHEN** o gestor abre a sessão/config
- **THEN** conecta o Google/Outlook (OAuth) e pode revogar; sem conta conectada, o lembrete funciona igual — só não vira evento.
