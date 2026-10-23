package com.testhub.common.security;

import com.testhub.common.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * Default-deny authorization for {@code /api/**}: every endpoint requires a
 * principal unless annotated with {@link PublicApi}, and {@link RequireRole}
 * further restricts the role set. Internal endpoints require the service
 * principal (established by the service token in {@link AuthFilter}).
 */
public class RoleInterceptor implements HandlerInterceptor {

    private final boolean internalEndpoints;

    public RoleInterceptor(boolean internalEndpoints) {
        this.internalEndpoints = internalEndpoints;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        if (isPublic(method)) {
            return true;
        }
        AuthPrincipal principal = CurrentUser.get();
        if (internalEndpoints) {
            if (principal == null || !"service".equals(principal.userId())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "SERVICE_TOKEN_REQUIRED", "内部接口需要服务令牌");
            }
            return true;
        }
        if (principal == null) {
            throw ApiException.unauthorized("请先登录");
        }
        RequireRole required = method.getMethodAnnotation(RequireRole.class);
        if (required == null) {
            required = method.getBeanType().getAnnotation(RequireRole.class);
        }
        if (required != null && Arrays.stream(required.value()).noneMatch(role -> role == principal.role())) {
            throw ApiException.forbidden("当前角色无权执行该操作");
        }
        return true;
    }

    private boolean isPublic(HandlerMethod method) {
        return method.hasMethodAnnotation(PublicApi.class)
                || method.getBeanType().isAnnotationPresent(PublicApi.class);
    }
}
