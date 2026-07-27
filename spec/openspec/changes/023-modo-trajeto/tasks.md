# Tarefas — 023 modo trajeto

## Decisão prévia (gate) — ADR-0027 (aceito)
- [x] Fonte de detecção de movimento registrada e **ratificada** (ADR-0027 aceito): estado explícito + override manual + sinais que só ligam o modo
- [ ] Definir a lista de classes recusáveis em movimento por tenant (default: BLOQUEIO + acima de valor-limite)

## Backend (este repo — pequeno)
- [x] Marcar comandos como "represados" (trajeto) e liberar em lote ao encerrar; efeito externo só após liberação (INV-14) — outbox.trajeto_id (V24); worker filtra trajeto_id IS NULL; EscopoTrajeto (ThreadLocal) carimba na publicação; `POST /v1/trajeto/liberar` → Outbox.liberarTrajeto. Verificado ao vivo (represado não emite; liberar solta) + @QuarkusTest
- [ ] Config por tenant das classes recusáveis em movimento (hoje default: BLOQUEIO + valor ≥ limite, no app)
- [x] Testes: represamento durante trajeto; liberação; isolamento por org (@QuarkusTest)

## App (Flutter)
- [x] Porta `FonteMovimento` + máquina de estados PARADO/EM_TRAJETO/RESUMO (fonte manual, ADR-0027)
- [x] Recusa por classe em movimento com bloco agendado (C1/C2) — em trajeto, RESOLVER item grave (BLOQUEIO ou valor ≥ limite) é recusado e reservado como bloco; parado, liberado. Testado (C1/C2 + borda). Config por tenant do limite/classes ainda usa default
- [x] Condução: um item por vez, sequência do sistema; corte de item longo (~8s) (C6/C7) — `ConducaoTrajeto` apresenta um item por vez na ordem da fila; pesados (BLOQUEIO/alto valor/título longo) viram bloco reservado na entrada. Botão "Conduzir" na TelaVoz; fala o item, aplica o comando, avança; "Pular". Testado (controlador C6/C7). Heurística de ~8s = tamanho do título (refinar depois)
- [x] Resumo de trajeto ao estacionar, com desfazer por item (C4/C5) — bottom sheet lista os despachos; desfazer descarta o efeito represado (POST /v1/trajeto/desfazer, terceiro não é comunicado); "Confirmar e comunicar" libera. Verificado ao vivo + testes
- [~] CarPlay/Android Auto, áudio em background, Live Activity durante o trajeto — porta `PresencaTrajeto` + stub + fiação no ciclo do trajeto (iniciar/atualizar/encerrar) prontos e testados; motores nativos (foreground service Android, Live Activity iOS, CarPlay/Android Auto) plugam atrás da porta, exigem Mac/dispositivo + entitlements. Ver `presenca-nativa.md`
- [x] Testes: C1..C7 cobertos
