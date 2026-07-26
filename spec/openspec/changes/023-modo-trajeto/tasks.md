# Tarefas — 023 modo trajeto

## Decisão prévia (gate) — ADR-0027 (proposto)
- [x] Fonte de detecção de movimento registrada em ADR-0027 (estado explícito + override manual + sinais que só ligam o modo)
- [ ] Ratificar ADR-0027
- [ ] Definir a lista de classes recusáveis em movimento por tenant (default: BLOQUEIO + acima de valor-limite)

## Backend (este repo — pequeno)
- [ ] Marcar comandos como "represados" (trajeto) e liberar em lote ao encerrar; efeito externo só após liberação (INV-14)
- [ ] Config por tenant das classes recusáveis em movimento
- [ ] Testes: represamento durante trajeto; liberação após resumo; isolamento

## App (Flutter)
- [ ] Porta `FonteMovimento` + máquina de estados PARADO/EM_TRAJETO/RESUMO
- [ ] Recusa por classe em movimento com bloco agendado (C1/C2)
- [ ] Condução: um item por vez, sequência do sistema; corte de item longo (~8s) (C6/C7)
- [ ] Resumo de trajeto ao estacionar, com desfazer por item (C4/C5)
- [ ] CarPlay/Android Auto, áudio em background, Live Activity durante o trajeto
- [ ] Testes: C1..C7
