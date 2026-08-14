# Tasks — 036 repasse interno com link e toque dirigido

## Especificação
- [x] Proposal, design, spec e tasks

## Link canônico
- [x] Rota web autenticada para Pendência
- [x] Android App Link e esquema de fallback
- [x] iOS Universal Link e esquema de fallback
- [x] App abre diretamente o detalhe e trata alvo indisponível

## Identidade e dispositivos
- [x] WhatsApp próprio validado na identidade
- [x] Registro/revogação de instalação Android/iOS
- [x] Token criptografado e isolado por tenant/pessoa
- [x] Documentar contratos em `spec/docs/API.md`

## Entrega
- [x] Outbox de reforço interno após janela/trajeto
- [x] Linktor para WhatsApp interno com link canônico
- [x] Porta push e adaptadores FCM/APNs
- [x] Fallbacks por canal e observabilidade sem PII

## Validação
- [x] Testes C1–C8 (rastreabilidade distribuída nos testes de link, autorização, outbox, canais e dispositivos)
- [ ] Android e iOS: frio, background e foreground
- [x] Jornada real após configurar Firebase/APNs e associação HTTPS (Android e iOS validados com repasse dirigido, abertura do detalhe e conclusão)

### Evidências de validação manual

- Firebase/FCM configurado no projeto `alcada`; APNs de desenvolvimento e produção configurados no Firebase.
- `assetlinks.json` e `apple-app-site-association` publicados em `https://alcada.vendax.ai/.well-known/`.
- Android: push recebido e link abriu o detalhe correto.
- iOS: push recebido no iPhone físico e ação “Abrir” abriu o detalhe correto; concluir a delegação não foi causado pelo toque.
- Os cenários frio/background ainda precisam de execução específica; os testes automatizados C1–C8 permanecem como próxima etapa de cobertura.

### Cobertura automatizada já disponível

- C3/C4/C7: `RepasseAvisoTest` e `RepasseExternoTest` (janela, idempotência e degradação de canal).
- C5/C8: `RepasseAvisoTest` (toque não executa; correlação minimizada e texto sem dados de contato no link).
- C6: `DispositivosPushResourceTest` (isolamento por pessoa/instalação e revogação).
- C1/C2: `LinkAplicativoResourceTest` e `SuperficieExecutorTest` (link canônico, formato inválido,
  isolamento e tentativa de ação por executor incorreto).
