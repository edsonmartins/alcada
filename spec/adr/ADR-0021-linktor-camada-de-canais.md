# ADR-0021 — Linktor como camada única de canais (WhatsApp e e-mail)

**Status:** aceito · **Data:** 2026-07 · **Substitui:** adaptadores diretos previstos no RFC-0001

## Contexto
O RFC-0001 previa adaptadores próprios por canal: Evolution API para mensageria e IMAP/Graph para
e-mail. Linktor (produto da casa, Go, multicanal) já resolve esse problema e está sob nosso controle
de roadmap.

## Decisão
Linktor é a **única** camada de entrada e saída de mensagens do produto. Alçada não fala com WhatsApp
nem com servidor de e-mail diretamente.

**Responsabilidades do Linktor**
- conexão e sessão de canal, reconexão, rate limiting
- recebimento e envio, com confirmação de entrega
- normalização para um envelope único, independente de canal
- guarda do conteúdo bruto dentro da infraestrutura própria
- identidade de canal (número, endereço, grupo) e vínculo com pessoa/entidade

**Responsabilidades da Alçada**
- filtro de relevância, extração, deduplicação, roteamento, decisão
- nunca persistir bruto que o Linktor já guarda; referenciar por id

**Contrato**
```
Linktor → Alçada MensagemRecebida { canal, fonte_id, autor_ext, thread_ref, texto,
                                    anexos_ref[], recebida_em, mensagem_id }
Alçada → Linktor EnviarMensagem   { canal, destino, texto, responder_a?, idempotency_key }
```
Entrega assíncrona nos dois sentidos, com idempotência por `mensagem_id` / `idempotency_key`.

**E-mail no Linktor.** Exige tratamento que mensageria não tem: reconstrução de thread, extração do
trecho novo (a pendência costuma estar no quarto parágrafo da nona mensagem), lista de cópia e
distinção entre remetente e solicitante real. Isso é trabalho de canal e fica no Linktor; a Alçada
recebe o trecho já isolado.

## Consequências
- (+) Elimina dependência de gateway de WhatsApp de terceiro.
- (+) O conteúdo bruto **não sai da casa** — o que torna a minimização do ADR-0020 realmente possível:
  para o modelo vai apenas o trecho pseudonimizado, não a mensagem.
- (+) Um único contrato de canal; novos canais entram sem tocar no domínio da Alçada.
- (+) Prova de uso real do Linktor, com requisitos que melhoram o produto.
- (−) Acoplamento entre roadmaps: atraso no Linktor bloqueia F1 da Alçada.
- (−) Requisitos novos para o Linktor: threading de e-mail, retenção com expurgo por `expira_em`
  (ADR-0011) e auditoria de captura seletiva.

## Requisitos que a Alçada impõe ao Linktor
1. Captura seletiva configurável por fonte — menção ao bot, resposta a item, padrão. **Sem varredura
   completa** (ADR-0011), com log auditável da proporção processada.
2. Retenção do bruto com data de expurgo por fonte.
3. Envio com resposta encadeada, para fechar o laço no canal de origem (ADR-0013).
4. Entrega ao menos uma vez, com deduplicação por `mensagem_id`.
