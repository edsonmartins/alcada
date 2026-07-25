# Spec — 010 mineração de regra de autonomia

## Cenário: classe consistente vira proposta com evidência
**WHEN** uma classe teve `>= min` ocorrências fechadas em 90 dias, `>= 95%` sem reversão e **zero**
reversões
**THEN** `GET /v1/regras/propostas` retorna a classe como candidata
**AND** traz `ocorrencias`, `consistencia`, `nivelSugerido` e `casos[]` navegáveis (ADR-0019)

## Cenário: poucos casos não viram regra
**WHEN** uma classe teve menos que `min` ocorrências
**THEN** ela **não** aparece em `/v1/regras/propostas` (ruído não vira política, RFC-0003)

## Cenário: reversão derruba a candidata
**WHEN** uma classe teve boa contagem mas ao menos uma pendência foi `INTERROMPIDA`,
`DESFEITA_NA_JANELA`, `DEVOLVIDA_PELO_EXECUTOR` ou `ESCALADA`
**THEN** ela não é proposta (zero reversões é condição dura)

## Cenário: aceitar cria a regra que o motor aplica
**WHEN** o gestor aciona `POST /v1/regras` com `{classe, nivel, donoId}` de uma proposta
**THEN** uma `regra_autonomia` ativa é criada para a classe
**AND** a próxima captura dessa classe é roteada por regra (`ROTEADA_POR_REGRA`, motor já existente)

## Cenário: não promove sozinho (INV-10)
**WHEN** existe uma proposta candidata
**THEN** nenhuma regra é criada até um humano aceitar
**AND** a mineração nunca chama modelo

## Cenário: uma regra ativa por classe
**WHEN** já existe `regra_autonomia` ativa para a classe e o gestor tenta criar outra
**THEN** a API responde `409`
**AND** a classe deixa de aparecer nas propostas enquanto houver regra ativa

## Cenário: nível não excede o teto da classe
**WHEN** o gestor tenta aceitar com `nivel` acima de `classe_decisao.nivel_maximo`
**THEN** a API responde `422`

## Cenário: silenciar remove a proposta
**WHEN** o gestor aciona `POST /v1/regras/propostas/silenciar {classe}`
**THEN** a classe é gravada como silenciada
**AND** não volta a aparecer em `/v1/regras/propostas`

## Cenário: desativar uma regra
**WHEN** o gestor aciona `POST /v1/regras/{id}/desativar`
**THEN** a regra fica `ativa = false`
**AND** novas capturas da classe deixam de ser roteadas por ela

## Cenário: isolamento por organização (INV-15)
**WHEN** dois tenants têm decisões
**THEN** a mineração de um nunca conta casos do outro
**AND** todo predicado carrega `org_id`
