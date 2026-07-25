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
