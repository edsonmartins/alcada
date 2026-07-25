# Especificação de Produto — Alçada

## 1. Posicionamento

**Alçada é o plano de controle de decisões de um gestor.**

Para gestores de PME e média empresa que são ponto de bloqueio de muitos fluxos, Alçada captura
passivamente tudo que espera decisão deles, roteia o que não precisa deles e reduz mês a mês o
volume que precisa — ao contrário de gerenciadores de tarefa, que organizam melhor a mesma
sobrecarga.

**Frase de teste do produto:** se ao fim de 90 dias o número de itens que dependem do gestor não
caiu, o produto não entregou.

## 2. Personas

### P1 — O gestor (usuário primário)
Diretor/gerente de PME, 15–30 pessoas sob influência direta. Participa de dezenas de fluxos como
portão de aprovação ou validação. Vive em WhatsApp e e-mail. Tem horas de desktop na empresa e
40–90 min/dia de trânsito. Não vai aprender ferramenta, não vai cadastrar, não vai arrastar cartão.
Reage a cobrança porque não tem fila única, não por indisciplina.

**Jobs:** parar de ser gargalo sem perder controle · saber o que importa hoje sem varrer nada ·
tirar do peito o item que ele adia há três meses · defender tempo de trabalho próprio.

### P2 — O executor (Carolina, Alexandre, Mayker)
Recebe delegação. Hoje fica bloqueado sem saber se pode agir. Precisa de contrato claro: o que
propus, até quando vale, o que acontece se ninguém responder.

**Jobs:** destravar sem cobrar · saber o limite da própria alçada · registrar o que fez.

### P3 — O solicitante (Rafael, RH, comercial)
Pediu algo e não sabe se foi visto. Cobra de novo, por outro canal — e isso infla o backlog
percebido.

**Jobs:** saber o estado do pedido sem cobrar · ser avisado quando sair.

### P4 — A contraparte externa (integrador, fornecedor)
Está esperando e **não tem como cobrar**: não está no grupo, não tem acesso. Cobra o comercial, que
cobra o time, que cobra o gestor. Cada salto adiciona ruído sem informação.

**Jobs:** ver estado e prazo previsto · saber exatamente o que falta dele.

### P5 — Admin da organização
Configura fontes de captura, políticas de retenção, tenants, integrações. Responsável por LGPD.

## 3. Superfícies

| Superfície | Papel | Momento |
|---|---|---|
| **Mobile** | canal principal de captura e despacho | trânsito, corredor, fora da mesa |
| **Web** | decisão profunda, revisão semanal, controle | mesa, blocos, sexta-feira |
| **Bots de canal** | captura e fechamento no canal de origem | onde o time já vive |
| **Portal externo** | estado para contraparte sem login | assíncrono |
| **API** | integração com ERP, monitoramento, esteira | contínuo |

### O que é exclusivo de cada uma

**Só no web:** bloco de decisão (dossiê + opções + redação), triagem por teclado, ação em lote,
revisão de sexta, radar, configuração de alçadas.

**Só no mobile:** captura por voz, modo trajeto (mãos-livres), despacho em sequência, offline.

**Nunca:** arrastar cartão, campo livre obrigatório, criação manual como caminho principal.

## 4. Modelo de domínio (resumo)

Detalhe completo em `DOMINIO.md`.

```
Organizacao 1─* Pessoa
Organizacao 1─* Fonte (canal declarado de captura)
Fonte 1─* EventoBruto ──extração──> Pendencia
Pendencia *─1 Classe {DECISAO, BLOQUEIO, ESTEIRA}
Pendencia *─1 Horizonte {HOJE, SEMANA, TRIMESTRE}
Pendencia 1─* Cobranca (temperatura)
Pendencia 1─* EntradaTrilha (append-only)
Pendencia 0─1 Delegacao {dono, nivel, prazo, janela}
Pendencia 0─1 Bloco (agendamento + dossiê + opções + rascunho)
Pendencia 0─1 Adiamento[] {volta_em, o_que_falta}
Regra (autonomia) *─1 Organizacao
Esteira 1─* Etapa 1─* Instancia (integrador em curso)
Esteira 1─1 Checklist (versionado)
```

## 5. Máquina de estados da pendência

```
                 ┌──────────► FECHADA ◄────────┐
                 │                             │
CAPTADA ─► ENTRADA ─┬─► DELEGADA ─┬─► FECHADA (executada / por ausência)
                    │             └─► ENTRADA (gestor interrompeu N2)
                    ├─► AGENDADA ────► FECHADA (decidida no bloco)
                    ├─► DORMINDO ────► ENTRADA (data de retorno ou cobrança)
                    └─► ENTRADA (adiada, com volta_em e o_que_falta)
```

### Dois níveis de estado (resolve ambiguidade apontada na revisão)

