# Design — 022 canal de voz

## Pipeline (no device)
```
áudio ──► persistência local (INV-13) ──► STT on-device ──► transcrição
                                                              │
                                                              ▼
                                       Interpretador de intenção (INV-10)
                                                              │
                        {intencao, alvoVago?, campos, confianca}
                                                              ▼
                       resolução de referência + confirmação de campos críticos
                                                              ▼
                                    Comando (021) enfileirado offline
```

## Interpretador de intenção
- Saída **fechada**: uma das intenções de 021 + campos. Se nada casa → pede
  reformulação (não inventa intenção).
- Mesma disciplina da consulta NL (020): o modelo escolhe de um conjunto fechado;
  quem executa é código. Pode reusar `/v1/consulta` para CONSULTAR.

## Referência vaga (ADR-0014 §6)
- "aquele do Panorama" → casa contra a fila (título/entidade/quem espera).
- 0 candidatos → "não achei nada do Panorama na fila".
- 1 candidato → segue para confirmação.
- ≥2 candidatos → pergunta **uma vez**, no máximo **duas** opções.
- 3ª tentativa sem resolução → cria ADIAR para revisão (não força).

## Confirmação de campos críticos (ADR-0014 §2)
- Campos críticos por intenção: REPASSAR → {dono, nivel, prazo}; ADIAR → {volta_em};
  REGISTRAR → nenhum (é só captura).
- Frase única de confirmação: *"Alexandre, N2, até sexta. Confirma?"*
- Sem "sim" explícito → **não** vira comando (nada irreversível de uma fala só).

## Gramática (RFC-0005) — exemplos, não sintaxe rígida
| Intenção | Fala | Campos críticos |
|---|---|---|
| RESOLVER | "já resolvi o do aditivo" | item |
| REPASSAR | "fala pro Alexandre tocar e me avisa" | item, dono, nível |
| ADIAR | "esse eu vejo semana que vem" | item, volta_em |
| REGISTRAR | "lembra de cobrar o Panorama" | — |
| CONSULTAR | "o que travou hoje" | — |

## Modos
- **Ditar**: REGISTRAR/CONSULTAR (+ cobrar) — sempre disponível.
- **Despachar**: RESOLVER/REPASSAR/ADIAR — sujeito à recusa por movimento (023).

## Métricas (RFC-0005)
- taxa de correção após confirmação (proxy de erro de STT);
- perda de comando por falha de sync (alvo: zero — garantido por 021).
