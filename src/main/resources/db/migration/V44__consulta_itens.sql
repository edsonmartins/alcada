CREATE INDEX ix_pendencia_consulta_atividade ON pendencia (org_id, criada_em DESC, id);
CREATE INDEX ix_delegacao_consulta_ultima ON delegacao (org_id, pendencia_id, criada_em DESC);
CREATE INDEX ix_trilha_consulta_ultima ON trilha (org_id, pendencia_id, ocorrido_em DESC);
