package cn.planet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

final class AccountRequestLimit extends OncePerRequestFilter {
    private static final Map<String, Integer> LIMITS = Map.of(
        "/api/account/login", 12,
        "/api/account/register", 6,
        "/api/account/password-reset", 4,
        "/api/account/password-reset/confirm", 8);
    private final Map<String, Integer> requests = new HashMap<>();
    private long window;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Integer limit = LIMITS.get(path);
        if ("POST".equals(request.getMethod()) && limit != null &&
                !allow(request.getRemoteAddr() + "|" + path, limit)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            return;
        }
        chain.doFilter(request, response);
    }

    private synchronized boolean allow(String key, int limit) {
        long minute = Instant.now().getEpochSecond() / 60;
        if (minute != window) {
            requests.clear();
            window = minute;
        }
        Integer count = requests.get(key);
        if ((count == null && requests.size() >= 4096) || (count != null && count >= limit)) return false;
        requests.put(key, count == null ? 1 : count + 1);
        return true;
    }
}
