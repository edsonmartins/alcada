# ADR-0010 — Inferência local para conteúdo sensível, roteamento por tarefa

**Status:** aceito · **Data:** 2026-07 · **Relacionado:** INV-12, ADR-0011, ADR-0014

## Contexto
O conteúdo processado inclui valores de contrato, avaliação de parceiros, negociação e áudio do
gestor no carro. Enviar isso para provedor de modelo de terceiro é incompatível com a proposta de
soberania de dados e com o perfil do cliente.

## Decisão
Roteamento de modelos por tarefa e sensibilidade:

| Tarefa | Onde | Justificativa |
|---|---|---|
| STT (voz) | **dispositivo** | latência < 1,5 s e conteúdo sensível |
| Extração e classificação | **local (stack on-prem/VPC)** | volume alto, modelo pequeno resolve |
| Deduplicação semântica / embeddings | **local** | volume alto, barato |
| Redação de mensagem difícil | local por padrão; nuvem opt-in por tenant | qualidade justifica opção |
| Consulta em linguagem natural | **local** | opera sobre dado estruturado do tenant |

Conteúdo classificado como sensível nunca sai, independentemente do opt-in.

## Consequências
- (+) Diferencial defensável no segmento e aderente à posição da casa.
- (+) Custo marginal por item previsível.
- (−) Exige capacidade de inferência operada; aumenta custo fixo e complexidade de deploy.
- (−) Qualidade de redação inferior à de modelos de fronteira em casos difíceis.

## Nota
Reabrir avaliação de TTS/STT on-device (Supertonic e alternativas) como gate anterior a qualquer POC
de voz — ver `docs/DECISOES-ABERTAS.md`.
