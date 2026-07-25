# Superfície Web

**Stack:** React 19 + TypeScript + Mantine v9 + Vite. SPA pura, sem SSR, sem Archbase (ADR-0023).

| Necessidade | Biblioteca |
|---|---|
| Estado de servidor | TanStack Query — cache, invalidação, mutação otimista (janela de desfazer) |
| Estado de UI | Zustand |
| Rotas | TanStack Router |
| Formulários | React Hook Form + Zod |

Referência visual e de interação: `prototipo/alcada-sistema.html`.

## Rotas por ator

### Gestor
| Rota | Tela | Observações |
|---|---|---|
| `/entrada` | triagem | um item por vez ou lista; teclado obrigatório |
| `/hoje` | três itens + em andamento | máximo 3, com justificativa |
| `/delegados` | N1/N2/N3 com prazo vivo | contagem regressiva visível |
| `/blocos` | agendados | acesso ao workspace |
| `/blocos/:id` | **workspace de decisão** | dossiê · opções · redação |
| `/itens` | consulta completa | filtros e busca |
| `/esteira` | quadro de instâncias | por etapa, com SLA |
| `/alcadas` | regras e propostas | evidência navegável |
| `/radar` | diagnóstico | métrica sempre com ação (ADR-0017) |
| `/sexta` | revisão conduzida | roteiro sequencial de 20 min |

### Executor
`/meus-itens` — delegações com nível, prazo e ações (propor, concluir, devolver).

### Admin
`/config/fontes` · `/config/classes` · `/config/pessoas` · `/config/retencao`

## Workspace de decisão (tela mais valiosa)
Três colunas:
1. **Dossiê** — contexto agregado + perguntas ancoradas com fonte clicável
2. **Opções** — checklist quando aplicável, alternativas com **consequência explícita**
3. **Comunicação** — rascunho editável, variação de tom, ação única "decidir e comunicar"

Ao concluir: fecha o item, comunica no canal de origem, registra trilha e dispara **uma** pergunta
de aprendizado.

## Teclado (não é acessório)
```
j / k        navegar          Enter    abrir
1 2 3 4      Resolver / Repassar / Reservar / Repousar
a            adiar            /        buscar
⌘K / Ctrl+K  paleta de comandos        Esc  fechar
```
Ação em lote por seleção múltipla é incentivada (ADR-0018); edição item a item de metadado, não.

## Restrições de interface (ADR-0018)
- sem arrastar cartão
- sem taxonomia customizável pelo usuário final
- toda tela oferece próxima ação
- sessão sem transição de estado é nomeada ao final

## Acessibilidade e desempenho
Navegação completa por teclado; foco visível; contraste AA. Primeira renderização de `/entrada`
abaixo de 1,5 s em conexão típica de escritório.
