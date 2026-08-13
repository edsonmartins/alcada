# Tasks — 028 instrumentação do piloto

## Decisões antes do código
- [x] Papel ADMIN adotado; bruto somente enquanto retido e acesso fail-closed (RIPD ainda é gate de produção)
- [x] Linguagem conservadora: tamanho/falsos negativos/inconclusivos; nunca rotular como recall exato
- [x] Evento `INTERROMPIDA` mapeado; corpo opcional preserva compatibilidade

## Persistência e domínio
- [x] Migrations V39 para motivo de intervenção, reconciliação e avaliação de descarte
- [x] Leitura do relatório/saúde e registros append-only para evidências humanas
- [ ] Vocabulário de trilha para evidências novas, se ADR-0016 exigir emenda

## API e web
- [x] Endpoints de relatório, reconciliação e amostra/avaliação; autorização e problem+json
- [x] `/piloto` fora da navegação diária; período, evidências e agregados
- [x] Motivo opcional no ato de intervir; reconciliação na superfície administrativa
- [x] Saúde de Fonte acompanhada de ação; gateway permanece no Radar existente

## Testes
- [~] Testes centrais de autorização, linguagem e isolamento; ampliar cobertura direta de C1–C12
- [ ] Teste de retenção: conteúdo expirado não aparece na amostra
- [x] Implementação de leitura não injeta gateway nem executa transição

## Validação
- [ ] Rodar por duas semanas em um piloto real
- [ ] Registrar entrevista de G2 e triangulação de G7
- [ ] Atualizar `DECISOES-ABERTAS.md` sem transformar hipótese em SLA
