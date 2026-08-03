# Cenários — Repasse com notificação

## Modelo e motor (F1.2)

## C1 — repasse interno não avisa por canal
- **WHEN** o gestor repassa a um membro interno
- **THEN** a delegação grava `dono_id`, **nenhum** `AVISO_REPASSE` é enfileirado e a trilha registra `REPASSADA`.
- *Teste:* `RepasseExternoTest.repasse_interno_nao_enfileira_aviso`

## C2 — repasse externo enfileira o aviso
- **WHEN** o gestor repassa a um contato externo
- **THEN** a delegação grava `contato_id` (e não `dono_id`), um `AVISO_REPASSE` vai ao outbox com contato, pendência, canal e endereço, e a trilha registra `REPASSADA`.
- *Teste:* `RepasseExternoTest.repasse_externo_enfileira_aviso_com_payload`

## C3 — o aviso não duplica no reprocesso (INV-13)
- **WHEN** o mesmo aviso é publicado de novo (`delegacaoId:aviso_repasse`)
- **THEN** continua havendo **uma** linha no outbox.
- *Teste:* `RepasseExternoTest.aviso_repasse_e_idempotente`

## C4 — a delegação tem um destino só
- **WHEN** se tenta gravar `dono_id` **e** `contato_id` na mesma delegação
- **THEN** o schema recusa (CHECK XOR).
- *Teste:* `RepasseExternoTest.check_impede_dono_e_contato_juntos`

## Entrega pelo canal (F1.3a/F1.3b)

## C5 — aviso por WhatsApp entrega e registra COMUNICADA
- **WHEN** o worker processa um `AVISO_REPASSE` de contato `WHATSAPP`
- **THEN** a mensagem sai pelo Linktor (`/messages/send`, sem conversa prévia) e a trilha registra `COMUNICADA` (ator SISTEMA).
- *Teste:* `RepasseAvisoTest.aviso_externo_entrega_no_canal_e_registra_comunicada`

## C6 — aviso por e-mail entrega por SMTP
- **WHEN** o contato é `EMAIL`
- **THEN** o aviso sai por SMTP e a trilha registra `COMUNICADA`.
- *Teste:* `RepasseAvisoTest.aviso_externo_email_envia_por_smtp_e_registra_comunicada`

## C7 — entrega idempotente
- **WHEN** o mesmo aviso é reprocessado
- **THEN** o contato não recebe duas mensagens.
- *Teste:* `RepasseAvisoTest.aviso_e_idempotente_no_reprocesso`

## C8 — canal indisponível não perde o aviso
- **WHEN** o canal falha
- **THEN** nada é dado por comunicado e a mensagem volta para retry (INV-13).
- *Teste:* `RepasseAvisoTest.canal_indisponivel_nao_comunica_e_reprocessa`

## Diretório de contatos (F1.4a)

## C9 — registrar e listar contato externo
- **WHEN** `POST /v1/contatos {nome, canal, endereco}`
- **THEN** `201 {id}` e o contato aparece em `GET /v1/contatos`, escopado ao tenant (INV-15).
- *Teste:* `ContatosResourceTest.cria_e_lista_contato_externo`

## C10 — canal ou nome inválidos são recusados
- **WHEN** o canal não é `WHATSAPP|EMAIL`, ou falta nome
- **THEN** `422`/`400` em problem+json, sem gravar.
- *Testes:* `ContatosResourceTest.canal_invalido_recusado`, `.sem_nome_recusado`

## Comando móvel (F1.4b)

## C11 — repasse para contato externo já conhecido
- **WHEN** o comando `REPASSAR` traz `campos.contato.id` de um contato do tenant
- **THEN** a pendência fica `DELEGADA` com `contato_id` preenchido e um `AVISO_REPASSE` é enfileirado.
- *Teste:* `ComandoRepasseExternoTest.repassa_para_contato_existente`

