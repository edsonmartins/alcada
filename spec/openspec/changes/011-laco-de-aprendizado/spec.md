# Spec — 011 laço de aprendizado

## Cenário: candidata gera uma pergunta com evidência
**WHEN** existe uma classe candidata a regra (010) sem pergunta aberta e o teto semanal não foi atingido
**THEN** `GET /v1/aprendizado/perguntas` cria e retorna uma pergunta `ABERTA` para a classe
**AND** a pergunta traz a evidência (casos navegáveis, nível e dono sugeridos — ADR-0019)
**AND** a trilha do caso representativo registra `SUGESTAO_EMITIDA`

## Cenário: no máximo uma pergunta aberta por classe
**WHEN** já existe pergunta `ABERTA` para a classe
**THEN** uma nova chamada não cria outra pergunta para a mesma classe

## Cenário: teto de três por semana
**WHEN** já foram criadas 3 perguntas na semana corrente
**THEN** nenhuma pergunta nova é criada até a próxima semana

## Cenário: "sim" cria a regra (INV-10)
**WHEN** o gestor responde `SIM`
**THEN** uma `regra_autonomia` é criada com o nível e dono sugeridos
**AND** a pergunta vai para `ACEITA` e a trilha registra `SUGESTAO_ACEITA`
**AND** novas capturas da classe passam a ser roteadas por regra

## Cenário: "agora não" recusa sem silenciar
**WHEN** o gestor responde `AGORA_NAO`
**THEN** a pergunta vai para `RECUSADA` e a trilha registra `SUGESTAO_RECUSADA`
**AND** a classe não é re-perguntada na mesma semana
**AND** a proposta continua disponível em `/alcadas`

## Cenário: "não perguntar isso" silencia a classe
**WHEN** o gestor responde `NAO_PERGUNTAR`
**THEN** a pergunta vai para `SILENCIADA` e a trilha registra `SUGESTAO_SILENCIADA`
**AND** a classe é silenciada (010): não volta em perguntas nem em propostas

## Cenário: sem evidência não há pergunta
**WHEN** uma classe não atinge o limiar de mineração
**THEN** nenhuma pergunta é gerada para ela (proibido sugerir autonomia sem evidência, ADR-0019)

## Cenário: isolamento por organização (INV-15)
**WHEN** dois tenants têm padrões
**THEN** as perguntas de um nunca consideram casos ou classes do outro
**AND** todo predicado carrega `org_id`
