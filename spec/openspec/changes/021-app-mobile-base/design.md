# Design — 021 app móvel base

## Contrato de sincronização
```
POST /v1/comandos          (lote idempotente)
Headers: X-Org-Id, X-Pessoa-Id   (mesmo contexto do web; OIDC quando houver)
Body: { comandos: [ Comando ] }

Comando = {
  comandoId: uuid,          // gerado no device, chave de idempotência
  intencao: RESOLVER | REPASSAR | RESERVAR | REPOUSAR | ADIAR | REGISTRAR | CONSULTAR,
  pendenciaId: uuid | null, // alvo (null para REGISTRAR/CONSULTAR)
  campos: { ... },          // por intenção (dono, nivel, prazo, volta_em, texto, pergunta)
  capturadoEm: iso8601      // quando o gestor falou/registrou (offline)
}

Resposta: { resultados: [ { comandoId, status: OK|IGNORADO|RECUSADO|ERRO,
                            detalhe?, pendenciaId?, consulta? } ] }
```

## Idempotência (PostgreSQL, ADR-0023)
- Tabela `comando_movel (org_id, comando_id PK-lógico, intencao, status, resultado, recebido_em)`.
- Reenvio do mesmo `comandoId` → devolve o resultado gravado, **não re-executa** (INV-13:
  sync pode repetir sem duplicar efeito).
- Escrita do comando + efeito na **mesma transação** (outbox transacional).

## Mapeamento determinístico (INV-10)
Cada intenção roteia para o serviço de domínio existente — nenhum efeito novo:
| Intenção | Ação |
|---|---|
| RESOLVER/REPOUSAR/RESERVAR/ADIAR | `TriagemService` |
| REPASSAR | `MotorAutonomia.delegar` |
| REGISTRAR | escape de captura (`POST /v1/pendencias`, métrica de falha — ADR-0005) |
| CONSULTAR | `Consulta.consultar` (020), resultado volta no `consulta` do resultado |

Comando que referencia pendência inexistente/fechada → `IGNORADO` (não erro: a fila
pode ter mudado desde a captura offline). REGISTRAR nunca é ignorado.

## App (Flutter, ADR-0015)
- Fila local persistida (SQLite/Drift): comando enfileirado ANTES de qualquer rede.
- Worker de sync com retry exponencial e chave de idempotência por comando.
- Sessão: `org_id`/`pessoa_id` (piloto, como o web); troca por OIDC quando existir.
- **Sem push de "novo item"** (CLAUDE.md §8).

## Fora do fluxo de efeito
INV-14 vale: REPASSAR N2 mantém a janela de reversibilidade do motor; o efeito
externo só sai após a janela — o app não força efeito imediato.
