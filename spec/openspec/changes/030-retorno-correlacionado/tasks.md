# Tasks — 030 retorno correlacionado

## Gate Linktor
- [ ] Implementar/confirmar ida e volta de `metadata.alcada_correlation` no Linktor
- [x] Adicionar fixture assinada de `message.received` com `data.context.alcada_correlation`
- [ ] Testar WhatsApp e e-mail em ambiente de integração

## Fundação
- [x] Migration V40, emenda de trilha e GuardaOrgId
- [x] Porta de correlação e geração/hash de token
- [ ] Correlação criada/revogada junto do contrato de delegação
- [x] Token fora de logs/trilha e fora de payload persistente claro

## Canal
- [ ] `EnviarDireto`/`EnviarMensagem` com correlação opcional e adaptadores/stubs
- [x] Parser de `data.context` no webhook
- [x] Validação de tenant, canal, autor, expiração e revogação
- [x] Idempotência por hash de `message.id`

## Domínio
- [x] Minimização e tipo fechado `INCONCLUSIVO` no modo observar
- [ ] Lock com virada N2 e `retorno_pendente`
- [ ] Tabela determinística de transições do RFC-0010
- [x] Trilha `RETORNO_RECEBIDO` sem texto/token/PII
- [ ] Porta de leitura para o dossiê

## Superfícies
- [ ] Evidência e ação no item/Entrada, sem caixa nova
- [ ] Dossiê mostra retorno e fonte
- [ ] Métricas agregadas de correlação/rejeição/latência
- [ ] Feature flag por tenant e modo observação

## Testes
- [ ] C1–C18 com nomes rastreáveis
- [ ] Corrida retorno × virada em ambas as ordens
- [ ] Vazamento: token, endereço e PII não atravessam trilha/modelo
- [x] Reentrega, token de outro tenant e autor divergente
- [ ] Native build e fixture Linktor real

## Evidência local (2026-08-13)

- envio direto inclui `metadata.alcada_correlation`; webhook assinado consome
  `data.context.alcada_correlation`;
- retorno válido é persistido minimizado e auditado como `OBSERVADO`, sem executar N2 nem criar
  uma segunda pendência;
- testes locais cobrem ida HTTP, volta assinada, reentrega, isolamento de tenant e autor divergente;
- gate externo continua aberto: o contrato precisa ser confirmado com a Linktor e exercitado em
  WhatsApp/e-mail reais antes de qualquer transição operacional.

## Validação
- [ ] Rodar observação no piloto e comparar com reconciliação humana
- [ ] Habilitar transição para um tenant
- [ ] Medir retorno correlacionado, recobrança e tempo até desbloqueio
