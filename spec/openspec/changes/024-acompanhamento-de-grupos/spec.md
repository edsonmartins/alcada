# Cenários — Acompanhamento de grupos

> Cada cenário vira ao menos um teste automatizado com o mesmo nome (CLAUDE.md §6/§7).

## C1 — reunião negociada num grupo vira compromisso (caso real do Marcello)
- **WHEN** um grupo declarado troca, ao longo do dia, as mensagens: proposta de
  reunião sobre "cronograma atualizado e próximos passos", recusa das datas da
  semana, acordo em "próxima segunda 14h", e "Manda invite" + e-mail do Marcello
- **THEN** o extrator propõe **um** compromisso: `tipo=REUNIAO`,
  `assunto≈"cronograma e próximos passos"`, `quando.resolvido=<próxima segunda 14:00 no fuso do tenant>`,
  `quemPede`/dono = Marcello (**primeiro nome**), `acaoPendente≈"enviar invite"`,
  `possivelmenteFeito=true` (houve "enviei!"), e vira **uma** pendência na Entrada.

## C2 — ruído puro não vira nada e não vai ao modelo
- **WHEN** chegam mensagens sem pedido/decisão ("Bom dia!", "obrigada", figurinha)
- **THEN** o pré-filtro as descarta como não-candidatas; **nenhuma chamada ao
  modelo** é feita para elas; a proporção processada é registrada no log de auditoria.

## C3 — só entra o que depende do gestor
- **WHEN** a janela contém um assunto que se resolve entre terceiros, sem depender
  de decisão/ação do gestor
- **THEN** o modelo retorna `dependeDoGestor=false` e **nada** é criado na Entrada.

## C4 — cobrança funde e escala (não duplica)
- **WHEN** existe uma pendência aberta de um grupo e chega novo trecho cobrando o
  mesmo assunto ("e aí, decidiu?", "estamos esperando")
- **THEN** o novo trecho **funde** na pendência existente (FUNDIDA), incrementa o
  contador de cobrança e, ao passar o limiar, marca **ESCALADA** com "já te
  cobraram Nx" — sem criar item novo.

## C5 — menção direta ao gestor fura o debounce
- **WHEN** o gestor é **mencionado** explicitamente (`mentions`) pedindo decisão
- **THEN** a avaliação ocorre imediatamente (sem esperar a conversa esfriar).

## C6 — bot visível é pré-condição da captura (ADR-0011 §2)
- **WHEN** uma fonte-grupo está cadastrada mas **sem** o aviso fixado ativo
- **THEN** nenhuma mensagem do grupo é capturada/processada até o aviso ser publicado.

## C7 — minimizador não vaza identificador direto (ADR-0020 §3)
- **WHEN** uma janela é montada para o modelo
- **THEN** nenhum identificador direto (nome completo, telefone, e-mail) atravessa
  o minimizador; a saída é re-hidratada localmente para **primeiro nome**.

## C8 — identidade mínima por finalidade (emenda ADR-0011)
- **WHEN** o compromisso é apresentado ao gestor
- **THEN** terceiros aparecem por **primeiro nome** por padrão; contato completo
  (e-mail/telefone) só quando **é a própria ação** (ex.: e-mail dado no fio para o
  invite); quando não há nome (só número), usa-se o número ou o papel, sem inventar nome.

## C9 — idempotência (reprocesso não duplica)
- **WHEN** a mesma mensagem (`message.id`) é reentregue, ou a mesma janela é
  reavaliada
- **THEN** não há duplicação: captura vira no-op limpo (200) e a extração funde
  pelo `sourceMessageIds`.

## C10 — isolamento multi-tenant (INV-15)
- **WHEN** dois grupos de organizações diferentes têm assuntos parecidos
- **THEN** nenhum item, aprendizado de pré-filtro ou fato cruza `org_id`.

## C11 — retenção do bruto ≤ 30 dias (ADR-0011 §4)
- **WHEN** passam mais de 30 dias da captura de uma janela bruta
- **THEN** o bruto expira; permanece apenas o fato derivado (a pendência e seus campos).

## C12 — trilha registra o ator do assistente (INV-11)
- **WHEN** o extrator cria/funde uma pendência
- **THEN** o evento da trilha tem ator `ASSISTENTE:{modelo,versão}` e é append-only
  (correção só por compensação).

## C13 — só grupos selecionados são acompanhados (opt-in, ADR-0011 §1)
- **WHEN** chega uma mensagem de um grupo que o gestor **não** selecionou (sem
  fonte-grupo ativa)
- **THEN** ela é **descartada** (não vira evento bruto, não é processada) — só os
  grupos que ele escolheu controlar entram.
