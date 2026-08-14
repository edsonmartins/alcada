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
- [ ] Porta push e adaptadores FCM/APNs
- [x] Fallbacks por canal e observabilidade sem PII

## Validação
- [ ] Testes C1–C8
- [ ] Android e iOS: frio, background e foreground
- [ ] Jornada real após configurar Firebase/APNs e associação HTTPS
