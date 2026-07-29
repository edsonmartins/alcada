# Protótipo do extrator — caso C1 (reunião no grupo)

Fixture de aceite do cenário **C1**. Mostra o que o extrator recebe, o schema, e o
que sai — **pseudonimizado**, exatamente como o minimizador (ADR-0020 §3) entrega
ao modelo. Nenhuma PII real vive neste arquivo (nome/telefone/e-mail viram tokens;
re-hidratação para 1º nome é **local**, não persistida aqui).

## 1) Entrada do modelo — janela minimizada
`P2` é o **gestor** (cliente Alçada) nesta fonte; `P1` é a organizadora; a data das
mensagens ancora a resolução de "próxima segunda".

```json
{
  "gestor": "P2",
  "grupoId": "G1",
  "dataMensagens": "2026-07-27",        // segunda-feira → "próxima segunda" = 2026-08-03
  "fusoTenant": "America/Sao_Paulo",
  "janela": [
    { "seq": 1,  "sender": "P1", "text": "Bom dia!" },
    { "seq": 2,  "sender": "P1", "text": "Vamos marcar pra falar de cronograma atualizado e próximos passos?" },
    { "seq": 3,  "sender": "P1", "text": "Qual das seguintes datas é melhor pra vocês? Quarta - 14h / Quinta - 11h" },
    { "seq": 4,  "sender": "P2", "text": "Essa semana nao consigo" },
    { "seq": 5,  "sender": "P2", "text": "Vamos na proxima" },
    { "seq": 6,  "sender": "P1", "text": "próxima segunda?" },
    { "seq": 7,  "sender": "P1", "text": "qual horário?" },
    { "seq": 8,  "sender": "P2", "text": "14 hta" },
    { "seq": 9,  "sender": "P2", "text": "Manda invite pf" },
    { "seq": 10, "sender": "P2", "text": "<EMAIL_1>" },      // e-mail (dado funcional do invite)
    { "seq": 11, "sender": "P1", "text": "enviei!" },
    { "seq": 12, "sender": "P1", "text": "obrigada" }
  ]
}
```

## 2) Instrução ao modelo (essência do prompt)
> Você recebe uma **janela de conversa de grupo** (com o remetente por linha) e o
> token do **gestor**. Decida se há algo que **depende de uma decisão ou ação do
> gestor** — alguém pedindo a ele, esperando por ele, ou uma decisão/compromisso
> que recai sobre ele. Se sim, extraia **um** compromisso no schema. Se não,
> `dependeDoGestor=false` e nada mais. **Não invente** dados que não estão no fio;
> resolva datas relativas usando `dataMensagens`/`fusoTenant`. Responda **só** o JSON.

## 3) Schema estrito (json_schema — o gateway exige, nunca json_object)
`dependeDoGestor` (bool), `tipo` (enum REUNIAO|APROVACAO|DECISAO|FOLLOW_UP|OUTRO),
`assunto` (str), `quemPede` ({token}), `quando` ({textoOriginal, resolvido?}),
`acaoPendente` (str|null), `possivelmenteFeito` (bool), `confianca` (0..1),
`sourceMessageSeqs` (int[]).

## 4) Saída esperada do modelo (ainda com tokens)
```json
{
  "dependeDoGestor": true,
  "tipo": "REUNIAO",
  "assunto": "cronograma atualizado e próximos passos",
  "quemPede": { "token": "P1" },
  "quando": { "textoOriginal": "próxima segunda, 14h", "resolvido": "2026-08-03T14:00" },
  "acaoPendente": "reunião acordada; invite solicitado ao gestor",
  "possivelmenteFeito": true,
  "confianca": 0.82,
  "sourceMessageSeqs": [2, 3, 5, 6, 8, 9, 11]
}
```

## 5) Re-hidratação local → pendência na Entrada (o que o gestor vê)
Tokens viram identidade **mínima por finalidade** (emenda ADR-0011):
- `P1` não tem nome no fio (só número) → mostra papel: **"a organizadora do grupo"**.
- `<EMAIL_1>` é **dado funcional do invite** → preservado só neste item.

```
[REUNIÃO]  Cronograma atualizado e próximos passos
Quando:    segunda, 03/08 · 14h
Com:       a organizadora do grupo (grupo "G1")
Situação:  vocês acordaram; invite já foi enviado — confirmar presença
Origem:    grupo "G1" · 27/07
```
- `dependeDoGestor=false` ou `confianca` baixa → **não** cria pendência (só loga).
- Nova cobrança do mesmo assunto → **funde** (FUNDIDA) + contador; limiar → ESCALADA.

## Notas de teste
- **C1** verde quando, dada a janela acima, sai o compromisso do item 4 (mesmo
  `tipo`, `resolvido`, `quemPede`, `acaoPendente`, `possivelmenteFeito`).
- Trocar `gestor` para `P1` muda a leitura: a ação vira "enviar o invite" e
  `possivelmenteFeito=true` (ela disse "enviei!") — o extrator deve refletir o papel do gestor.
- Rodar de verdade exige o gateway ligado (chave por env); em dev/test, stub com
  esta saída fixa valida o pipeline sem chamar o provedor.
