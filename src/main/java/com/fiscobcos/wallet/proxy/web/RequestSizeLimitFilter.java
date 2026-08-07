package com.fiscobcos.wallet.proxy.web;

import com.fiscobcos.wallet.proxy.config.ProxyProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final ProxyProperties properties;

    public RequestSizeLimitFilter(ProxyProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long maxBytes = properties.getMaxRequestBytes();
        long contentLength = request.getContentLengthLong();
        if (maxBytes > 0 && contentLength > maxBytes) {
            response.sendError(
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body too large");
            return;
        }
        chain.doFilter(request, response);
    }
}

