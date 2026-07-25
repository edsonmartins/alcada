# Design — 001 captura multicanal

Referência completa: `rfc/RFC-0001-pipeline-de-captura.md`.

## Módulos
`captura.ingestao` · `captura.normalizacao` · `captura.relevancia` · `captura.extracao` ·
`captura.entidades` · `captura.dedup` · `captura.roteamento`

## Tabelas
```sql
fonte(id, org_id, tipo, identificador, finalidade, responsavel_id, ativa, criada_em)
evento_bruto(id, fonte_id, autor_ext, texto, anexos_ref, thread_ref, recebido_em, expira_em)
pendencia(id, org_id, titulo, quem_espera, o_que_trava, prazo_implicito, valor_em_jogo,
          classe, horizonte, status, temperatura, adiado_count, criada_em)
cobranca(id, pendencia_id, evento_bruto_id, recebida_em)
entidade(id, org_id, tipo, nome_canonico, apelidos[])
```
`evento_bruto.expira_em` é obrigatório (ADR-0011, retenção ≤ 30 d) e há job de expurgo.

## Contratos
- Extração retorna JSON validado por schema; falha de schema → 1 reprocesso → item de baixa confiança
- Roteamento é determinístico: modelo entrega `classe` + `confianca`; a regra decide (INV-10)
- Dedup: `entidade + janela 7d + similaridade > limiar`; empate resolve por thread

## Riscos técnicos
Resolução de entidade em texto informal; limiar de similaridade conservador para evitar fusão errada.
