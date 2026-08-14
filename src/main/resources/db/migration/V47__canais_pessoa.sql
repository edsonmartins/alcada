ALTER TABLE pessoa ADD COLUMN whatsapp text;

ALTER TABLE pessoa ADD CONSTRAINT pessoa_whatsapp_e164_ck
    CHECK (whatsapp IS NULL OR whatsapp ~ '^\+[1-9][0-9]{9,14}$');

CREATE UNIQUE INDEX ux_pessoa_whatsapp_org
    ON pessoa (org_id, whatsapp) WHERE whatsapp IS NOT NULL;
