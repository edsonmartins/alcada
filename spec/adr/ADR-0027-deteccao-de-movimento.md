# ADR-0027 — Detecção de movimento do modo trajeto

**Status:** proposto (aguarda ratificação) · **Data:** 2026-07 · **Encerra:** G6 (DECISOES-ABERTAS)
**Relacionado:** ADR-0014 · **Condiciona:** pacote 023

## Contexto
O modo trajeto (ADR-0014 §3) **recusa decisões de alto impacto em movimento** e represa efeitos até
estacionar. Isso depende de saber se o gestor está dirigindo. O erro tem dois lados assimétricos:
- **Falso positivo** (acha que está em movimento, mas está parado) → **bloqueia uma decisão legítima**.
  Irrita e faz perder confiança.
- **Falso negativo** (acha que está parado, mas está dirigindo) → **permite decisão grave em
  movimento**. É o risco de segurança que o modo existe para evitar.

Nenhuma fonte isolada é confiável: GPS gasta bateria e erra em túnel; OBD/veículo não é universal;
declaração manual esquece de ligar/desligar.

## Decisão

### 1. O modo trajeto é um estado explícito, com override manual sempre disponível
- O gestor **liga o modo trajeto** (ou ele é sugerido automaticamente — ver 2). Enquanto ligado,
  valem a recusa por classe e o represamento (023).
- **Override manual "estou parado" está sempre a um toque**: desfaz o bloqueio de uma ação legítima.
  Isso neutraliza o custo do falso positivo sem abrir o falso negativo (o gestor parado é quem
  destrava; ninguém destrava dirigindo por engano de forma silenciosa).

### 2. Sinais de confirmação (soft), atrás de uma porta
A porta `FonteMovimento` do 023 combina, do mais forte ao mais fraco:
- **Conexão CarPlay / Android Auto ativa → em veículo** (sinal forte; liga o modo trajeto).
- **APIs de atividade do SO** (`CMMotionActivity` iOS / Activity Recognition Android): `automotive`
  com confiança alta sugere ligar o modo.
- **Declaração manual**: sempre disponível, prevalece sobre os sinais.

Nenhum sinal isolado **desbloqueia** uma decisão grave automaticamente; sinais só **ligam** o modo
(lado seguro). Desligar/destravar é ato do gestor.

### 3. Viés de projeto: preferir bloquear a permitir
Na dúvida entre parado e em movimento, o modo trajeto trata como **em movimento** (recusa a decisão
grave, agenda o bloco). O override manual paga o custo do falso positivo; o falso negativo — o risco
real — é evitado.

### 4. Sem rastreamento
Não persistir trajeto, rota ou localização. O sinal de movimento é efêmero, usado só para o estado do
modo. Coerente com o anti-vigilância (ADR-0017) e com a minimização (ADR-0010).

## Alternativas consideradas
- **Velocidade por GPS**: preciso, mas custa bateria, falha em túnel e cheira a rastreamento
  (recusado como fonte primária; pode entrar como sinal opcional futuro).
- **Conexão OBD/veículo como requisito**: sinal ótimo quando existe, mas não universal — vira só um
  dos sinais, não o gate.
- **Detecção 100% automática, sem override**: maximiza o falso positivo bloqueando ação legítima —
  recusado; o override manual é inegociável.

## Consequências
- (+) Falso positivo é barato (um toque destrava); falso negativo — o perigoso — é evitado pelo viés.
- (+) Sem rastreamento; sinais atrás de porta plugável.
- (−) Depende de o gestor usar o override quando um sinal errar — há atrito residual.
- (−) Cobertura dos sinais varia por aparelho/veículo; a declaração manual é o piso garantido.

## Revisão
Reabrir se a taxa de falso positivo medida na POC irritar o piloto, ou se a integração de veículo
(CarPlay/Android Auto/OBD) provar-se confiável o bastante para virar gate em vez de sinal.