`AGUARDANDO_JANELA` **não é estado da Pendencia**. É sub-estado da `Delegacao`.

| Agregado | Estados |
|---|---|
| `Pendencia.status` | `ENTRADA` · `DELEGADA` · `AGENDADA` · `DORMINDO` · `FECHADA` |
| `Delegacao.status` | `ABERTA` · `PROPOSTA` · `AGUARDANDO_JANELA` · `EXECUTADA` · `DEVOLVIDA` · `ESCALADA` |

Motivo: uma pendência pode ser delegada, devolvida e redelegada. A janela pertence ao contrato
daquela delegação específica, não à pendência. O job de virada e o desfazer operam sobre
`delegacao_id`. A UI e as métricas leem `Pendencia.status`, que permanece grosso e estável.

Enquanto `Delegacao.status ∈ {ABERTA, PROPOSTA, AGUARDANDO_JANELA}`, a `Pendencia.status` é
`DELEGADA`. A transição para `EXECUTADA` fecha a pendência; `DEVOLVIDA` e `ESCALADA` a devolvem para
`ENTRADA`.

Regras invioláveis:
- `DELEGADA` com nível `N3` **conta como dependente do gestor** para efeito de métrica.
- `AGENDADA` conta como dependente do gestor.
- Só `FECHADA` notifica solicitante e contraparte.
- Transição para `FECHADA` por ausência exige janela de reversibilidade encerrada.

## 6. Priorização (função de ordenação)

Score não é exposto ao usuário. Componentes:

| Sinal | Peso | Fonte |
|---|---|---|
| dinheiro parado | alto | valor extraído + tipo de bloqueio |
| pessoas bloqueadas × tempo | alto | quem espera + idade |
| prazo duro | alto | data com consequência legal/contratual |
| custo de atraso crescente | médio | classe do item (esteira e negociação crescem) |
| temperatura | médio | cobranças distintas deduplicadas |
| adiamentos | baixo, negativo | contador — sobe para bloco, não para topo do hoje |

Saída do "Hoje": no máximo 3 itens. Nunca mais.

## 7. Superfícies do assistente

O assistente **não é chat onipresente**. É situado, com contexto pré-carregado:

| Momento | O que faz | Onde |
|---|---|---|
| **Dossiê** | responde perguntas ancoradas na base, com fonte | bloco de decisão |
| **Redação** | escreve o retorno da decisão, com variações de tom | bloco de decisão |
| **Aprendizado** | uma pergunta após a decisão, nunca duas | pós-decisão |
| **Consulta** | linguagem natural sobre a fila estruturada | web e voz |
| **Condução** | roteiro da revisão de sexta | web |

Proibido: campo de chat aberto na home; assistente que responde sem citar fonte; sugestão de autonomia
sem evidência clicável.

## 8. Roadmap por fases

| Fase | Escopo | Critério de saída |
|---|---|---|
| **F0 — papel** | método rodado 2 semanas com facilitador humano | gestor aceita N2; ritmo cabe |
| **F1 — núcleo** | captura WhatsApp + e-mail, triagem web, 4R + adiar, N1/N2/N3, trilha | fila única real; N2 executando |
| **F2 — contraparte** | tela do executor, fechamento no canal de origem, portal externo | solicitante para de cobrar |
| **F3 — encolhimento** | mineração de regras de autonomia, regras, radar, revisão de sexta | curva de dependência caindo |
| **F4 — profundidade** | bloco de decisão com dossiê e redação, esteira e checklist | item aversivo saindo |
| **F5 — mobilidade** | app Flutter, voz, modo trajeto, offline | trajeto virando decisão |

Ordem é deliberada: **contraparte antes de IA avançada**. N2 sem tela do executor é mecanismo sem
apoio.

## 9. Modelo de negócio (esboço)

- Preço por **gestor ativo**, não por assento total — executores e solicitantes entram sem custo,
  porque são o que faz o mecanismo funcionar.
- Portal externo incluído.
- Módulo esteira como add-on por processo definido.
- Métrica de valor contratual: redução de itens dependentes do gestor, medida e reportada.

## 10. Riscos de produto

| Risco | Severidade | Mitigação |
|---|---|---|
| Gestor não aceita N2 | **crítico** | validar em F0; sem isso o produto entrega ~30% |
| Captura passiva perde itens | crítico | F1 mede recall contra baseline manual |
| Vira ferramenta de vigilância | alto | INV-07; enquadramento de advogado; sem ranking |
| Gestor "cuida" da fila em vez de esvaziar | alto | sem arrastar, sem jardim; aviso de sessão improdutiva |
| Critério tácito não é objetivo | médio | esteira vira opcional; produto sobrevive sem ela |
| LGPD em captura de grupo | alto | ADR-0011; fontes declaradas; minimização |
