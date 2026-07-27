# Tarefas — 023 modo trajeto

## Decisão prévia (gate) — ADR-0027 (aceito)
- [x] Fonte de detecção de movimento registrada e **ratificada** (ADR-0027 aceito): estado explícito + override manual + sinais que só ligam o modo
- [ ] Definir a lista de classes recusáveis em movimento por tenant (default: BLOQUEIO + acima de valor-limite)

## Backend (este repo — pequeno)
- [ ] Marcar comandos como "represados" (trajeto) e liberar em lote ao encerrar; efeito externo só após liberação (INV-14)
- [ ] Config por tenant das classes recusáveis em movimento
- [ ] Testes: represamento durante trajeto; liberação após resumo; isolamento

## App (Flutter)
- [~] Porta `FonteMovimento` + máquina de estados PARADO/EM_TRAJETO (fonte manual, ADR-0027; RESUMO na fatia C)
- [x] Recusa por classe em movimento com bloco agendado (C1/C2) — em trajeto, RESOLVER item grave (BLOQUEIO ou valor ≥ limite) é recusado e reservado como bloco; parado, liberado. Testado (C1/C2 + borda). Config por tenant do limite/classes ainda usa default
- [ ] Condução: um item por vez, sequência do sistema; corte de item longo (~8s) (C6/C7)
- [ ] Resumo de trajeto ao estacionar, com desfazer por item (C4/C5)
- [ ] CarPlay/Android Auto, áudio em background, Live Activity durante o trajeto
- [ ] Testes: C1..C7
