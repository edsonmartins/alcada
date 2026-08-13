# Design — 031 pedido estruturado de informação

## Fatia vertical

1. `pedido_informacao` e correlação tipada por alvo;
2. comando transacional sobre a Pendência;
3. outbox reversível e entrega Linktor;
4. retorno/timeout determinísticos sob lock;
5. formulário dentro do drawer já existente.

## Fronteiras

Triagem publica a porta do comando. Notificação resolve e entrega o contato sem expor PII ao
domínio. Autonomia/correlação publica o recebimento tipado; captura apenas autentica e encaminha.

## Segurança

Todas as consultas carregam `org_id`. Token e endereço não entram em trilha. Pergunta enviada é
conteúdo confirmado pelo humano; a resposta é minimizada antes de persistir.

## Rollout

Testes locais habilitam a transição do pedido, mas o estado permanece “testado localmente,
parcial” até validar metadata e resposta na Linktor real.

