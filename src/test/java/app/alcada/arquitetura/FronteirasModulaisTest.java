package app.alcada.arquitetura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Fronteira real entre módulos (ADR-0023, CLAUDE.md §5): nenhum módulo acessa
 * o {@code internal} de outro. O teste falha se qualquer classe fora de um
 * módulo depender de classes no {@code internal} dele.
 */
class FronteirasModulaisTest {

    /** Segmentos de pacote de cada módulo (domínio + plataforma). */
    private static final List<String> MODULOS = List.of(
            "identidade", "captura", "triagem", "autonomia", "regras",
            "esteira", "assistente", "metricas", "consulta", "notificacao",
            "plataforma.multitenancy", "plataforma.trilha",
            "plataforma.outbox", "plataforma.scheduler", "plataforma.gateway");

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("app.alcada");

    @Test
    void internals_nao_sao_acessados_de_fora_do_modulo() {
        for (String modulo : MODULOS) {
            String internal = "app.alcada." + modulo + ".internal..";
            String moduloTodo = "app.alcada." + modulo + "..";

            ArchRule regra = classes()
                    .that().resideInAPackage(internal)
                    .should().onlyHaveDependentClassesThat()
                    .resideInAnyPackage(moduloTodo)
                    .as("internal de '" + modulo + "' só é acessado dentro do próprio módulo")
                    .allowEmptyShould(true);

            regra.check(CLASSES);
        }
    }

    private static final List<String> DOMINIO = List.of(
            "identidade", "captura", "triagem", "autonomia", "regras",
            "esteira", "assistente", "notificacao", "metricas", "consulta");

    @Test
    void modulos_de_dominio_nao_dependem_de_internal_de_plataforma() {
        // Domínio fala com plataforma só pelas portas.
        for (String modulo : DOMINIO) {
            ArchRule regra = classes()
                    .that().resideInAPackage("app.alcada." + modulo + "..")
                    .should().onlyDependOnClassesThat()
                    .resideOutsideOfPackages("app.alcada.plataforma..internal..")
                    .as("'" + modulo + "' não depende de internal de plataforma")
                    .allowEmptyShould(true);
            regra.check(CLASSES);
        }
    }

    @Test
    void modulos_esperados_existem() {
        // Guarda simples: a lista de módulos não regrediu silenciosamente.
        assertTrue(MODULOS.size() == 15, "15 módulos esperados (10 domínio + 5 plataforma)");
    }
}
