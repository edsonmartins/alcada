# ADR-0014 — Canal de voz: offline-first, confirmação de campos críticos, recusa por classe

**Status:** aceito · **Data:** 2026-07 · **Relacionado:** INV-13, INV-14, ADR-0010

## Contexto
O gestor-alvo passa 40–90 min/dia em trânsito e já usa esse tempo para resolver coisas por telefone.
O método (uma pergunta, quatro saídas, sem comparação) sobrevive sem tela — é o formato certo para voz.

## Decisão

**1. Dois modos, com risco distinto.**

| Modo | O que permite | Disponível em movimento |
|---|---|---|
| Ditar | registrar, cobrar, consultar | sim |
| Despachar | decidir a fila | parcialmente — ver (3) |

**2. Voz decide, confirmação valida.** Nada irreversível sai de uma única fala. O sistema confirma
apenas os campos críticos (nome, valor, prazo) em uma frase curta. Reconhecimento erra exatamente
aí ("Carol"/"Carla", "quinze"/"cinquenta").

**3. Recusa por classe em movimento.** Decisões de alto impacto ou irreversíveis são recusadas
durante o trajeto, com justificativa e agendamento de bloco:
> "Esse é o de embalagem. Não vou passar ele agora — separei para quinta, com o dossiê."
Com o veículo parado, tudo é liberado.

**4. Janela de reversibilidade de trajeto.** Efeitos externos não disparam durante o deslocamento.
Ao encerrar o trajeto, o app apresenta o resumo com desfazer por item; só então comunica terceiros.

**5. Offline obrigatório.** Áudio capturado e persistido localmente, fila sincronizada de forma
idempotente. Perder uma fala custa a adoção permanentemente (INV-13).

**6. Resolução de referência vaga.** A fala real é "aquele negócio do Panorama, fala pro Alexandre
tocar". O sistema resolve contra a fila; sem certeza, pergunta uma vez com no máximo duas opções;
na terceira tentativa, adia para revisão.

## Consequências
- (+) Converte 1–1,5 h/dia hoje improdutiva em despacho verificável.
- (+) Recusar decisão grave em trânsito ganha confiança em vez de perder.
- (−) STT on-device eleva o custo do app e restringe dispositivos.
- (−) Detecção de movimento é heurística; falso positivo bloqueia ação legítima.
