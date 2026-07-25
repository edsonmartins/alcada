/**
 * Módulo <b>captura</b>. Captura multicanal via Linktor: ingestão, relevância, extração (com minimizador), dedup e roteamento. Pacote OpenSpec 001.
 *
 * <p>Fronteira real (ADR-0023): dependências de outros módulos só via
 * {@code port}; nunca acessa {@code internal} alheio. Esqueleto nesta fase —
 * sem regra de negócio.
 */
package app.alcada.captura;
