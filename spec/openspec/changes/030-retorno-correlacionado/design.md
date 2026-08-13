# Design — 030 retorno correlacionado

## Componentes

- `autonomia.port.RetornosDelegacao`: cria/revoga correlação e recebe evidência;
- `autonomia.internal.RetornosJdbc`: lock, idempotência, validação e transição;
- captura extrai contexto do webhook e desvia somente quando token resolve exatamente;
- gateway recebe apenas trecho minimizado para propor tipo;
- dossiê inclui retornos pela porta publicada, sem acesso à tabela interna.

## Migration V40

Cria `correlacao_retorno`, `retorno_delegacao`, flag `delegacao.retorno_pendente` e índice por
organização. Emenda o CHECK da trilha com `RETORNO_RECEBIDO`. Token/endereço não entram na trilha.

## Token

32 bytes por `SecureRandom`, Base64 URL-safe sem padding. Banco persiste SHA-256. Comparação usa
digest binário. Expiração inicial: prazo + escalonamento + 30 dias, limitada pela retenção do tenant.

## Integração outbound

`EnviarDireto` e `EnviarMensagem` ganham `correlacao` opcional. Adaptadores colocam o valor em
metadata; stubs guardam para teste. Outbox mantém o token apenas no payload cifrado/temporário até a
entrega. Se o modelo de outbox atual não oferece confidencialidade suficiente, o token é recuperado
por referência no momento do despacho e nunca gravado no payload claro.

## Integração inbound

O webhook resolve a Fonte antes do token, garantindo tenant. Token inválido/ausente segue `Ingestao`.
Token válido chama a porta de retornos e não cria uma segunda Pendência. Autor externo é comparado
normalizado com o contato da delegação; divergência segue captura e incrementa métrica de rejeição.

## Classificação

Uma função de gateway específica recebe texto minimizado e schema fechado. Falha, baixa confiança ou
tipo inválido resulta em `INCONCLUSIVO`. Código decide a transição pela tabela do RFC-0010.

## Reversibilidade

Receber evidência não é efeito externo. Suspender execução evita um efeito ainda não publicado. Uma
ação posterior do gestor para retomar/confirmar usa as janelas existentes.

## Feature flag

`alcada.retorno-correlacionado.orgs` habilita por tenant. Em modo observação, persiste métrica e
retorno, mas não muda estado. Rollout geral somente depois da integração ao vivo.
