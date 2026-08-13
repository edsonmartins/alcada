# 028 — Instrumentação do piloto G2/G7

**Fase:** validação · **Honra:** INV-01, INV-02, INV-07, INV-11, INV-15
**Depende de:** 001, 002, 003, 009, 024, 027

## Problema

G2 e G7 são bloqueantes, mas hoje a evidência está espalhada entre trilha, pendência, delegação,
descarte e entrevistas. Sem um protocolo reproduzível, uma demonstração bem-sucedida pode ser
confundida com aceitação do N2, e ausência de reclamação pode ser confundida com bom recall.

## Proposta

1. Uma leitura agregada do piloto por organização e período, sem métrica individual.
2. Motivo curto e opcional ao interromper N2, para distinguir prudência de rejeição ao mecanismo.
3. Amostra de descartes para auditoria por facilitador autorizado, sob retenção e minimização.
4. Reconciliação semanal: registrar decisões reais lembradas que ficaram fora da fila.
5. Saúde operacional das Fontes e do gateway acompanhada de ação corretiva.
6. Relatório de encerramento que apresenta evidência, sem decidir automaticamente G2/G7.

## Não-objetivos

- score, ranking ou produtividade individual;
- afirmar recall exato a partir de amostra pequena;
- alterar automaticamente filtro, regra ou nível;
- facilitar ações na fila em nome do gestor;
- tornar o relatório uma nova tela de acompanhamento diário.

## Critério de aceite

Ao final de duas semanas, o responsável pelo piloto consegue responder G2 e avaliar G7 com trilha,
amostra e reconciliação, conhecendo limitações da amostra, sem consulta SQL artesanal.
