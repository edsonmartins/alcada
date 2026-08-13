# Spec — 029 repasse para destino reconhecível

## C1 — busca unifica equipe e contatos
**WHEN** o gestor busca um nome presente como pessoa e contato
**THEN** recebe ambos, identificados por tipo e canal/detalhe
**AND** o sistema não escolhe entre homônimos.

## C2 — busca ignora acento e casa prefixo de palavra
**WHEN** a busca varia acento ou usa o primeiro nome
**THEN** encontra os destinos correspondentes do próprio tenant
**AND** termo vazio não devolve todo o diretório.

## C3 — isolamento por organização
**WHEN** pessoa ou contato homônimo existe em outro tenant
**THEN** não aparece nem pode ser usado por ID direto.

## C4 — resultado não expõe PII desnecessária
**WHEN** um contato externo aparece na busca
**THEN** endereço é mascarado e canal é visível
**AND** o endereço não atravessa o gateway de modelos.

## C5 — recentes são pessoais e determinísticos
**WHEN** a busca está vazia
**THEN** retorna até 8 destinos recentes daquele gestor
**AND** não usa métrica de desempenho para ordenar.

## C6 — Classe influencia sugestão, não escolha
**WHEN** o gestor repassou a mesma Classe ao mesmo destino anteriormente
**THEN** nível e prazo comparáveis podem ser sugeridos
**AND** o destino nunca é selecionado automaticamente.

## C7 — limite da Classe prevalece
**WHEN** o último contrato usou nível acima do máximo atualmente permitido
**THEN** a sugestão é limitada à política vigente
**AND** a UI explica o limite.

## C8 — sem base não inventa prazo
**WHEN** não existe contrato comparável nem política de horário suficiente
**THEN** prazo fica sem sugestão e deve ser escolhido pelo humano.

## C9 — repasse interno tipado
**WHEN** o corpo traz `destino INTERNO` válido, nível e prazo
**THEN** cria delegação interna e registra a trilha como hoje
**AND** não enfileira aviso externo.

## C10 — repasse externo conhecido
**WHEN** o corpo traz `destino EXTERNO` válido
**THEN** cria delegação externa e aviso idempotente conforme 025.

## C11 — contato novo é consequência do repasse
**WHEN** o gestor informa um contato externo novo válido
**THEN** contato e delegação são gravados atomicamente
**AND** o contato não precisa ser cadastrado antes em outra tela.

## C12 — contato equivalente é reutilizado
**WHEN** já existe contato do tenant com canal e endereço normalizados equivalentes
**THEN** o repasse reutiliza o contato existente
**AND** não cria duplicata.

## C13 — falha não deixa contato órfão
**WHEN** a delegação falha depois da validação do contato novo
**THEN** a transação inteira é revertida e nenhum contato isolado permanece.

## C14 — contrato antigo continua aceito
**WHEN** cliente existente envia `{donoId,nivel,prazo}`
**THEN** o servidor normaliza como destino interno
**AND** produz o mesmo efeito anterior durante a janela de compatibilidade.

## C15 — web não exibe identificador técnico
**WHEN** o gestor abre o formulário de repasse
**THEN** pesquisa e seleciona por identidade reconhecível
**AND** UUID não aparece como campo ou instrução.

## C16 — fluxo é operável por teclado
**WHEN** o usuário usa somente teclado
**THEN** consegue buscar, navegar, selecionar, ajustar o contrato e confirmar
**AND** foco retorna ao item correto após concluir ou cancelar.

## C17 — sugestão exige confirmação
**WHEN** destino, nível ou prazo foram sugeridos
**THEN** a transição só ocorre após confirmação humana explícita
**AND** a trilha registra o contrato confirmado, não a sugestão como decisão.

## C18 — sem vigilância
**WHEN** destinos são listados
**THEN** não há carga, ranking, velocidade, taxa de conclusão ou comparação individual.
