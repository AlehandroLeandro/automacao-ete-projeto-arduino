package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.report.ReportController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class ReportAuthorizationTest {

    @Test
    void reportControllerAllowsOnlySupervisorAndAdmin() {
        PreAuthorize preAuthorize = ReportController.class.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains("SUPERVISOR", "ADMIN");
        assertThat(preAuthorize.value()).doesNotContain("OPERATOR");
    }
}
