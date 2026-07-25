# 002 — Motor de autonomia N2

## Por quê
N2 é o mecanismo central de encolhimento (INV-01, INV-06). Sem ele o produto organiza melhor a mesma
sobrecarga. É também o maior risco cultural e jurídico — por isso as garantias são parte do escopo,
não refinamento posterior.

## O quê
Delegação com nível e prazo; execução por ausência com janela de reversibilidade; escalonamento por
silêncio de ambos; modo ausência; trilha específica.

## Fora de escopo
Superfície do executor (pacote 005) — este pacote entrega API e motor. Mineração de regras de autonomia (008).

## Critério de aceite
- Nenhum efeito externo antes do fim da janela de reversibilidade
- Nenhuma execução em branco (silêncio de ambos escala, não executa)
- Reinício da aplicação não perde nem duplica execução agendada
- Modo ausência converte N2 em N3 automaticamente
