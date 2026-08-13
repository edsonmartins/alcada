# 029 — Repasse para destino reconhecível

**Fase:** experiência essencial · **Honra:** INV-02, INV-04, INV-06, INV-10, INV-15
**Depende de:** 002, 005, 022, 025, 027

## Problema

O web pede `id da pessoa` no principal ato de autonomia. Isso torna a jornada inviável para usuário
real e força manutenção prévia em “Canais e contatos”. O produto já conhece pessoas e contatos, mas
não oferece um seletor único nem padrões úteis daquele gestor.

## Proposta

- destino unificado: pessoa interna ou contato externo, exibido por nome e canal;
- busca por nome ou apelido, tolerante a acento, sempre escopada ao tenant;
- recentes e destinos usados na mesma Classe;
- nível e prazo sugeridos por regra determinística a partir do último contrato comparável;
- registro de contato externo durante o repasse, como escape operacional;
- contrato de API tipado, preservando temporariamente `donoId` para compatibilidade.

## Não-objetivos

- ranking de pessoas, carga individual ou recomendação baseada em produtividade;
- escolher destino automaticamente;
- importar diretório corporativo;
- responder ao repasse pelo canal — pacote 030;
- transformar contatos em CRM ou exigir cadastro antecipado.

## Critério de aceite

No web, o gestor repassa para pessoa ou contato conhecido sem ver UUID e registra um contato novo
sem abandonar a ação. Destino, nível e prazo sempre são confirmados antes da transição.
