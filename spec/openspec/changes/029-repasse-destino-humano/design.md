# Design — 029 repasse para destino reconhecível

## Vocabulário

`DestinoRepasse` já distingue `Interno{pessoaId}` e `Externo{contatoId}`. A API e o web passam a
refletir esse tipo em vez de achatar tudo em `donoId`.

## API

```text
GET /v1/destinos-repasse?busca=&classe=&limite=
→ [{ tipo, id, nome, detalhe, canal?, recente, usadoNaClasse, nivelSugerido?, prazoSugerido? }]

POST /v1/pendencias/{id}/repassar
{
  destino: { tipo: "INTERNO", pessoaId } |
           { tipo: "EXTERNO", contatoId } |
           { tipo: "EXTERNO_NOVO", nome, canal, endereco },
  nivel,
  prazo
}
```

Durante uma janela de compatibilidade, o corpo antigo `{donoId,nivel,prazo}` continua aceito e é
normalizado para `INTERNO`. Resposta e erros permanecem problem+json.

## Consulta de destinos

A porta pertence a `autonomia`, compondo dados publicados por `identidade` e contatos externos sem
ler tabelas internas de outro módulo. Ordenação determinística:

1. casamento exato de nome/apelido;
2. usado na mesma Classe;
3. recente para aquele gestor;
4. prefixo de palavra;
5. nome normalizado.

No máximo 8 resultados. Termo vazio devolve recentes, nunca o diretório inteiro. Endereço completo
do contato não é necessário na busca: o detalhe mostra canal e versão mascarada.

## Sugestões

- **nível:** último nível usado pelo mesmo gestor + destino + Classe, desde que não exceda o máximo
  da Classe; fallback N2;
- **prazo:** duração do último contrato equivalente, ancorada ao fuso e política disponíveis; sem
  horário comercial configurado, não inventar prazo e exigir seleção;
- toda sugestão é identificada como sugestão e pode ser alterada;
- nenhum modelo é chamado e nenhuma pessoa é escolhida automaticamente.

O histórico de uso pode ser derivado de delegações/trilha. Se desempenho exigir materialização,
ela deve ser atualizada transacionalmente e escopada por `org_id`.

## Contato novo

O formulário inline solicita nome, canal e endereço; normaliza telefone/e-mail e reutiliza contato
equivalente no mesmo tenant. A criação e o repasse ocorrem na mesma transação para não deixar
cadastro órfão após falha. A decisão de deduplicar por endereço exige preservar delegações antigas e
registrar o reaproveitamento.

## Web

O drawer apresenta um combobox com seções `Recentes`, `Equipe` e `Contatos`. Busca abre após foco;
“Adicionar contato” aparece somente quando não há casamento adequado. Após selecionar, mostra:

- identidade e canal;
- nível com explicação N1/N2/N3;
- prazo absoluto e texto do contrato do silêncio;
- destino do aviso quando externo.

Teclado completo: pesquisar, navegar, selecionar, ajustar nível/prazo, confirmar e cancelar.

## Privacidade e antiviligância

Sem quantidade de delegações, taxa de conclusão, velocidade ou comparação entre destinos. Endereço
é PII, não atravessa gateway e aparece mascarado na busca.

## Compatibilidade mobile/voz

O endpoint unificado reutiliza o casamento já definido em 025. Mobile pode migrar depois sem quebra;
o pacote só exige que a regressão do corpo antigo permaneça verde.
