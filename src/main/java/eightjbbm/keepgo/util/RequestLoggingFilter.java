package eightjbbm.keepgo.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        log.info(
                "[HTTP REQUEST] method={} uri={} remoteAddr={}",
                sanitize(request.getMethod()),
                sanitize(request.getRequestURI()),
                sanitize(request.getRemoteAddr()));

        filterChain.doFilter(request, response);
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\r', '_').replace('\n', '_');
    }
}
