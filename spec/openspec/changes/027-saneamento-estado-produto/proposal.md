# 027 — Saneamento do estado do produto

## Por quê

README, roadmap e índice OpenSpec descreviam F3/F4 como planejadas embora código e testes já
existissem, omitiam os pacotes 024–026 e misturavam implementação local com validação ao vivo. Essa
ambiguidade induz decisões erradas de roadmap e pode transformar dívida de integração em promessa.

## O quê

- vocabulário verificável de maturidade;
- matriz de capacidades e jornadas por persona;
- correção dos status e tarefas comprovadamente defasados;
- inclusão dos pacotes recentes e do próximo ciclo no índice;
- vínculo explícito entre roadmap implementado e gates de adoção.

## Fora de escopo

- alterar código funcional;
- declarar G2/G7 encerrados;
- marcar integração como validada sem teste real;
- reescrever a história dos pacotes: tarefas externas ou adiadas continuam visíveis.

## Critério de aceite

Uma pessoa nova distingue, sem consultar o código, o que está especificado, escrito, testado
localmente, validado ao vivo e adotado, e encontra a tarefa aberta que impede a próxima promoção.
