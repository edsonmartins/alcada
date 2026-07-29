# ADR-0011 — LGPD na captura de canais de mensageria e e-mail

**Status:** aceito · **Data:** 2026-07 · **Criticidade:** alta · **Relacionado:** INV-12

## Contexto
A captura passiva lê grupos de mensageria e caixas de e-mail corporativas. Isso envolve dados de
terceiros (clientes, parceiros, colaboradores) que não são usuários do sistema.

## Decisão
Regime de captura mínima:

1. **Fonte declarada.** Nenhum canal é lido sem cadastro explícito por admin, com finalidade
   registrada e responsável identificado.
2. **Aviso no canal.** Em grupos, mensagem fixada e aviso na entrada do bot. O bot é visível e
   identificável, nunca observador silencioso.
3. **Captura seletiva.** Só mensagens que mencionam o bot, respondem a item existente ou casam com
   padrão configurado. **Varredura completa é proibida.**
4. **Retenção do bruto.** Conteúdo original retido por no máximo 30 dias (configurável para menos),
   apenas para correção de extração. Persiste-se o extraído estruturado.
5. **Minimização.** Não persistir dado pessoal não necessário à decisão. Anonimização de terceiros
   não envolvidos.
6. **Direitos do titular.** Eliminação e portabilidade operam sobre pendências e trilha, com
   pseudonimização preservando integridade da trilha (INV-11).
7. **Base legal.** Legítimo interesse para dados de colaboradores no exercício da função, com LIA
   documentada; contrato para dados de parceiros.

## Consequências
- (+) Torna a captura defensável em auditoria e em venda para cliente maduro.
- (−) Captura seletiva reduz recall; parte do backlog invisível continua invisível. Aceito.
- (−) Conflito entre trilha imutável e direito de eliminação exige pseudonimização, não deleção.

## Pendência
DPIA/RIPD formal antes do primeiro cliente em produção.

## Emenda 2026-07 (via change 024 — acompanhamento de grupos)
Refina §3 e §5 para viabilizar acompanhar grupos sem afrouxar o regime:

1. **Captura seletiva de grupo por padrão de decisão.** A "captura seletiva" (§3)
   inclui, para uma fonte-grupo declarada com finalidade, um **pré-filtro
   determinístico** que só admite como candidato o trecho que: menciona o
   bot/gestor, responde/segue um item já rastreado, **ou** casa com padrão de
   pedido/decisão/prazo/agendamento. Ruído é descartado **antes** de qualquer
   chamada de modelo. Continua valendo: **varredura completa proibida** e **log
   auditável da proporção processada** por fonte. Não altera §2 (bot visível, aviso
   fixado — pré-condição da captura).

2. **Identificação mínima por finalidade** (refina §5, "anonimização de
   terceiros"). Anonimizar tudo inviabiliza a decisão ("quem pede?"). A regra passa
   a ser **o mínimo de identidade necessário para o gestor agir**:
   - **primeiro nome por padrão** (suficiente para reconhecer);
   - **contato completo** (e-mail/telefone) só quando **é a própria ação** — ex.: o
     e-mail que o terceiro deu no fio para receber o invite;
   - **supressão** para terceiro que é apenas contexto (não vira identidade);
   - sem nome disponível (só número) → usa o número ou o papel; **nunca inventar nome**.

   Não conflita com o minimizador (ADR-0020 §3): ao **modelo** vai pseudonimizado;
   a re-hidratação para 1º nome é **local**, só na superfície do gestor. E não
   conflita com ADR-0017: o alvo é a **decisão do gestor**, nunca métrica de
   comportamento dos membros do grupo.
