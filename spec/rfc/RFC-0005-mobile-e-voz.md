# RFC-0005 — Aplicativo móvel e canal de voz

**Status:** proposto · **Implementa:** ADR-0014, INV-13

## Arquitetura do app (Flutter)

```
[ Captura de áudio ] ──► [ STT on-device ] ──► [ Interpretador de intenção ]
        │                                              │
        ▼                                              ▼
[ Fila local persistida ] ◄────────────────── [ Confirmação de campos críticos ]
        │
        ▼ (sync idempotente)
[ API /v1/comandos ]
```

**Offline-first.** Áudio e comandos persistidos localmente antes de qualquer chamada de rede. Fila
com retry exponencial e chave de idempotência por comando. Nenhuma perda por falta de sinal.

## Gramática de comandos
Não é sintaxe rígida. O interpretador mapeia fala livre para intenção:

| Intenção | Exemplo de fala | Campos críticos |
|---|---|---|
| `RESOLVER` | "já resolvi o do aditivo" | item |
| `REPASSAR` | "fala pro Alexandre tocar e me avisa" | item, dono, nível |
| `ADIAR` | "esse eu vejo semana que vem" | item, volta_em, o_que_falta |
| `REGISTRAR` | "lembra de cobrar o Panorama" | — |
| `CONSULTAR` | "o que travou hoje" | — |

**Confirmação:** apenas campos críticos, em uma frase.
> "Alexandre, N2, até sexta. Confirma?"

**Referência vaga:** resolve contra a fila; ambiguidade pergunta uma vez com no máximo duas opções;
terceira tentativa adia para revisão.

## Modo trajeto
- sequência conduzida pelo sistema, não navegação pelo usuário
- item que não cabe em ~8 s de fala não entra no trajeto
- classes de alto impacto são recusadas em movimento (ADR-0014), com bloco agendado
- efeitos externos represados até o fim do trajeto
- **resumo de trajeto** ao estacionar, com desfazer por item, antes de comunicar terceiros

## Integrações de plataforma
CarPlay / Android Auto, áudio em background, ativação por atalho de sistema, Live Activity /
notificação persistente durante o trajeto.

## Decisões pendentes
- motor de STT on-device (qualidade em PT-BR com ruído de carro, tamanho do modelo, licença)
- TTS: reavaliar Supertonic e alternativas como gate anterior à POC (ver DECISOES-ABERTAS)
- detecção de movimento: sensor vs. conexão do veículo vs. declaração manual

## Métricas
- % do trajeto convertido em decisão
- taxa de correção após confirmação (proxy de erro de STT)
- perda de comando por falha de sync (alvo: zero)
