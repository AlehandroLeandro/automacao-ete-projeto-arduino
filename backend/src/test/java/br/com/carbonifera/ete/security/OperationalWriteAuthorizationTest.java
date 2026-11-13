package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.lime.LimeController;
import br.com.carbonifera.ete.measurement.FlowMeasurementController;
import br.com.carbonifera.ete.measurement.PhMeasurementController;
import br.com.carbonifera.ete.stoppage.StoppageController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalWriteAuthorizationTest {

    @Test
    void operationalWriteEndpointsAllowOnlyOperatorAndAdmin() {
        List<Class<?>> controllers = List.of(
                PhMeasurementController.class,
                FlowMeasurementController.class,
                LimeController.class,
                StoppageController.class
        );

        List<Method> writeMethods = controllers.stream()
                .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
                .filter(this::isWriteEndpoint)
                .toList();

        assertThat(writeMethods).isNotEmpty();
        assertThat(writeMethods).allSatisfy(method -> {
            PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

            assertThat(preAuthorize).isNotNull();
            assertThat(preAuthorize.value()).contains("OPERATOR", "ADMIN");
            assertThat(preAuthorize.value()).doesNotContain("SUPERVISOR", "VIEWER");
        });
    }

    private boolean isWriteEndpoint(Method method) {
        return method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }
}
