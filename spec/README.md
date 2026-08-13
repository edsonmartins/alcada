# Alçada — corpus de especificação

> Plano de controle de decisões para gestores. `alcada.app` · nome decidido em ADR-0022.

## Como navegar

| Ordem | Documento | Para quê |
|---|---|---|
| 0 | `CLAUDE.md` | regras permanentes para o Claude Code |
| 0 | `PROMPT-KICKOFF.md` | prompts de abertura do projeto, em ordem |
| 1 | `CONSTITUTION.md` | invariantes. Nada abaixo pode contradizer |
| 2 | `docs/METODO.md` | a metodologia — ativo publicável, funciona no papel |
| 3 | `docs/PRODUTO.md` | personas, superfícies, domínio, roadmap, riscos |
| 3 | `docs/PLANO-IMPLEMENTACAO.md` | ondas, dependências, gates e ordem das próximas entregas |
| 3 | `docs/ESTADO-PRODUTO.md` | fotografia verificável: escrito, testado, validado e adotado |
| 4 | `adr/` | 23 decisões de arquitetura |
| 5 | `rfc/` | 7 desenhos técnicos das partes difíceis |
| 6 | `docs/API.md` · `docs/WEB.md` · `docs/MOBILE.md` | superfícies |
| 7 | `openspec/` | pacotes de execução para Claude Code |
| 8 | `docs/DECISOES-ABERTAS.md` | 7 gates que condicionam o resto |

## Leitura mínima por papel
- **Cliente / piloto:** `docs/METODO.md`
- **Sócio / comercial:** `docs/PRODUTO.md` §1–3, §8–10
- **Dev entrando no projeto:** `CONSTITUTION.md` → `docs/GLOSSARIO.md` → ADRs → pacote OpenSpec da vez
- **Claude Code:** `CLAUDE.md`, depois `PROMPT-KICKOFF.md`

## Índice de ADRs
| # | Decisão |
|---|---|
| 0001 | Pendência como unidade atômica |
| 0002 | Quatro saídas fixas e adiamento de primeira classe |
| 0003 | Três níveis de autonomia como motor |
| 0004 | Silêncio como aprovação em N2 |
| 0005 | Captura passiva; o gestor nunca cadastra |
| 0006 | Classe do item define o roteamento padrão |
| 0007 | Recobrança não cria item; aumenta temperatura |
| 0008 | Três horizontes; faixa trimestral blindada |
| 0009 | LLM propõe, código determinístico executa |
| 0010 | Inferência local para conteúdo sensível |
| 0011 | LGPD na captura de canais |
| 0012 | Esteira como agregado com checklist versionado |
| 0013 | Produto multi-ator; portal externo sem login |
| 0014 | Canal de voz: offline-first e recusa por classe |
| 0015 | Stack e arquitetura de execução |
| 0016 | Trilha imutável como prova |
| 0017 | Métrica comportamental só com ação associada |
| 0018 | A interface empurra; não oferece jardim |
| 0019 | Assistente situado, não chat onipresente |
| 0020 | OpenRouter como gateway de modelos (emenda o INV-12) |
| 0021 | Linktor como camada única de canais |
| 0022 | Nome do produto: Alçada |
| 0023 | Stack: Quarkus, Postgres único, React sem Archbase (substitui 0015) |
| 0029 | Correlação explícita do retorno pelo canal; nenhuma heurística de identidade |

## Estado
Corpus fundacional e diversos pacotes estão implementados, mas validação técnica e adoção são
estados distintos. Consulte `docs/ESTADO-PRODUTO.md` e o índice em `openspec/README.md`.
**G2 é bloqueante** — sem aceitação de N2 pelo gestor-piloto, o roadmap muda.
