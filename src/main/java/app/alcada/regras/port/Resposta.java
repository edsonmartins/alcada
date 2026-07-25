package app.alcada.regras.port;

/** Três respostas da pergunta de aprendizado (ADR-0019). */
public enum Resposta {
    SIM,            // cria a regra (confirmação humana, INV-10)
    AGORA_NAO,      // recusa sem silenciar
    NAO_PERGUNTAR   // silencia a classe permanentemente
}
