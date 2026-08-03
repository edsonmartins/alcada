-- Repasse com notificação (RFC-0008): destinatário pode ser um contato EXTERNO
-- (WhatsApp/e-mail) que NÃO é usuário do Alçada. O contato é dado operacional de
-- repasse (escape, ADR-0005), não uma conta (INV-02). Multi-tenant por org_id (INV-15).
CREATE TABLE contato_externo (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id     uuid        NOT NULL,
    nome       text        NOT NULL,
    canal      text        NOT NULL CHECK (canal IN ('WHATSAPP', 'EMAIL')),
    endereco   text        NOT NULL,                 -- telefone (E.164) ou e-mail
    criado_por uuid        NOT NULL,                 -- gestor que registrou
    criada_em  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_contato_externo_org ON contato_externo (org_id);

-- A delegação passa a apontar para um dono interno (pessoa) OU um contato externo.
-- Exatamente um dos dois (XOR).
ALTER TABLE delegacao ALTER COLUMN dono_id DROP NOT NULL;
ALTER TABLE delegacao ADD COLUMN contato_id uuid REFERENCES contato_externo(id);
ALTER TABLE delegacao ADD CONSTRAINT delegacao_destino_ck
    CHECK ((dono_id IS NOT NULL) <> (contato_id IS NOT NULL));
