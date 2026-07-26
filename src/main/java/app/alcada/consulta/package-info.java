/**
 * Módulo <b>consulta</b> (RFC-0004 §3): consulta em linguagem natural sobre a
 * fila. Traduz pergunta livre → template de uma whitelist fechada → SQL
 * determinístico escopado por organização (INV-15). O modelo, quando habilitado,
 * só escolhe o template; nunca gera SQL nem inventa dados (INV-10). Fala com a
 * plataforma apenas pelas portas.
 */
package app.alcada.consulta;
