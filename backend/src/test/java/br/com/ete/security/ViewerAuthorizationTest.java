package br.com.ete.security;

import br.com.ete.audit.AuditController;
import br.com.ete.lime.LimeController;
import br.com.ete.measurement.FlowMeasurementController;
import br.com.ete.measurement.PhMeasurementController;
import br.com.ete.report.ReportController;
import br.com.ete.stoppage.StoppageController;
import br.com.ete.user.UserController;
import br.com.ete.user.UserDirectoryController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ViewerAuthorizationTest {

    @Test
    void viewerCanReadFlowAndStoppages() {
        List<Method> readMethods = getMethods(FlowMeasurementController.class, StoppageController.class).stream()
                .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
                .toList();

        assertThat(readMethods).isNotEmpty();
        assertThat(readMethods).allSatisfy(method ->
                assertThat(method.getAnnotation(PreAuthorize.class).value()).contains("VIEWER"));
    }

    @Test
    void viewerCannotReadPhLimeAuditOrLimeStock() {
        List<Method> readMethods = getMethods(PhMeasurementController.class, LimeController.class, AuditController.class);

        assertThat(readMethods).isNotEmpty();
        assertThat(readMethods).allSatisfy(method -> {
            PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

            assertThat(preAuthorize).isNotNull();
            assertThat(preAuthorize.value()).doesNotContain("VIEWER");
        });
    }

    @Test
    void viewerCannotAccessReportsOrUsers() {
        List<Class<?>> controllers = List.of(ReportController.class, UserController.class, UserDirectoryController.class);

        assertThat(controllers).allSatisfy(controller -> {
            PreAuthorize preAuthorize = controller.getAnnotation(PreAuthorize.class);

            assertThat(preAuthorize).isNotNull();
            assertThat(preAuthorize.value()).doesNotContain("VIEWER");
        });
    }

    private List<Method> getMethods(Class<?>... controllers) {
        return Arrays.stream(controllers)
                .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .toList();
    }
}
