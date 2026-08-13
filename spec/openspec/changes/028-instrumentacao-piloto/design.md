# Design — 028 instrumentação do piloto

## Superfície

O relatório vive em `/piloto`, visível apenas ao papel administrativo/facilitador. Não entra na
navegação de trabalho do gestor. O gestor vê somente a pergunta opcional de intervenção e a
reconciliação dentro da revisão de sexta.

## Persistência

### `intervencao_n2_motivo`

Registro append-only ligado à delegação e à entrada correspondente na trilha:

- `id`, `org_id`, `delegacao_id`, `motivo`, `observacao`, `registrado_por`, `registrado_em`;
- motivo fechado: `DISCORDOU`, `RISCO_ALTO`, `PRAZO_INADEQUADO`, `PROPOSTA_INCOMPLETA`,
  `NAO_CONFIA_NO_SILENCIO`, `OUTRO`;
- observação opcional, minimizada e nunca enviada ao modelo.

Não altera o desfecho: a intervenção continua sendo executada pelo motor determinístico existente.

### `reconciliacao_piloto`

Registro append-only por organização/semana: contagem de decisões fora da fila, notas minimizadas,
ator e timestamp. Não cria Pendência retroativamente; se o gestor quiser capturar um miss, usa o
escape existente, que continua medido.

### Auditoria de descarte

Reutiliza `descarte_captura` dentro da retenção existente. A seleção é aleatória e reproduzível por
`org_id + periodo + semente`, exclui conteúdo expirado e devolve somente o mínimo necessário. A
avaliação `ERA_PENDENCIA | NAO_ERA | INCONCLUSIVO` fica em tabela append-only separada.

## API proposta

```text
POST /v1/delegacoes/{id}/intervir
     { motivo?, observacao? }

GET  /v1/piloto/relatorio?inicio=&fim=
POST /v1/piloto/reconciliacoes
     { semana, decisoesForaDaFila, observacao? }
GET  /v1/piloto/descartes/amostra?inicio=&fim=&limite=&semente=
POST /v1/piloto/descartes/{id}/avaliacoes
     { resultado }
GET  /v1/piloto/saude
```

O contrato de intervenção deve preservar compatibilidade com clientes existentes: ausência de
motivo continua válida durante o piloto.

## Métricas

- estoque dependente: ENTRADA + AGENDADA + delegação ativa N3;
- fração autônoma: fechamentos N1/N2 sem ação do gestor / fechamentos elegíveis;
- N2: propostas, execuções por ausência, intervenções, devoluções, escalonamentos e reversões,
  sempre separados;
- captura: capturados, escapes, descartes avaliados e falsos negativos da amostra;
- recobrança e tempo até desbloqueio por classe, agregados;
- saúde: último evento por Fonte, latência de processamento e falha de gateway.

`recallEstimado` só é mostrado com tamanho da amostra e intervalo/aviso de incerteza. A decisão G7
combina escape, amostra, reconciliação e dupla-codificação; não usa média ingênua.

## Segurança e privacidade

- papel administrativo explícito para endpoints `/piloto`;
- `org_id` obrigatório em toda query;
- nenhum dado individual exportável;
- descarte expirado é irrecuperável;
- observações não atravessam o gateway de modelos;
- relatório registra o período e a completude das fontes.

## Alertas

Alerta só existe com ação: Fonte silenciosa oferece testar/configurar Fonte; falha do gateway aponta
configuração e últimas falhas. Não há alerta de novo item nem alerta sobre comportamento pessoal.

## Decisões que exigem confirmação antes do código

- mecanismo de papel `FACILITADOR` versus reutilização de `ADMIN`;
- tamanho padrão da amostra e acesso ao trecho descartado conforme RIPD;
- evento exato que representa “intervir” no motor atual;
- cálculo estatístico apresentado quando a amostra for pequena.
