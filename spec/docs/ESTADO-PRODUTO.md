# Estado verificável do produto

> Fotografia auditada em 2026-08-13. A fonte operacional continua sendo cada `tasks.md`; este
> documento impede que “há código” seja confundido com “funciona em uso real”.

## Vocabulário de estado

| Estado | Significado |
|---|---|
| **Especificado** | proposta, design, cenários e tarefas existem; código não é pressuposto |
| **Escrito** | código existe, mas a validação automatizada ou integrada ainda está incompleta |
| **Testado localmente** | cenários centrais possuem testes automatizados verdes no repositório aplicável |
| **Validado ao vivo** | jornada crítica foi exercida com provedor/dispositivo/ambiente real |
| **Adotado** | usuários reais repetiram a jornada e a métrica de resultado foi observada |

“Implementado” pode ser usado como descrição informal de **testado localmente**, mas nunca como
sinônimo de validado ao vivo ou adotado.

## Matriz por capacidade

| Capacidade | Estado verificável | Evidência / pendência principal |
|---|---|---|
| Núcleo: captura, triagem, trilha e autonomia N2 | **testado localmente** | instrumentação G2/G7 disponível; piloto real ainda aberto |
| Executor, fechamento e portal externo | **testado localmente** | falta medir queda de recobrança em uso real |
| Linktor e gateway de modelos | **testado localmente** | configuração/provedores e G9 precisam validação contratual e ao vivo |
| Radar, revisão, mineração e aprendizado | **testado localmente** | resultado de encolhimento ainda não adotado no piloto |
| Bloco, dossiê, consulta, esteira e portal de instância | **testado localmente** | profundidade funcional sem adoção real comprovada |
| Mobile offline | **testado localmente no repositório mobile** | dispositivo e jornada completa ainda pendentes |
| Voz e modo trajeto | **escrito** | corpus PT-BR, STT real e presença nativa pendentes |
| Acompanhamento de grupos | **testado localmente, parcial** | seleção real de grupos e contrato Linktor ainda pendentes |
| Repasse com notificação | **testado localmente, parcial** | destino nominal e retorno correlacionado em observação entregues; gate Linktor/e-mail, transição e opt-out abertos |
| Pedido estruturado de informação | **testado localmente, parcial** | WhatsApp externo: confirmação, outbox, prazo e retorno entregues; Linktor real, e-mail e pessoa interna abertos |
| Prazos úteis e lembretes por exceção | **testado localmente** | calendário, 50/90 úteis, preferência e resumo agregado por e-mail entregues; Linktor ao vivo/WhatsApp interno abertos |
| Lembrete datado e Google Calendar | **testado localmente, parcial** | consentimento/evento ao vivo e configuração de deploy pendentes |

## Jornadas por persona

| Persona | Jornada disponível | Lacuna antes de adoção |
|---|---|---|
| Gestor | Entrada, Hoje, 4R, adiar, bloco, radar, revisão, regras e pedido de informação | validar se pedidos reduzem cobrança manual; G2/G7 sem dado real |
| Executor | receber, propor, concluir e devolver delegação | retorno pelo canal e UX real ainda não validados |
| Solicitante | fechamento no canal e consulta por portal | medir se parou de cobrar por fora |
| Contraparte externa | aviso/estado sem conta; pedido e resposta correlacionada | validar ida/volta Linktor real e ampliar para e-mail |
| Admin | fontes, grupos, contatos e integrações parciais | saúde da captura e seleção de grupo incompletas |

## Dívidas que afetam o piloto

1. G2/G7 ainda precisam de duas semanas de uso real, apesar da instrumentação disponível;
2. respostas externas já são correlacionadas localmente em observação, mas ainda não fecham o ciclo nem foram validadas na Linktor;
3. calendário Google não foi validado com credenciais e consentimento reais;
4. seleção/listagem de grupos depende de capacidade pendente no Linktor;
5. documentação de deploy ainda precisa refletir as variáveis de calendário.
6. pedido de informação está validado apenas com adaptador local; falta jornada real na Linktor.

## Regra de atualização

Todo pacote deve atualizar esta matriz quando mudar de estado. A passagem para **validado ao vivo**
exige registrar ambiente, data e jornada exercida no `tasks.md`; a passagem para **adotado** exige
amostra de uso e métrica de resultado, não apenas uma demonstração.
