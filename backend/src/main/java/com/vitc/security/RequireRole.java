package com.vitc.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PART 11B-1 - declares, directly on the controller, which {@link Role}(s)
 * are allowed to call an endpoint. Enforced centrally by
 * {@link RoleAuthorizationInterceptor} so the same rule cannot be
 * accidentally duplicated (or forgotten) in individual controller methods.
 *
 * <p>Can be placed on a controller class (applies to every handler method in
 * it) or on an individual method, which overrides the class-level value.
 * Endpoints with no {@code @RequireRole} anywhere are left exactly as they
 * are today - this annotation only ever narrows a path that one of the
 * existing session interceptors has already authenticated; it never replaces
 * that authentication step.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequireRole {

    Role[] value();
}
