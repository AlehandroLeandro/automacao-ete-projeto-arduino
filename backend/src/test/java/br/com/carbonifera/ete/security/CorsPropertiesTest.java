package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.config.CorsProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsPropertiesTest {

    @Test
    void originsReturnsTrimmedConfiguredOrigins() {
        CorsProperties properties = new CorsProperties("http://localhost:5174, https://ete.example.com ");

        assertThat(properties.origins())
                .containsExactlyElementsOf(List.of("http://localhost:5174", "https://ete.example.com"));
    }

    @Test
    void originsRejectsWildcardOrigin() {
        CorsProperties properties = new CorsProperties("http://localhost:5174,https://*.example.com");

        assertThatThrownBy(properties::origins)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CORS_ALLOWED_ORIGINS must list explicit origins");
    }

    @Test
    void originsRejectsEmptyOriginList() {
        CorsProperties properties = new CorsProperties(" , ");

        assertThatThrownBy(properties::origins)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CORS_ALLOWED_ORIGINS must list at least one origin");
    }
}
