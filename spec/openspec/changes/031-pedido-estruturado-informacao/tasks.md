# Tasks — 031 pedido estruturado de informação

## Especificação
- [x] ADR-0030 e RFC-0011
- [x] Proposal, design, spec e tasks

## Backend
- [x] Migration V41 e isolamento no GuardaOrgId
- [x] Porta/comando transacional de pedido
- [x] Outbox correlacionada e entrega Linktor (WhatsApp local)
- [x] Retorno e vencimento determinísticos sob lock
- [x] Trilha sem token/endereço/texto livre

## Web
- [x] Ação oferecida após `INSUMO`/`TERCEIRO`
- [x] Formulário de contato, pergunta e prazo no drawer
- [x] Confirmação explícita antes do envio

## Testes
- [x] C1–C10 rastreáveis (`PedidoInformacaoTest`)
- [x] Suíte backend, web e typecheck (311 backend + 57 web em 2026-08-13)
- [ ] Integração real Linktor (aberta)

## Limites verificados da fatia local

- envio e retorno cobertos para contato externo WhatsApp já reconhecido;
- e-mail e pessoa interna aguardam resolução de canal pela Linktor;
- texto livre apenas desperta para avaliação humana;
- validação ao vivo permanece aberta e impede declarar adoção.
