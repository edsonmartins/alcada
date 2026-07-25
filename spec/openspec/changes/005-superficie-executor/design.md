# Design — 005 superfície do executor

Referências: `adr/ADR-0013-multi-ator-e-portal-externo.md`, `rfc/RFC-0002-motor-de-autonomia.md`.

## Módulos
`autonomia` (transições `concluir`/`devolver` no motor + endpoints) e o app **web** (tela do
executor). Reusa a máquina de delegação do 002 — não há tabela nova.

## Transições (complementam a máquina do 002)
```
ABERTA|PROPOSTA ─ concluir ─► EXECUTADA   (pendência FECHADA · trilha EXECUTADA · avisa solicitante)
ABERTA|PROPOSTA ─ devolver ─► DEVOLVIDA   (pendência ENTRADA · trilha DEVOLVIDA_PELO_EXECUTOR)
```

**`concluir` é imediato, sem janela — e isso é deliberado.** A janela de reversibilidade (INV-14)
existe para proteger contra execução que a pessoa **não escolheu** — o caso da ausência, onde o
silêncio virou ação. Quando o executor conclui, houve ato deliberado de quem tem alçada; não há o que
proteger, e abrir janela seria cerimônia sem risco. "Imediato" é sobre **não haver janela de espera**,
não sobre chamar o mundo externo no request: o efeito de `concluir` sai pelo **outbox** como todo
efeito externo. Se o gestor quiser reverter depois de concluído, o caminho é o mesmo do item executado
por ausência — **abrir pendência de reversão**, não `desfazer`.

**`devolver` usa `DEVOLVIDA_PELO_EXECUTOR` (ADR-0024), nunca `ESCALADA`.** São opostos: `ESCALADA` é
"ninguém agiu"; devolução é "o executor agiu e recusou". Reusar contaminaria o radar. `devolver` não
emite efeito externo de execução.

## API
```
GET    /v1/delegacoes                      # escopado ao executor autenticado (fronteira de autorização)
POST   /v1/delegacoes/{id}/propor          # já existe (002)
POST   /v1/delegacoes/{id}/concluir        { resultado }
POST   /v1/delegacoes/{id}/devolver        { motivo }
```
Autorização por dono: `concluir`/`devolver`/`propor` exigem `dono_id == pessoa autenticada` (senão
`403`).

**`GET /v1/delegacoes` é fronteira de autorização, não filtro de conveniência.** Retorna as delegações
onde `dono = pessoa do contexto`, e ponto. A visão do gestor (todas do tenant) é **rota à parte com
verificação de papel** — nunca o mesmo endpoint com `?todos=true`, que é onde vaza dado entre pessoas
se alguém esquecer de checar. O isolamento do INV-15 vale aqui também: executor de uma org nunca vê
delegação de outra, mesmo com o id na mão.

## Contrato visível (o que a tela mostra)
Por delegação: proposta, `nivel`, `prazo`, `janela` e a explicação do silêncio —
- executor propôs + gestor silencia → **executa por ausência** ao fim da janela;
- ninguém age → **escala** ao gestor (não executa em branco).

Efeitos via outbox: `concluir` → `delegacao.executada` + `item.fechado` (avisa solicitante);
`devolver` → `delegacao.devolvida` (avisa gestor).

**INV-09 — o aviso ao solicitante fica pela metade aqui, de propósito.** O `concluir` produz o efeito
`item.fechado` no outbox, mas a **entrega no canal de origem via Linktor** (o que faz o Rafael saber
que o desconto dele saiu) é do **pacote 006 (fechamento-canal-origem)**. Este pacote entrega o evento;
o 006 o transforma em mensagem no canal. Declarado explícito para não virar um INV-09 pela metade que
ninguém percebeu que ficou incompleto.

## Web
Nova rota **/executor**: lista as delegações do usuário (TanStack Query em `GET /v1/delegacoes`), cada
uma com o contrato e as ações. Formulários `propor`/`concluir`/`devolver` (RHF + Zod). Mesma stack do
003 (React 19 + Mantine 9).

## Decisões (fechadas na revisão)
1. `devolver` → **`DEVOLVIDA_PELO_EXECUTOR`** (ADR-0024). Não reusar `ESCALADA`.
2. `concluir` **imediato, sem janela**; efeito via outbox; reversão pós-conclusão = nova pendência.
3. `GET /v1/delegacoes` **escopado ao executor como fronteira de autorização**; gestor em rota à parte.
4. Aviso ao solicitante no canal (INV-09) = efeito de outbox aqui; **entrega via Linktor é do 006**.

## Riscos
Autorização multi-ator amplia a superfície (ADR-0013): o executor não pode ver deliberação de outro,
nem delegação que não é dele. Teste de isolamento por dono, além do isolamento por org (INV-15).
