# ADR-0005 — Captura passiva; o gestor nunca cadastra

**Status:** aceito · **Data:** 2026-07 · **Relacionado:** INV-02, RFC-0001

## Contexto
"Organizar" é mais trabalho para quem já não tem tempo. Ferramentas de gestão morrem na terceira
semana porque exigem alimentação manual, ficam desatualizadas e perdem confiança.

## Decisão
Nenhuma superfície pode ter criação manual como caminho principal. A pendência nasce de captura:

| Camada | Fontes |
|---|---|
| Sistema | monitoramento, ERP, esteira, fila de erro, webhook |
| Humano | grupo de mensageria, e-mail encaminhado, áudio, transcrição de reunião |
| Compromisso | extração de promessa em ata/transcrição ("eu vejo isso e te retorno") |

O gestor **confirma ou corrige** o extraído. Criação manual existe como escape, escondida, e é
métrica de falha: se ultrapassar 10% das entradas, a captura está inadequada.

## Consequências
- (+) Remove a barreira de adoção mais alta do segmento.
- (+) A camada "compromisso" recupera backlog hoje totalmente invisível.
- (−) Recall da extração vira risco existencial do produto; exige medição contínua (F1).
- (−) Falso positivo polui a fila; exige descarte de 1 toque e realimentação do classificador.

## Alternativas rejeitadas
- **Formulário rápido:** ainda é cadastro; morre igual.
- **Integração só com ferramenta de tarefas existente:** o cliente-alvo não usa nenhuma.
