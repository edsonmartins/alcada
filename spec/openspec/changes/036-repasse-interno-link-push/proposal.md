# Proposal — 036 repasse interno com link e toque dirigido

## Problema

O executor interno recebe a delegação na fila do Alçada, mas pode não vê-la a tempo. O gestor volta
a avisá-lo por fora e o repasse novamente se divide em dois trabalhos. Um aviso genérico para cada
item da Entrada seria ruído e contrariaria a atenção por exceção.

## Resultado

Um repasse dirigido a uma pessoa interna produz, depois da janela de reversibilidade, um reforço
idempotente: link canônico que abre a Pendência no Alçada e push nos dispositivos daquela pessoa.
WhatsApp é opcional e só existe quando a própria pessoa cadastrou um endereço válido.

## Fora da fatia

- push para itens genéricos da Entrada;
- envio antes da janela ou durante trajeto não liberado;
- descoberta de telefone pela agenda de outra pessoa;
- campanhas, broadcast, ranking ou confirmação automática pelo clique;
- ativação de FCM/APNs sem credenciais oficiais dos respectivos projetos.
