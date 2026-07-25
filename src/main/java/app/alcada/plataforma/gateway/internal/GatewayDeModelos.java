package app.alcada.plataforma.gateway.internal;

import java.math.BigDecimal;
import java.util.function.Supplier;

import app.alcada.plataforma.gateway.internal.AdaptadorOpenRouter.ResultadoExterno;
import app.alcada.plataforma.gateway.port.Destino;
import app.alcada.plataforma.gateway.port.FalhasGateway;
import app.alcada.plataforma.gateway.port.ModelGateway;
import app.alcada.plataforma.gateway.port.Sensibilidade;
import app.alcada.plataforma.gateway.port.Tarefas.Classificacao;
import app.alcada.plataforma.gateway.port.Tarefas.Embedding;
import app.alcada.plataforma.gateway.port.Tarefas.Extracao;
import app.alcada.plataforma.gateway.port.Tarefas.Redacao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaClassificacao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaEmbedding;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaExtracao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaRedacao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Gateway de modelos (RFC-0007). Decide o destino, aplica retentativa com
 * backoff dentro da lista homologada, registra a chamada (sem prompt/resposta)
 * e trata falha:
 * <ul>
 *   <li>schema estrito recusado → propaga {@link FalhasGateway.ProvedorSemSchema}
 *       (nunca degrada);</li>
 *   <li>indisponível na extração → enfileira reprocesso e devolve pendente
 *       (captura não perdida);</li>
 *   <li>indisponível na redação → falha visível (não enfileira, não degrada).</li>
 * </ul>
 */
@ApplicationScoped
public class GatewayDeModelos implements ModelGateway {

    @ConfigProperty(name = "gateway.retry.max", defaultValue = "3")
    int retryMax;

    @ConfigProperty(name = "gateway.retry.base-ms", defaultValue = "200")
    long retryBaseMs;

    private final Roteador roteador;
    private final AdaptadorOpenRouter externo;
    private final AdaptadorLocal local;
    private final FilaReprocesso fila;
    private final RegistroChamadas registro;

    public GatewayDeModelos(Roteador roteador, AdaptadorOpenRouter externo, AdaptadorLocal local,
                            FilaReprocesso fila, RegistroChamadas registro) {
        this.roteador = roteador;
        this.externo = externo;
        this.local = local;
        this.fila = fila;
        this.registro = registro;
    }

    @Override
    @Transactional
    public <T> Extracao<T> extrair(TarefaExtracao<T> t) {
        Destino destino = roteador.decidir(t.org(), t.sensibilidade());
        if (destino == Destino.LOCAL) {
            registro.registrar(t.org(), "extracao", t.sensibilidade(), Destino.LOCAL,
                    "local", null, 0, 0, 0, BigDecimal.ZERO, null, t.refMensagemId());
            String json = local.inferir(t.texto(), t.schemaJson()); // stub: não sai para fora
            return new Extracao<>(t.mapeador().apply(json), 1.0);
        }
        try {
            ResultadoExterno r = comBackoff(() -> externo.extrair(t.texto(), t.schemaJson()));
            registro.registrar(t.org(), "extracao", t.sensibilidade(), Destino.EXTERNO,
                    externo.provedorEfetivo(), externo.modeloExtracao(),
                    r.tokensIn(), r.tokensOut(), 0, BigDecimal.ZERO, true, t.refMensagemId());
            return new Extracao<>(t.mapeador().apply(r.conteudo()), 1.0);
        } catch (FalhasGateway.Indisponivel indisponivel) {
            // captura nunca perdida: enfileira e devolve pendente (confianca = null)
            fila.enfileirar(t.org(), "extracao", t.refMensagemId());
            registro.registrar(t.org(), "extracao", t.sensibilidade(), Destino.EXTERNO,
                    externo.provedorEfetivo(), externo.modeloExtracao(),
                    0, 0, 0, BigDecimal.ZERO, false, t.refMensagemId());
            return Extracao.pendente();
        }
        // ProvedorSemSchema e GuardrailRecusou propagam — falha, nunca degrada.
    }

    @Override
    @Transactional
    public Redacao redigir(TarefaRedacao t) {
        Destino destino = roteador.decidir(t.org(), t.sensibilidade());
        if (destino == Destino.LOCAL) {
            registro.registrar(t.org(), "redacao", t.sensibilidade(), Destino.LOCAL,
                    "local", null, 0, 0, 0, BigDecimal.ZERO, null, t.refMensagemId());
            return new Redacao(local.inferir(t.contexto(), null));
        }
        ResultadoExterno r = comBackoff(() -> externo.redigir(t.contexto(), t.tom()));
        // Indisponivel propaga: redação falha de forma visível, não enfileira, não degrada.
        registro.registrar(t.org(), "redacao", t.sensibilidade(), Destino.EXTERNO,
                externo.provedorEfetivo(), externo.modeloRedacao(),
                r.tokensIn(), r.tokensOut(), 0, BigDecimal.ZERO, null, t.refMensagemId());
        return new Redacao(r.conteudo());
    }

    @Override
    public Classificacao classificar(TarefaClassificacao t) {
        throw new UnsupportedOperationException("classificação: implementada com o pacote 001");
    }

    @Override
    public Embedding embutir(TarefaEmbedding t) {
        throw new UnsupportedOperationException("embedding (pgvector): fase posterior");
    }

    /** Retenta apenas indisponibilidade, dentro da lista homologada, com backoff. */
    private <R> R comBackoff(Supplier<R> chamada) {
        FalhasGateway.Indisponivel ultima = null;
        for (int i = 0; i < Math.max(1, retryMax); i++) {
            try {
                return chamada.get();
            } catch (FalhasGateway.Indisponivel e) {
                ultima = e;
                dormir(i);
            }
        }
        throw ultima;
    }

    private void dormir(int tentativa) {
        if (retryBaseMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryBaseMs * (1L << Math.min(tentativa, 6)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
