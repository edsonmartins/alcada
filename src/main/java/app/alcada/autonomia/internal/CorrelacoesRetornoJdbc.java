package app.alcada.autonomia.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import app.alcada.autonomia.port.CorrelacoesRetorno;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Token HMAC regenerável: o valor claro nunca é persistido. */
@ApplicationScoped
public class CorrelacoesRetornoJdbc implements CorrelacoesRetorno {
    private final EntityManager em;
    private final Trilha trilha;
    private final String segredo;

    public CorrelacoesRetornoJdbc(EntityManager em, Trilha trilha,
            @ConfigProperty(name="alcada.correlacao.segredo") Optional<String> segredo) {
        this.em=em; this.trilha=trilha;
        this.segredo=segredo.filter(s -> s.length() >= 32).orElse(null);
    }

    @Override @Transactional
    public void criar(OrgId org, UUID delegacaoId, String canal, String destino, OffsetDateTime expiraEm) {
        criar(org, delegacaoId, null, canal, destino, expiraEm);
    }

    @Override @Transactional
    public void criarParaPedido(OrgId org, UUID pedidoId, String canal, String destino, OffsetDateTime expiraEm) {
        criar(org, null, pedidoId, canal, destino, expiraEm);
    }

    private void criar(OrgId org, UUID delegacaoId, UUID pedidoId, String canal, String destino,
                       OffsetDateTime expiraEm) {
        exigirSegredo();
        UUID id=UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO correlacao_retorno
                    (id,org_id,delegacao_id,pedido_informacao_id,token_hash,canal,destino_hash,expira_em)
                VALUES (?,?,?,?,?::bytea,?,?::bytea,?) ON CONFLICT DO NOTHING
                """).setParameter(1,id).setParameter(2,org.valor()).setParameter(3,delegacaoId)
                .setParameter(4,pedidoId).setParameter(5,hex(hash(derivar(org,id)))).setParameter(6,canal)
                .setParameter(7,hex(hash(normalizar(canal,destino)))).setParameter(8,expiraEm).executeUpdate();
    }

    @Override
    public Optional<String> tokenParaEnvio(OrgId org,UUID delegacaoId) {
        return token(org, "delegacao_id", delegacaoId);
    }

    @Override
    public Optional<String> tokenParaPedido(OrgId org, UUID pedidoId) {
        return token(org, "pedido_informacao_id", pedidoId);
    }

    private Optional<String> token(OrgId org, String coluna, UUID alvoId) {
        exigirSegredo();
        @SuppressWarnings("unchecked") List<Object> ids=em.createNativeQuery("""
                SELECT id FROM correlacao_retorno WHERE org_id=? AND %s=?
                  AND revogada_em IS NULL AND expira_em>now()
                """.formatted(coluna)).setParameter(1,org.valor()).setParameter(2,alvoId).getResultList();
        return ids.isEmpty()?Optional.empty():Optional.of(derivar(org,UUID.fromString(ids.getFirst().toString())));
    }

    @Override @Transactional
    public Resultado receber(OrgId org,String token,String canal,String autor,String mensagemId,String trecho) {
        return receberDetalhado(org, token, canal, autor, mensagemId, trecho).resultado();
    }

    @Override @Transactional
    public Recepcao receberDetalhado(OrgId org,String token,String canal,String autor,String mensagemId,String trecho) {
        if(vazio(token)||vazio(mensagemId)) return new Recepcao(Resultado.NAO_CORRELACIONADO, null);
        exigirSegredo();
        @SuppressWarnings("unchecked") List<Object[]> rs=em.createNativeQuery("""
                SELECT c.delegacao_id,c.canal,c.destino_hash,
                       COALESCE(d.pendencia_id,p.pendencia_id),c.pedido_informacao_id
                FROM correlacao_retorno c
                LEFT JOIN delegacao d ON d.org_id=c.org_id AND d.id=c.delegacao_id
                LEFT JOIN pedido_informacao p ON p.org_id=c.org_id AND p.id=c.pedido_informacao_id
                WHERE c.org_id=? AND c.token_hash=?::bytea AND c.revogada_em IS NULL AND c.expira_em>now()
                """).setParameter(1,org.valor()).setParameter(2,hex(hash(token))).getResultList();
        if(rs.size()!=1) return new Recepcao(Resultado.NAO_CORRELACIONADO, null);
        Object[] r=rs.getFirst();
        if(!String.valueOf(r[1]).equalsIgnoreCase(canal)||vazio(autor)
                ||!MessageDigest.isEqual((byte[])r[2],hash(normalizar(canal,autor))))
            return new Recepcao(Resultado.AUTOR_DIVERGENTE, null);
        UUID id=UUID.randomUUID();
        int inseriu=em.createNativeQuery("""
                INSERT INTO retorno_delegacao
                    (id,org_id,delegacao_id,pedido_informacao_id,mensagem_id_hash,tipo,trecho_minimizado,estado)
                VALUES (?,?,?,?,?::bytea,'INCONCLUSIVO',?,'OBSERVADO')
                ON CONFLICT (org_id,mensagem_id_hash) DO NOTHING
                """).setParameter(1,id).setParameter(2,org.valor()).setParameter(3,r[0])
                .setParameter(4,r[4]).setParameter(5,hex(hash(mensagemId)))
                .setParameter(6,minimizar(trecho)).executeUpdate();
        UUID pedidoId = r[4] == null ? null : (UUID) r[4];
        if(inseriu==0) return new Recepcao(Resultado.REPETIDO, pedidoId);
        trilha.registrar(new EventoTrilha(org,(UUID)r[3],TipoEvento.RETORNO_RECEBIDO,
                Ator.sistemaMotor("retorno"),null,null,null,
                "{\"alvo\":\""+(pedidoId == null ? "DELEGACAO" : "PEDIDO_INFORMACAO")
                        +"\",\"tipo\":\"INCONCLUSIVO\"}"));
        return new Recepcao(Resultado.OBSERVADO, pedidoId);
    }

    private String derivar(OrgId org,UUID id){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return Base64.getUrlEncoder().withoutPadding().encodeToString(m.doFinal((org.valor()+":"+id).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("falha ao gerar correlação",e);}}
    private void exigirSegredo(){if(segredo==null)throw new IllegalStateException("alcada.correlacao.segredo exige ao menos 32 caracteres");}
    private static byte[] hash(String v){try{return MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String hex(byte[] b){return "\\x"+HexFormat.of().formatHex(b);}
    private static String normalizar(String canal,String v){String s=v==null?"":v.trim().toLowerCase();return "WHATSAPP".equalsIgnoreCase(canal)?s.replaceAll("\\D",""):s;}
    private static String minimizar(String s){String v=vazio(s)?"[sem texto]":s;v=v.replaceAll("(?i)[\\w.+-]+@[\\w-]+\\.[\\w.-]+","<EMAIL>").replaceAll("\\b\\d{10,14}\\b","<TELEFONE>");return v.substring(0,Math.min(1000,v.length()));}
    private static boolean vazio(String s){return s==null||s.isBlank();}
}
