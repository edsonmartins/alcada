# 022 — Canal de voz: STT on-device, intenção e confirmação de campos críticos

**Fase:** F5 · **Implementa:** ADR-0014 · RFC-0005 · **Honra:** INV-13, INV-14, INV-10
**Depende de:** 021 (base offline + `/v1/comandos`)

## Problema
A fala real do gestor no carro é livre: *"aquele negócio do Panorama, fala pro
Alexandre tocar e me avisa"*. Falta transformar fala → intenção estruturada,
**sem inventar** e **sem deixar nada irreversível sair de uma única fala**.

## Proposta
1. **STT on-device** (offline, INV-13): áudio capturado e persistido localmente
   antes de qualquer rede; transcrição no dispositivo.
2. **Interpretador de intenção** (INV-10): mapeia fala livre → uma das intenções
   fechadas (RESOLVER/REPASSAR/ADIAR/REGISTRAR/CONSULTAR) + campos. O modelo
   **propõe**; o comando determinístico (021) executa. Nunca gera ação fora da
   lista.
3. **Confirmação de campos críticos** (ADR-0014 §2): nome, valor e prazo são
   confirmados em uma frase curta antes de qualquer efeito — é onde o STT erra
   ("Carol"/"Carla", "quinze"/"cinquenta").
4. **Resolução de referência vaga** (ADR-0014 §6): resolve contra a fila; sem
   certeza, pergunta **uma vez** com no máximo duas opções; na terceira
   tentativa, adia para revisão.
5. **Dois modos** (ADR-0014 §1): **Ditar** (registrar/cobrar/consultar) e
   **Despachar** (decidir a fila). A recusa por movimento é do pacote 023.

## Não-objetivos
- Detecção de movimento e recusa por classe em trânsito (pacote 023).
- Escolha final do motor de STT/TTS (decisão pendente do RFC-0005 / DECISOES-ABERTAS):
  este pacote define o **contrato** do interpretador; o motor entra por trás de uma porta.
