-- 029: um mesmo endereço operacional representa o mesmo contato dentro do tenant.
-- Mantém PII no domínio e impede duplicata também sob corrida.
CREATE INDEX ix_contato_externo_endereco_normalizado
    ON contato_externo (org_id, canal, lower(trim(endereco)));
