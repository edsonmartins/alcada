# Superfície Mobile

**Stack:** Flutter 3.27 · offline-first · sem boilerplate interno (ADR-0023)

## Papel
Canal **principal** de captura e despacho. Trânsito, corredor, fora da mesa. Não é espelho do web:
o que exige leitura longa ou comparação pertence ao desktop.

## Telas
| Tela | Função |
|---|---|
| Hoje | três itens, ação direta |
| Entrada | triagem sequencial, um por vez |
| Delegados | acompanhamento com prazo |
| Trajeto | modo mãos-livres conduzido |
| Resumo de trajeto | lista do que foi decidido, com desfazer |
| Captura | botão único de gravação, funciona offline |

## Modo trajeto
1. Ativação por atalho de sistema, CarPlay/Android Auto ou toque único
2. O sistema **lê a sequência**; o gestor não navega
3. Item que não cabe em ~8 s de fala não entra
4. Classes de alto impacto são recusadas em movimento, com bloco agendado (ADR-0014)
5. Efeitos externos ficam represados
6. Ao encerrar: resumo com desfazer por item; só então comunica terceiros

## Confirmação de campos críticos
Somente nome, valor e prazo — é onde o STT erra.
> "Alexandre, N2, até sexta. Confirma?"

## Offline (INV-13)
- áudio e comando persistidos localmente **antes** de qualquer rede
- fila com retry exponencial e `Idempotency-Key` por comando
- indicador honesto de pendente de sincronização
- perda de comando por falha de sync: alvo zero, métrica publicada

## Privacidade
STT no dispositivo (ADR-0010). Áudio bruto não sai do aparelho por padrão; o que sobe é texto
extraído estruturado. Retenção local curta e configurável.

## Notificações
Somente três tipos, por design:
- N2 prestes a executar sem intervenção (a 50% e a 90% do prazo)
- item escalado por silêncio de ambos
- resumo de trajeto pronto

Notificação de "novo item na entrada" é **proibida** — reintroduz a reatividade que o produto combate.
