# Spec — 028 instrumentação do piloto

## C1 — relatório separa os desfechos de N2
**WHEN** o facilitador consulta o período do piloto
**THEN** propostas, execuções por ausência, intervenções, devoluções, escalonamentos e reversões são
contadas separadamente
**AND** a resposta não produz um score de gestor.

## C2 — motivo de intervenção é opcional e fechado
**WHEN** o gestor interrompe uma delegação N2
**THEN** pode informar um motivo do vocabulário fechado e observação opcional
**AND** omitir o motivo não impede a intervenção
**AND** o registro é append-only e ligado à trilha.

## C3 — motivo não cruza tenant nem modelo
**WHEN** há motivos em duas organizações
**THEN** cada relatório lê apenas sua organização
**AND** observação nenhuma é enviada ao gateway de modelos.

## C4 — amostra respeita retenção
**WHEN** o facilitador solicita uma amostra de descartes
**THEN** somente descartes ainda retidos e da própria organização são elegíveis
**AND** a mesma semente e período produzem a mesma seleção
**AND** conteúdo expirado não pode ser reconstruído.

## C5 — avaliação de descarte é append-only
**WHEN** um descarte é avaliado como `ERA_PENDENCIA`, `NAO_ERA` ou `INCONCLUSIVO`
**THEN** a avaliação registra ator e timestamp sem alterar o descarte original.

## C6 — escape é piso, não recall completo
**WHEN** o relatório calcula a taxa de escape
**THEN** apresenta `escapes / (capturados + escapes)` como piso de misses conhecidos
**AND** não o rotula como recall.

## C7 — estimativa informa incerteza
**WHEN** existem descartes auditados
**THEN** o relatório mostra tamanho da amostra, inconclusivos e incerteza
**AND** não combina as quatro fontes por média simples.

## C8 — reconciliação não cria item escondido
**WHEN** o gestor informa decisões que ocorreram fora da fila
**THEN** o registro entra na evidência do piloto
**AND** nenhuma Pendência é criada automaticamente.

## C9 — saúde de Fonte vem com ação
**WHEN** uma Fonte fica sem evento além do limiar configurado
**THEN** a saúde a marca como silenciosa e oferece testar ou revisar configuração
**AND** não acusa o gestor nem membros do canal.

## C10 — G2 não é decidido pelo código
**WHEN** o relatório contém ao menos uma execução por ausência
**THEN** apresenta a evidência e a entrevista pendente
**AND** não declara G2 aprovado automaticamente.

## C11 — período e completude são explícitos
**WHEN** o relatório é gerado
**THEN** inclui início, fim, Fontes observadas e lacunas de coleta
**AND** recusa período inválido em problem+json.

## C12 — acesso é restrito
**WHEN** usuário sem papel permitido acessa `/v1/piloto/*`
**THEN** recebe 403 sem contagens nem conteúdo amostral.
