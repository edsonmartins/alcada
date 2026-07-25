# RFC-0006 — Esteira, checklist versionado e portal externo

**Status:** proposto · **Implementa:** ADR-0012, ADR-0013

## Modelo

```
Esteira { id, nome, etapas[] }
Etapa   { ordem, nome, dono_papel, sla, checklist_id? }
Checklist { id, versao, criterios[] }
Criterio { chave, descricao, tipo: OBJETIVO|JULGAMENTO, obrigatorio }
Instancia { id, esteira_id, entidade_externa, etapa_atual, entrou_em, historico[] }
Avaliacao { instancia_id, checklist_versao, resultados[], desfecho, avaliador }
```

**Versionamento é obrigatório.** Avaliações antigas são lidas contra a versão vigente à época;
comparar decisão de junho com critério de agosto produz conclusão errada na mineração (RFC-0003).

## Regra de avanço
- todos os critérios `OBJETIVO` obrigatórios aprovados **e** nenhum `JULGAMENTO` pendente → avança
  sem gerar pendência para o gestor
- qualquer falha ou julgamento pendente → gera pendência **com o resultado da avaliação anexado**,
  não com a submissão crua

## Autoavaliação pela contraparte
A contraparte recebe o checklist objetivo antes de submeter e declara conformidade. Isso elimina
boa parte das idas e vindas — é a economia mais direta do módulo.

## Portal externo
Acesso por **link assinado**, sem login:
- token com expiração, escopo por instância, revogável
- expõe: etapa atual, data de entrada, prazo previsto, **o que falta da contraparte**
- nunca expõe: deliberação interna, nome de decisores, histórico de outras contrapartes
- sem dado de terceiros; cabeçalhos de no-index

```
GET /p/{token}                 -> estado da instância
POST /p/{token}/autoavaliacao  -> declaração de conformidade
```

## SLA e custo de atraso
Cada etapa tem SLA. Instância acima do SLA na etapa do gestor entra na priorização com **custo de
atraso crescente** (o parceiro esfria, o comercial reprometeu, a receita não inicia).

## Métricas
- tempo médio por etapa e por instância
- % de instâncias que passam sem tocar o gestor (**alvo do módulo**)
- retrabalho por submissão (proxy da qualidade do checklist)
