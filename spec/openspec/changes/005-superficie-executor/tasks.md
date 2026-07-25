# Tasks — 005 superfície do executor

## Emenda ao vocabulário (ADR-0024)
- [x] ADR-0024 escrito + anexo do ADR-0016 atualizado
- [x] Migration `V9`: `CHECK` da `trilha` recriado com `DEVOLVIDA_PELO_EXECUTOR`
- [x] `TipoEvento` (enum) ganha `DEVOLVIDA_PELO_EXECUTOR`

## Backend — motor (autonomia)
- [x] `MotorAutonomia.concluir` → EXECUTADA + pendência FECHADA + trilha EXECUTADA + outbox (executada + item.fechado)
- [x] `MotorAutonomia.devolver` → DEVOLVIDA + pendência ENTRADA + trilha `DEVOLVIDA_PELO_EXECUTOR` + outbox delegacao.devolvida
- [x] Autorização por dono em `propor`/`concluir`/`devolver` (403 se não for o dono)
- [x] `GET /v1/delegacoes` escopado ao executor autenticado (fronteira de autorização; sem `?todos=`)
- [x] Guardas por estado (409 em ação inválida); job de virada vira no-op após concluir/devolver

## Backend — API
- [x] `POST /v1/delegacoes/{id}/concluir` { resultado }
- [x] `POST /v1/delegacoes/{id}/devolver` { motivo }
- [x] Reflexão dos DTOs; erros em problem+json

## Web — tela do executor
- [x] Rota `/executor`: lista das delegações do usuário (nível, prazo, contrato)
- [x] Explicação do silêncio (executa por ausência / escala) por delegação
- [x] Ações `propor` / `concluir` / `devolver`
- [~] Validação com Zod nos formulários — inline nesta fase; formalizar no incremento

## Testes
- [x] Backend: isolamento por dono (403), concluir (FECHADA + aviso), devolver (ENTRADA + aviso), 409, GET escopado, isolamento org
- [x] Web: lista só do executor, contrato visível, ações disparam mutação (Vitest)

## Decisões (fechadas)
1. `devolver` → `DEVOLVIDA_PELO_EXECUTOR` (ADR-0024); não reusar `ESCALADA`.
2. `concluir` imediato, sem janela; efeito via outbox; reversão = nova pendência.
3. `GET /v1/delegacoes` escopado como fronteira de autorização; gestor em rota à parte.
4. INV-09: aviso ao solicitante é efeito de outbox aqui; entrega no canal via Linktor é do **006**.

---
**Estado:** pacote 005 **completo** — emenda ADR-0024, backend (64 testes JVM, nativo ~71 MB RSS) +
web (tela /executor, 11 testes Vitest).
