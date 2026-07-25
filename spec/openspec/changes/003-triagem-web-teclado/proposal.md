# 003 — Triagem web por teclado

## Por quê
É onde a fila única vira decisão. Sem uma triagem rápida e de baixa fricção, a captura (001) só
acumula — o gestor não esvazia. INV-05 exige uma pergunta ("isso precisa mesmo de você?") e quatro
saídas fixas; ADR-0018 exige que a interface **empurre**, não ofereça um jardim para cuidar. A
triagem por teclado é o que torna "esvaziar a entrada" um gesto de segundos, não uma sessão.

## O quê
- **API das saídas**: `resolver`, `reservar`, `repousar`, `adiar` (o `repassar` reusa o motor de
  autonomia do 002). Cada uma é uma transição determinística com trilha.
- **Adiamento de primeira classe** (ADR-0002): `volta_em` obrigatório + `o_que_falta ∈
  {NADA, INSUMO, TERCEIRO}`, com resposta diferenciada por valor.
- **`GET /v1/hoje`**: no máximo 3 itens, com justificativa por item.
- **SPA web** (React 19 + Mantine, ADR-0023): Entrada com cursor de teclado, drawer de detalhe,
  atalhos `1–4`/`a`/`j`/`k`/`⌘K`, ação em lote, janela de desfazer (INV-14) por mutação otimista.
- **Anti-jardinagem** (ADR-0018): sem arrastar, sem campo livre obrigatório, sem tela contemplativa;
  sessão improdutiva é nomeada.

## Fora de escopo
- **Bloco de decisão / dossiê / redação** (pacotes 011/012/013) — `reservar` apenas agenda; o dossiê
  é fase posterior.
- **Radar, métricas e revisão de sexta** (009/010).
- **Voz e modo trajeto** (016/017) — a triagem sobrevive sem tela (ADR-0002), mas a superfície de voz
  é outro pacote.
- **Despertar por cobrança** depende da captura (001); aqui o despertar por **data** é garantido.

## Critério de aceite
- As quatro saídas e o adiar transicionam o estado e gravam trilha rastreável pelo cenário.
- Só `FECHADA` notifica solicitante/contraparte.
- `adiar` sem `volta_em` é rejeitado; `o_que_falta` fora do enum é rejeitado.
- `/hoje` nunca devolve mais de 3 itens.
- Triagem operável **inteira pelo teclado**; nenhuma ação de arrastar existe.
- Sessão de uso sem transição de estado é sinalizada ao gestor.
