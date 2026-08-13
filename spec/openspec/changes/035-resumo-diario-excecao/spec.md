# Spec — 035 resumo diário por exceção

## C1 — resumo reúne somente categorias acionáveis
**WHEN** o usuário possui Hoje, N2 em janela, retorno observado e escalonamento aberto
**THEN** um envelope contém as quatro seções e cada exemplo aponta para uma ação canônica.

## C2 — Hoje respeita o teto de três
**WHEN** mais de três Pendências qualificam para Hoje
**THEN** o resumo contém exatamente as três da ordenação canônica.

## C3 — categoria extensa não vira fila paralela
**WHEN** uma categoria de exceção possui mais de três ocorrências
**THEN** o resumo mostra três exemplos, o total real e um único link para continuar no produto.

## C4 — N2 usa a execução real
**WHEN** uma delegação aguarda o fim da janela e seu job de virada vence antes do próximo período
**THEN** ela aparece com o instante de execução; N2 apenas aberta ou proposta não aparece.

## C5 — retorno exige decisão pendente
**WHEN** retorno está OBSERVADO e sua Pendência não está fechada
**THEN** ele aparece; depois de APLICADO ou REJEITADO deixa de aparecer.

## C6 — deliberação de retorno é humana e idempotente
**WHEN** o gestor aplica ou rejeita um retorno com a mesma chave
**THEN** há uma transição e um evento RETORNO_AVALIADO; decisão divergente posterior recebe 409.

## C7 — aplicar retorno não executa efeito
**WHEN** o gestor marca um retorno como APLICADO
**THEN** nenhuma Saída, mudança de Pendência ou efeito externo é inferido.

## C8 — escalonamento absorvido não reaparece
**WHEN** houve ESCALADA mas uma Saída humana posterior retirou a Pendência da Entrada
**THEN** o escalonamento não integra o próximo envelope.

## C9 — estimativa usa mediana histórica
**WHEN** existem ao menos cinco intervalos válidos do próprio usuário
**THEN** a estimativa usa a mediana, a quantidade distinta e arredondamento de cinco minutos.

## C10 — histórico insuficiente não inventa estimativa
**WHEN** existem menos de cinco intervalos válidos
**THEN** o resumo omite a estimativa sem pedir pontuação manual.

## C11 — período é deduplicado sob concorrência
**WHEN** dois workers geram o mesmo usuário, período e data local simultaneamente
**THEN** existe um retrato e uma mensagem lógica no máximo.

## C12 — retrato vazio é silêncio
**WHEN** nenhuma categoria possui item
**THEN** não existe outbox, ainda que haja histórico para estimativa.

## C13 — nenhuma mensagem por entrada individual
**WHEN** várias Pendências entram ou vários retornos chegam
**THEN** o P035 não publica mensagem individual por ocorrência.

## C14 — tenant e usuário são isolados
**WHEN** organizações ou gestores diferentes possuem exceções
**THEN** conteúdo, estimativa, decisão e destinatário não atravessam seus escopos.

## C15 — estado pode mudar depois do retrato
**WHEN** uma Pendência é resolvida após a geração e antes do clique
**THEN** o resumo não é reenviado e a ação canônica responde com o estado atual sem repetir efeito.

## C16 — legado continua entregável
**WHEN** o despachante recebe uma mensagem textual do P032 criada antes da migração
**THEN** entrega pelo mesmo gate Linktor sem exigir o novo retrato.
