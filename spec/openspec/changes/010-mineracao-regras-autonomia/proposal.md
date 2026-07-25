# 010 — Mineração de regra de autonomia

## Por quê
O INV-01 exige que o volume que depende do gestor **caia mês a mês**. O radar (009) mostra a curva;
falta o motor que a faz descer: transformar **padrão decisório observado em regra explícita**
(RFC-0003 §A, ADR-0003). Sem isso, N3→N2→N1 depende só de o gestor lembrar de criar regra à mão —
que é justamente o que ele não faz (INV-02).

A regra já é **aplicada** hoje: `ProcessadorCaptura` roteia por `regra_autonomia (classe, ativa) →
nivel` e grava `ROTEADA_POR_REGRA`. Falta **minerar e propor** a regra; aceitar cria a linha que o
motor de captura já consome.

## O quê
- **Mineração determinística** (sem modelo, INV-10) sobre trilha+pendência, janela de 90 dias:
  uma classe vira candidata quando teve **≥ N ocorrências** (padrão 15, configurável),
  **desfecho consistente ≥ 95%** e **zero reversões** (interrompida / desfeita na janela / devolvida
  / escalada).
- **Proposta com evidência clicável** (ADR-0019): os casos navegáveis. Sem os casos, não propõe.
- **API**: `GET /v1/regras`, `GET /v1/regras/propostas`, `POST /v1/regras` (aceitar — humano confirma,
  INV-10), `POST /v1/regras/propostas/silenciar`, `POST /v1/regras/{id}/desativar`.
- **Silenciar** persiste a assinatura silenciada — não volta a ser proposta.
- **Tela `/alcadas`** (web): regras ativas + propostas com evidência, aceitar/silenciar.

## Escopo honesto (limite do motor atual)
O motor de captura casa regra por **classe**. Então a regra criada é `{classe, nivel, dono_id}`, e a
mineração agrupa por **classe** (a faixa de valor e o tipo de solicitante entram como **evidência**,
não como chave). A assinatura fina do RFC (`{classe, faixa_valor, tipo_solicitante, escopo}`) exige
upgrade do motor — pacote futuro.

## Fora de escopo
- **Checklist de esteira** (RFC-0003 §B) — pacote próprio.
- **Laço de aprendizado** (1 pergunta pós-decisão) — pacote próprio; aqui o silêncio é por assinatura.
- **Assinatura fina** por faixa/tipo/escopo (exige motor de aplicação por faixa).
- **Promoção automática** — proibida (INV-10): toda regra nasce de confirmação humana.

## Critério de aceite
- Uma classe com ≥N ocorrências, ≥95% de consistência e zero reversões em 90 dias vira proposta com
  os casos navegáveis; abaixo disso, não.
- Aceitar cria `regra_autonomia` ativa; a partir daí novas capturas da classe são roteadas por regra.
- Silenciar impede novas propostas daquela classe.
- Nada é promovido sem aceite humano; nenhuma proposta cruza tenant (INV-15); zero chamada de modelo.
