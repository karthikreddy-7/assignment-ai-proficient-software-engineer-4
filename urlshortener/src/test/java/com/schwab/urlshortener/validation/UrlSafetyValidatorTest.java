package com.schwab.urlshortener.validation;

import com.schwab.urlshortener.exception.ValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlSafetyValidatorTest {

    private final UrlSafetyValidator validator = new UrlSafetyValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com",
            "https://example.com/path?query=1",
            "https://sub.example.com:8443/a/b/c"
    })
    void acceptsValidHttpAndHttpsUrls(String url) {
        assertThatCode(() -> validator.validate(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "file:///etc/passwd",
            "data:text/html,<script>alert(1)</script>",
            "ftp://example.com/file"
    })
    void rejectsDisallowedSchemes(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost",
            "http://localhost:8080/admin",
            "http://127.0.0.1",
            "http://127.0.0.1:9000/internal",
            "http://10.0.0.5",
            "http://172.16.0.1",
            "http://192.168.1.1",
            "http://169.254.169.254/latest/meta-data",
            "http://0.0.0.0"
    })
    void rejectsLoopbackPrivateAndLinkLocalTargets(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not a url",
            "http://",
            "http:///path-no-host"
    })
    void rejectsMalformedOrHostlessUrls(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(ValidationException.class);
    }
}
