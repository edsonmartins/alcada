# Cenários — Canal de voz

## C1 — fala livre vira intenção estruturada
- **WHEN** o gestor diz "fala pro Alexandre tocar o do Panorama"
- **THEN** o interpretador produz `REPASSAR` com alvo=item do Panorama e dono=Alexandre.

## C2 — campo crítico é confirmado antes de virar comando
- **WHEN** uma fala de REPASSAR é interpretada
- **THEN** o app pede confirmação de {dono, nível, prazo} em uma frase e **só enfileira o comando após o "sim"**.

## C3 — sem confirmação, nada sai
- **WHEN** o gestor não confirma (ou corrige) os campos críticos
- **THEN** nenhum comando é enfileirado — nada irreversível saiu de uma única fala (ADR-0014 §2).

## C4 — referência vaga com duas opções pergunta uma vez
- **WHEN** "aquele contrato" casa com dois itens da fila
- **THEN** o app pergunta **uma vez** com no máximo duas opções.

## C5 — referência vaga não resolvida vira revisão
- **WHEN** três tentativas não resolvem o alvo
- **THEN** o item é ADIADO para revisão, sem forçar decisão (ADR-0014 §6).

## C6 — fala sem intenção reconhecida
- **WHEN** a fala não casa nenhuma intenção da lista fechada
- **THEN** o app pede reformulação e **não inventa** intenção (INV-10).

## C7 — captura offline não se perde (INV-13)
- **WHEN** o gestor fala sem rede
- **THEN** o áudio e o comando resultante ficam persistidos localmente e sincronizam depois, sem perda.

## C8 — modo Ditar sempre disponível
- **WHEN** o gestor usa REGISTRAR ou CONSULTAR
- **THEN** funciona sem depender de decisão da fila (modo Ditar, ADR-0014 §1).
