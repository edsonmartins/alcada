# ADR-0029 — Correlação explícita do retorno pelo canal

**Status:** aceito · **Data:** 2026-08 · **Emenda:** ADR-0021 e ADR-0016

## Contexto

O aviso de repasse externo sai pelo Linktor, mas uma resposta posterior chega apenas com canal,
autor e conversa. Correlacionar por telefone, conversa ou “única delegação aberta” pode anexar uma
resposta ao item errado. O erro esconderia uma Pendência ou poderia alimentar execução por ausência.

## Decisão

Toda comunicação que espera retorno recebe um **token opaco de correlação**, aleatório e não
sequencial, escopado por organização e uso único lógico. O token vai em metadata do envio e o Linktor
o propaga no contexto de `message.received` quando a mensagem recebida responde ou pertence ao fio
iniciado por aquela comunicação.

```text
Alçada → Linktor metadata.alcada_correlation
Linktor → Alçada data.context.alcada_correlation
```

O token identifica o contrato de delegação, não expõe `pendencia_id`, `delegacao_id` ou PII. Só o
hash é persistido; o valor claro existe no outbox enquanto necessário.

Sem token válido, a mensagem segue a captura normal. **Não há fallback por endereço, conversa,
similaridade, modelo ou quantidade de delegações abertas.** Preferimos não correlacionar a fundir
errado.

## Efeito da resposta

Texto livre recebido pelo canal é evidência, não comando. Ele nunca conclui delegação, executa N2 ou
dispara efeito externo. O sistema propõe uma classe de retorno; código determinístico:

- persiste o retorno minimizado, append-only;
- registra `RETORNO_RECEBIDO` na trilha;
- coloca a Pendência em `ENTRADA` quando há ação do gestor;
- mantém a delegação sem execução automática até ação humana/estruturada.

Conclusão pelo executor continua exigindo a superfície autenticada ou portal com ação explícita.

## Privacidade e segurança

- token com pelo menos 128 bits de entropia e comparação por hash;
- token não aparece em log, trilha ou URL pública;
- autor/endereço é validado contra o destino esperado antes de aceitar o retorno;
- conteúdo é minimizado antes de qualquer classificação por modelo;
- reentrega de `message.id` é idempotente;
- token de outro tenant ou revogado é indistinguível de token inválido.

## Vocabulário da trilha

ADR-0016 passa a admitir `RETORNO_RECEBIDO`. A carga contém apenas referências e tipo proposto,
nunca token, endereço ou texto livre.

## Consequências

- (+) correlação verificável e fail-closed;
- (+) resposta não consegue executar ação por inferência;
- (+) funciona para canais diferentes sob o mesmo contrato;
- (−) depende de mudança coordenada no Linktor;
- (−) respostas antigas/fora do fio continuam sem correlação automática.
