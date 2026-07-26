# Design — 023 modo trajeto

## Estados
```
PARADO  ──(sinal de movimento)──►  EM_TRAJETO  ──(estacionou)──►  RESUMO
   ▲                                                                  │
   └──────────────────── desfazer/comunicar ◄────────────────────────┘
```

## Sinal de movimento (porta plugável)
`FonteMovimento.emMovimento(): bool` — implementação (sensor/veículo/manual) é
decisão pendente do RFC-0005. O comportamento do modo não depende de qual fonte.

## Recusa por classe em movimento (ADR-0014 §3)
- Classe/decisão de **alto impacto ou irreversível** → recusada EM_TRAJETO, com
  justificativa falada + **bloco agendado** (RESERVAR com gerar_dossie) para depois.
- A lista de classes recusáveis é configuração por tenant (default: BLOQUEIO e
  decisões acima de um valor-limite).
- PARADO: nada é recusado por movimento.

## Janela de reversibilidade de trajeto (ADR-0014 §4, INV-14)
- Comandos ditados EM_TRAJETO são enfileirados (021) mas marcados **represados**:
  nenhum efeito externo sai enquanto durar o trajeto.
- Isto compõe com a janela do motor (002): trajeto represa a emissão; a janela de
  desfazer segue valendo por item.

## Resumo ao estacionar
- Ao voltar a PARADO, o app apresenta o **resumo do trajeto**: itens despachados,
  cada um com **desfazer**. Só após a confirmação (ou o fim da janela) os efeitos
  externos são liberados e terceiros comunicados.

## Condução
- Sequência escolhida pelo sistema (fila priorizada), um item por vez.
- Item que exige mais de ~8s de fala para caber → fora do trajeto (vai para bloco).

## Integrações de plataforma
CarPlay / Android Auto; áudio em background; Live Activity/notificação persistente
enquanto EM_TRAJETO. **Não** é notificação de "novo item" (CLAUDE.md §8).
