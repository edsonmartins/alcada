# Spec — 015 portal de instância + autoavaliação

## Cenário: contraparte vê o estado por link assinado
**WHEN** a contraparte abre `GET /pi/{token}` com um token válido
**THEN** recebe esteira, etapa atual, data de entrada, prazo previsto e os critérios objetivos que
faltam dela
**AND** não recebe deliberação interna, nomes de decisores nem outras contrapartes

## Cenário: token inválido/expirado/revogado é uniforme
**WHEN** o token é inválido, expirado ou revogado
**THEN** a resposta é a mesma ausência ("não encontrado"), sem distinguir o motivo

## Cenário: contraparte declara conformidade
**WHEN** a contraparte envia `POST /pi/{token}/autoavaliacao` com declarações por critério
**THEN** as declarações são gravadas escopadas à instância do token
**AND** ficam disponíveis ao gestor (não decidem por si — INV-10)

## Cenário: emissão devolve o token cru uma única vez
**WHEN** o gestor emite um token para a instância
**THEN** o token cru é retornado uma vez e o banco guarda apenas o hash

## Cenário: revogação corta o acesso
**WHEN** o gestor revoga o token
**THEN** `GET /pi/{token}` passa a responder a ausência uniforme

## Cenário: isolamento por organização (INV-15)
**WHEN** o token de uma organização é resolvido
**THEN** só expõe/grava dados daquela organização e instância
