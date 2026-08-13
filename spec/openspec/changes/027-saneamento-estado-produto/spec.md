# Spec — 027 saneamento do estado do produto

## Cenário: implementação não significa adoção
**WHEN** uma capacidade possui código e testes, mas não foi usada no piloto
**THEN** aparece como `testado localmente`, nunca como `adotado`
**AND** sua validação pendente é nomeada.

## Cenário: roadmap reflete o repositório
**WHEN** F3 e F4 possuem componentes e testes no repositório
**THEN** README não as apresenta como planejadas
**AND** distingue entrega técnica de validação ao vivo.

## Cenário: tarefa comprovadamente entregue
**WHEN** código e teste rastreável comprovam uma tarefa ainda aberta
**THEN** a tarefa é marcada como concluída
**AND** tarefas dependentes de ambiente externo permanecem abertas.

## Cenário: próximo ciclo é navegável
**WHEN** alguém consulta o índice OpenSpec
**THEN** encontra os pacotes 024–029 e seus estados honestos
**AND** chega ao plano mestre e à fotografia verificável.
