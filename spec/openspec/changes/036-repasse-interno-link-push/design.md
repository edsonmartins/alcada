# Design — 036 repasse interno com link e toque dirigido

## Ordem

1. link canônico e roteamento Android/iOS/web;
2. identidade de WhatsApp declarada pelo próprio usuário;
3. registro revogável de dispositivo por tenant e pessoa;
4. outbox de reforço interno após a janela;
5. adaptadores FCM/APNs e Linktor, cada qual degradável de forma independente;
6. observabilidade, isolamento e jornada em aparelhos reais.

## Segurança

O link contém apenas o UUID opaco da Pendência. Abrir o recurso sempre exige sessão e a API aplica
`org_id` e `pessoa_id`; conhecer o UUID não concede acesso. Tokens push são PII operacional,
criptografados em repouso, revogados no logout e nunca registrados em log ou Trilha.

## Atenção e reversibilidade

O evento é uma delegação destinada à pessoa, não “novo item na Entrada”. O reforço nasce no mesmo
outbox da delegação e só fica entregável depois da janela de desfazer e da liberação do trajeto.
Reprocessamento usa a delegação e o canal como chave idempotente.

## Degradação

A fila interna é a fonte de verdade. Sem telefone, token, FCM ou APNs, a delegação continua válida;
o sistema registra o canal indisponível sem transformar ausência de push em falha do repasse.
