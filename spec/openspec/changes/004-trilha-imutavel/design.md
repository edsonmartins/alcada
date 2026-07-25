# Design — 004 trilha imutável

Referência: `adr/ADR-0016-trilha-imutavel.md` e seu **anexo normativo** (vocabulário fechado).

## Módulos
`plataforma.trilha` — porta de escrita consumida por todos os módulos de domínio; ninguém escreve na
tabela diretamente. Leitura por `metricas`/API via porta de consulta.

## Tabela
```sql
trilha(
  id, org_id, pendencia_id, tipo, ator, ocorrido_em,
  origem jsonb, estado_anterior, estado_posterior, carga jsonb
) PARTITION BY RANGE (ocorrido_em)          -- mensal; PK (id, ocorrido_em)
```
- `CHECK` de `tipo` espelha os **29 tipos** do anexo; `CHECK` de `ator` valida o formato.
- Append-only garantido em dois níveis: `TRIGGER BEFORE UPDATE OR DELETE` (propaga às partições) +
  `REVOKE UPDATE, DELETE, TRUNCATE` do role da aplicação.
- Partições mensais + uma partição `DEFAULT` (rede de segurança). A rolagem cria a partição do mês
  seguinte antecipadamente, com a `DEFAULT` vazia no momento da criação.

> A tabela, o enum, o formato de ator, o trigger e o escritor **já foram entregues no bootstrap
> (Sessão 2)** e têm teste. Este pacote formaliza o contrato e acrescenta rolagem, compensação,
> consulta e LGPD.

## Formato do ator (anexo ADR-0016)
```
HUMANO:{pessoa_id}
SISTEMA:{motor|regra}:{identificador}
ASSISTENTE:{modelo}@{versao}
```

## Carga por evento
Todos: `pendencia_id, tipo, ator, ocorrido_em, origem, estado_anterior, estado_posterior`.
- `EXECUTADA_POR_AUSENCIA` acrescenta `delegacao_id, prazo, proposta, janela, intervencoes[]`.
- `SUGESTAO_*` acrescenta `tarefa, modelo, desfecho`.
- `COMPENSACAO` acrescenta `evento_compensado_id` e o motivo.

## Contratos
- **Escrita participa da transação do chamador.** Transição de estado e escrita da trilha na mesma
  transação — se a transação reverte, o evento não existe.
- **Compensação é o único mecanismo de correção.** Não há caminho de `UPDATE`. Um erro registrado é
  corrigido por um novo evento `COMPENSACAO` que referencia o original.
- **Sem identificador direto na trilha.** `origem` e `carga` referenciam por id (`pessoa_id`,
  `mensagem_id`), nunca nome, CPF/CNPJ, telefone ou e-mail. A eliminação LGPD pseudonimiza o registro
  do titular em `identidade`; as referências na trilha continuam válidas e a cadeia intacta.
- **Rolagem de partição** é job do scheduler persistente (SISTEMA), idempotente por mês.

## Consulta
`GET /v1/pendencias/{id}/trilha` — eventos da pendência em ordem cronológica, sempre filtrado por
`org_id` (INV-15).

## Riscos técnicos
Volume de escrita e custo de armazenamento (ADR-0016): particionamento mensal obrigatório e
arquivamento frio previsto para fase posterior. Rolagem que atrase deixa linhas caírem na `DEFAULT` —
o job precisa de folga (cria com meses de antecedência) e alerta se a `DEFAULT` receber linhas.
