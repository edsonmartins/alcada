# Spec — 014 perguntas ao dossiê (índice híbrido)

## Cenário: recupera passagem por BM25 com fonte
**WHEN** existe uma passagem indexada que casa com a pergunta (full-text)
**THEN** `perguntar` retorna `encontrou=true` com a passagem em `fontes` (tipo + ref navegável)
**AND** a resposta cita a fonte (nunca responde sem fonte, RFC-0004)

## Cenário: sem passagem acima do limiar, não inventa
**WHEN** nenhuma passagem casa acima do limiar
**THEN** `perguntar` retorna `encontrou=false` e "não encontrei isso na base"
**AND** não sintetiza resposta de memória

## Cenário: caminho vetorial recupera por similaridade
**WHEN** há passagens com embedding e a query tem embedding
**THEN** a recuperação usa cosseno (`<=>`) e traz a passagem mais similar
**AND** funciona junto com o BM25 (híbrido)

## Cenário: sem embedding, cai em BM25 sem quebrar
**WHEN** as passagens não têm embedding (sem modelo)
**THEN** a recuperação usa só BM25 e ainda responde com fonte

## Cenário: síntese degrada com honestidade
**WHEN** o gateway de modelos não está disponível
**THEN** a resposta são as próprias passagens recuperadas (com fonte), sem falhar

## Cenário: isolamento por organização (INV-15)
**WHEN** dois tenants têm passagens
**THEN** a pergunta de um nunca recupera passagem do outro
**AND** todo predicado carrega `org_id`

## Cenário: indexação idempotente
**WHEN** uma pendência é indexada duas vezes
**THEN** não há passagens duplicadas para ela (reindexação substitui)
