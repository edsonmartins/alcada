# Tarefas — 025 repasse com notificação (RFC-0008)

## Backend — modelo e motor (F1.2)
- [x] Migration `V34`: tabela `contato_externo`; `delegacao.dono_id` nullable + `contato_id` + CHECK XOR (C4)
- [x] Porta `DestinoRepasse` (Interno|Externo) e `ContatosExternos` em `autonomia/port`
- [x] `MotorAutonomia.delegar(destino)` grava dono OU contato; externo publica `AVISO_REPASSE` no outbox, idempotente por `delegacaoId:aviso_repasse` (C1–C3). `delegar(donoId)` vira wrapper
- [x] Testes `RepasseExternoTest` (C1–C4)

## Backend — entrega pelo canal (F1.3)
- [x] WhatsApp: `Canal.enviarDireto` → Linktor `POST /api/v1/messages/send`; `DespachanteCanal` case `AVISO_REPASSE` resolve o `linktor_channel_id` do tenant → envia → trilha `COMUNICADA` (C5)
- [x] E-mail: porta `Email` + `EmailStub` (fora de prod) e `EmailSmtp` (@prod, quarkus-mailer); despachante roteia por canal do contato (C6)
- [x] Testes `RepasseAvisoTest` (C5–C8)

## Backend — diretório e comando (F1.4a/F1.4b)
- [x] `ContatosResource` `POST/GET /v1/contatos` (C9/C10) + `ContatosResourceTest`
- [x] `Comando.Campos.contato` ({id} ou {nome,canal,endereco}) e `case REPASSAR` chamando `motor.delegar(Externo)`; destino é `dono` XOR `contato`; contato novo nasce na transação do comando (C11–C15)
- [x] `ContatosExternos.buscar(org,id)` — valida tenant do contato referenciado (C16)
- [x] `aliasFalado` só aprende apelido no destino interno; nível segue virando preferência (C17)
- [x] Aviso represado quando o comando é ditado em trajeto (C18)
- [x] Testes `ComandoRepasseExternoTest` (C11–C18)
- [x] `spec/docs/API.md`: `/v1/contatos` e `campos.contato` em `/v1/comandos`

## Assistente (F1.4c)
- [ ] `InterpretadorVoz` resolve o nome falado contra pessoas **e** contatos externos; ambíguo → pergunta (C19)
- [ ] Nome novo → propõe registrar contato (nome, canal, endereço) com confirmação (C20)
- [ ] Apelido falado de contato externo (memória durável, hoje só de pessoa — C17)

## App (Flutter, F1.4d)
- [ ] Fluxo de escolher contato existente / criar novo no repasse
- [ ] Fala do resultado: "vou avisar o Marcello por WhatsApp" (C21)

## Web (React, F1.5)
- [ ] Tela de contatos externos (listar/criar/editar) (C22)
- [ ] Config de canais do tenant (channel id do Linktor, remetente de e-mail)

## Questões abertas herdadas do RFC-0008
- [ ] Consentimento/base legal do contato externo (LGPD) e opt-out
- [ ] Dedup de contato por endereço; reconciliação quando o externo vira usuário
- [ ] Eventos de resposta do externo (portal) — reusar RFC-0006 ou ampliar ADR-0016
- [ ] Interno também recebe toque no canal?
- [ ] Throttling/custo por canal no gateway de destino
