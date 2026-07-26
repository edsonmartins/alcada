# Cenários — Modo trajeto

## C1 — decisão de alto impacto é recusada em movimento
- **WHEN** EM_TRAJETO o gestor tenta despachar uma classe de alto impacto (ex.: BLOQUEIO)
- **THEN** o comando é recusado com justificativa e um **bloco é agendado** (RESERVAR + dossiê) para depois.

## C2 — parado, a mesma decisão é liberada
- **WHEN** PARADO o gestor despacha a mesma classe
- **THEN** o comando é aceito normalmente (sem recusa por movimento).

## C3 — efeito externo represado durante o trajeto (INV-14)
- **WHEN** um comando aceito EM_TRAJETO produziria efeito externo
- **THEN** o efeito **não sai** enquanto durar o trajeto (fica represado).

## C4 — resumo ao estacionar com desfazer por item
- **WHEN** o trajeto encerra (volta a PARADO)
- **THEN** o app mostra o resumo dos itens despachados, cada um com **desfazer**, antes de comunicar terceiros.

## C5 — só após o resumo os efeitos são liberados
- **WHEN** o gestor confirma o resumo (ou a janela fecha)
- **THEN** os efeitos externos represados são liberados e terceiros são comunicados — não antes.

## C6 — item longo não entra no trajeto
- **WHEN** um item exige mais de ~8s de fala para caber
- **THEN** ele é deixado fora do trajeto e agendado como bloco.

## C7 — condução é do sistema
- **WHEN** o modo trajeto está ativo
- **THEN** o sistema apresenta um item por vez em sequência que ele escolhe — o gestor não navega a fila.
