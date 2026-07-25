# Protótipo — Alçada

`alcada-sistema.html` — abra no navegador, roda sem servidor e sem instalação.
Dados fictícios. Estado em memória: recarregar zera tudo.

**Serve como referência visual e de interação para a implementação em React + Mantine**
(ADR-0023). O que estiver aqui é intenção de produto; o que não estiver, não inventar.

## Roteiro de demonstração (8 minutos)

1. **Entrada** — clique numa linha. Painel de detalhe com histórico, leitura da IA e as quatro
   saídas. Cada saída diz se o item sai da mesa ou continua com o gestor.
2. **Cronômetro** — um item em N2 vence cerca de 45 s após abrir a página. Quando estoura, o sistema
   executa por ausência, avisa e registra na trilha. Outro vence em 3 minutos.
3. **Adiamento** — abra o item do Panorama (adiado 3×) ou o piloto do Marra (4×), clique em "Deixar
   para depois" e escolha *"nada, só não quero decidir agora"*. É o melhor momento da demonstração.
4. **Bloco de decisão** — aba Blocos, abra qualquer item. Dossiê com fontes, opções com consequência
   e o retorno já redigido. Ao decidir, aparece a pergunta de aprendizado.
5. **Regras** — os toggles ligam e desligam autonomias, com o ganho semanal ao lado.
6. **Esteira** — "Aplicar checklist" move três integradores e deixa só o Panorama.
7. **Radar** — o percentual de gargalo desce conforme o gestor delega durante a própria demo.

Teclado: `j`/`k` navegam, `1-4` decidem, `a` adia, `/` busca, `⌘K` abre a paleta.

## O que ainda não existe
A tela do executor. Enquanto o N2 não tiver contraparte, o mecanismo central roda no vazio —
está no roadmap como pacote 005.
