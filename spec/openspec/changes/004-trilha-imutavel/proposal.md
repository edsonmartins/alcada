# 004 — Trilha imutável

## Por quê
INV-11: execução por ausência (N2) e promoção de autonomia mudam quem responde por uma decisão. Sem
prova verificável, o mecanismo não sobrevive à primeira decisão controversa. A trilha é ainda a
**dependência de escrita de todos os demais pacotes de F1** — captura, motor e triagem gravam nela —
por isso vem primeiro na ordem de execução.

## O quê
Registro **append-only** por pendência, com vocabulário fechado (anexo normativo do ADR-0016),
formato de ator, carga específica por evento, evento de compensação como único mecanismo de correção,
particionamento mensal com rolagem automática, consulta por pendência isolada por organização e
eliminação LGPD por pseudonimização do titular preservando a cadeia.

## Fora de escopo
- A **semântica** de cada evento é dos pacotes que os emitem (001 captura, 002 autonomia, 003 triagem,
  assistente…). Aqui se define o contrato de escrita, o vocabulário e as garantias, não quando cada
  evento ocorre.
- **Arquivamento frio** de partições antigas (fase posterior; o particionamento já deixa o caminho).

## Critério de aceite
- Nenhum `UPDATE` ou `DELETE` em `trilha` é possível — nem para o role da aplicação, nem por
  privilégio (trigger + `REVOKE`).
- Tipo fora dos 29 do anexo é rejeitado na escrita; ator fora do formato é rejeitado.
- Correção existe **apenas** como evento `COMPENSACAO`, que referencia o evento compensado e não o
  altera.
- Descarte por irrelevância **não** gera trilha (não há pendência) — vai para métrica de captura.
- Consulta de trilha nunca retorna evento de outra organização (INV-15).
- A partição do mês seguinte existe **antes** de qualquer escrita nele (rolagem por job).
- Nenhum identificador direto é persistido na trilha; a eliminação LGPD pseudonimiza o titular sem
  quebrar a cadeia.
