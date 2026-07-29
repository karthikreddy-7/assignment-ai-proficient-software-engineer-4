package com.schwab.urlshortener.validation;

import com.schwab.urlshortener.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** SSRF/open-redirect guard: blocks disallowed schemes and loopback/private/link-local IPs. */
@Component
public class UrlSafetyValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    public void validate(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ValidationException("longUrl is not a valid URI");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new ValidationException("longUrl must use http or https");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ValidationException("longUrl must include a host");
        }

        if (isDisallowedHost(host)) {
            throw new ValidationException("longUrl targets a disallowed host");
        }
    }

    private boolean isDisallowedHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.equals("localhost")) {
            return true;
        }
        if (!isIpLiteral(normalized)) {
            return false;
        }
        try {
            // No DNS lookup: host is already a numeric literal here.
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                    || addr.isSiteLocalAddress() || addr.isAnyLocalAddress();
        } catch (UnknownHostException e) {
            return true; // malformed IP literal - reject conservatively
        }
    }

    private boolean isIpLiteral(String host) {
        return IPV4_LITERAL.matcher(host).matches() || host.contains(":");
    }
}
