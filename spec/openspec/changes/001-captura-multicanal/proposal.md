# 001 — Captura multicanal

## Por quê
INV-02 exige que o gestor nunca cadastre. Sem captura funcionando, todo o resto do produto é
inalcançável: não há fila única, não há triagem, não há métrica.

## O quê
Ingestão de mensageria (grupo declarado), e-mail encaminhado e webhook de sistema; normalização,
filtro de relevância, extração estruturada, resolução de entidade, deduplicação com temperatura e
roteamento por classe.

## Fora de escopo
Áudio (pacote 016), transcrição de reunião (fase posterior), captura de compromisso em ata.

## Critério de aceite
- Recall ≥ meta definida em G7, medido contra baseline manual em 2 semanas de operação
- Latência p95 de ingestão → item visível < 30 s
- Zero varredura completa de canal (auditável por log de filtro)
- Reversão de fusão em 1 toque
