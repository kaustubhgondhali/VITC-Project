package com.vitc.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * PART 11B-1 - runs first (before any interceptor) and last (after the whole
 * chain, success or failure) for every request, guaranteeing
 * {@link CurrentUserContext} never leaks between requests on a pooled thread.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CurrentUserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            CurrentUserContext.clear();
        }
    }
}
