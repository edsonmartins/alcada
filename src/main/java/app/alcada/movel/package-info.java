/**
 * Módulo <b>movel</b> (021, RFC-0005): sincronização de comandos do canal móvel.
 * Recebe comandos já estruturados e os executa de forma idempotente (INV-13),
 * mapeando cada intenção para a ação determinística das portas de triagem,
 * autonomia, captura e consulta (INV-10). Não conhece STT nem voz. Fala com os
 * outros módulos apenas pelas portas.
 */
package app.alcada.movel;