## C12 — repasse para contato novo registra na hora (escape, INV-02)
- **WHEN** o comando traz `campos.contato {nome, canal, endereco}` sem `id`
- **THEN** o contato é registrado e a delegação aponta para ele, com o aviso enfileirado.
- *Teste:* `ComandoRepasseExternoTest.repassa_para_contato_novo_registrando_na_hora`

## C13 — reenvio não duplica contato nem aviso (INV-13)
- **WHEN** o mesmo `comandoId` é sincronizado de novo
- **THEN** devolve o resultado gravado; continua havendo um contato, uma delegação e um aviso.
- *Teste:* `ComandoRepasseExternoTest.reenvio_do_mesmo_comando_nao_duplica_contato_nem_aviso`

## C14 — o repasse tem um destino só
- **WHEN** o comando traz `dono` **e** `contato` — ou nenhum dos dois
- **THEN** `ERRO` com o motivo e **nada** é despachado (a pendência segue na entrada).
- *Testes:* `ComandoRepasseExternoTest.dono_e_contato_juntos_e_recusado`, `.repasse_sem_destino_e_recusado`

## C15 — contato novo inválido não grava nada
- **WHEN** o contato novo vem com canal fora de `WHATSAPP|EMAIL`, sem canal, ou sem nome/endereço
- **THEN** `ERRO` com o motivo (nunca um erro interno, que ficaria cacheado pelo `comandoId`), nenhum contato criado e a pendência intacta.
- *Testes:* `ComandoRepasseExternoTest.contato_novo_com_canal_invalido_e_recusado`, `.contato_novo_sem_canal_e_recusado_com_motivo`

## C15b — contato sem nenhum campo não é destino
- **WHEN** o comando traz `campos.contato` vazio (`{}`)
- **THEN** vale como ausência: com `dono` é repasse interno; sem `dono` é `ERRO "REPASSAR exige dono ou contato"`.
- *Teste:* `ComandoRepasseExternoTest.contato_vazio_nao_conta_como_destino`

## C16 — contato de outra organização não existe aqui (INV-15)
- **WHEN** o comando referencia um `contato.id` de outro tenant
- **THEN** `ERRO "contato não encontrado"`, sem delegação.
- *Teste:* `ComandoRepasseExternoTest.contato_de_outra_organizacao_nao_e_encontrado`

## C17 — apelido falado não vira apelido de pessoa no destino externo
- **WHEN** o repasse externo traz `aliasFalado`
- **THEN** nenhum apelido de pessoa é aprendido; o nível usado continua virando o padrão do gestor.
- *Teste:* `ComandoRepasseExternoTest.repasse_externo_nao_aprende_apelido_de_pessoa`

## C18 — em trajeto, o terceiro só é avisado ao estacionar (INV-14)
- **WHEN** o repasse externo é ditado em trajeto
- **THEN** o `AVISO_REPASSE` nasce represado e só sai na liberação do resumo.
- *Teste:* `ComandoRepasseExternoTest.aviso_de_repasse_em_trajeto_nasce_represado`

## Assistente e app (F1.4c/F1.4d) — pendentes

## C19 — nome falado resolve contra pessoas e contatos externos
- **WHEN** o gestor diz "repassa pro Marcello" e Marcello é contato externo
- **THEN** o interpretador propõe o repasse externo para confirmação (nunca decide sozinho, INV-10).

## C20 — nome desconhecido vira contato com confirmação
- **WHEN** o nome falado não casa com ninguém
- **THEN** o assistente **pergunta** (quem é / por qual canal) e só registra o contato na confirmação.

## C21 — o app diz por onde o executor será avisado
- **WHEN** o repasse externo é confirmado no app
- **THEN** a fala do resultado diz que o aviso vai por WhatsApp ou e-mail.

## Web (F1.5) — pendente

## C22 — tela de contatos externos
- **WHEN** o gestor abre a configuração de contatos
- **THEN** vê, cria e edita os contatos do tenant, com o canal de cada um.
