package com.vitc.controller;

import com.vitc.entity.Admin;
import com.vitc.mail.google.GmailOAuthService;
import com.vitc.mail.google.GoogleAuthException;
import com.vitc.repository.AdminRepository;
import com.vitc.security.CurrentUser;
import com.vitc.security.CurrentUserContext;
import com.vitc.security.Role;
import io.swagger.v3.oas.annotations.Hidden;
import java.net.URI;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Google sends the administrator's browser after the "Connect Google Account" consent screen.
 *
 * <p>This endpoint has to be public - it is a plain top-level navigation from Google, so there
 * are no admin session headers to authenticate it with. It is safe because it authenticates the
 * <em>flow</em> rather than the request: the {@code state} parameter is a single-use value this
 * server signed when the admin clicked Connect, and nothing happens unless it verifies. An attacker
 * who calls this URL directly, replays an old one, or supplies their own authorization code cannot
 * produce a valid state and is simply bounced back with an error.</p>
 *
 * <p>Nothing sensitive travels onward: the authorization code is consumed here and the browser is
 * redirected to the configured admin page with a one-word outcome - never a token, never a code,
 * and never a URL taken from the request.</p>
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/api/v1/public/email/google")
public class PublicGoogleOAuthCallbackController {

    private final GmailOAuthService gmailOAuth;
    private final AdminRepository adminRepository;
    private final String frontendUrl;

    public PublicGoogleOAuthCallbackController(GmailOAuthService gmailOAuth, AdminRepository adminRepository,
                                               @Value("${app.frontend-url:http://localhost:5500}") String frontendUrl) {
        this.gmailOAuth = gmailOAuth;
        this.adminRepository = adminRepository;
        this.frontendUrl = frontendUrl;
    }

    /**
     * @param code  Google's one-time authorization code - exchanged immediately, never stored or logged
     * @param state the signed single-use value issued when the admin clicked Connect
     * @param error set by Google when the admin refused consent
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        if (error != null && !error.isBlank()) {
            // "access_denied" is the normal outcome of clicking Cancel on the consent screen.
            log.info("Google authorisation was not granted (Google reported: {})", safe(error));
            return redirect("denied");
        }

        // The state is checked before the code is touched: an unverified callback must not cause
        // a token exchange, which is what makes a forged or replayed callback harmless.
        Optional<Long> adminId = gmailOAuth.consumeState(state);
        if (adminId.isEmpty()) {
            log.warn("Google callback rejected: the state value was missing, expired, already used or invalid.");
            return redirect("invalid_state");
        }
        if (code == null || code.isBlank()) {
            return redirect("invalid_callback");
        }
        Optional<Admin> admin = adminRepository.findById(adminId.get()).filter(a -> Boolean.TRUE.equals(a.getActive()));
        if (admin.isEmpty()) {
            return redirect("invalid_state");
        }

        try {
            // Credit the audit record to the admin who started the flow (cleared by CurrentUserContextFilter).
            CurrentUserContext.set(new CurrentUser(Role.MAIN_ADMIN, admin.get().getId(), admin.get().getUsername()));
            gmailOAuth.completeConnection(code, admin.get());
            return redirect("connected");
        } catch (GoogleAuthException e) {
            // The message is safe, but it is not passed through the URL either: the screen
            // re-reads the real status from the authenticated API instead.
            log.warn("Could not complete the Google connection: {}", e.getMessage());
            return redirect("failed");
        } catch (RuntimeException e) {
            log.error("Unexpected failure completing the Google connection ({})", e.getClass().getSimpleName());
            return redirect("failed");
        } finally {
            CurrentUserContext.clear();
        }
    }

    /**
     * Back to the admin screen. The destination comes from this server's own configuration
     * (app.frontend-url), never from a request parameter, so this cannot become an open redirect.
     */
    private ResponseEntity<Void> redirect(String outcome) {
        String base = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(base + "/admin/smtp-settings.html?google=" + outcome))
                .build();
    }

    /** Google's error codes are short and fixed; this stops anything else reaching the log. */
    private static String safe(String value) {
        String cleaned = value.replaceAll("[^A-Za-z0-9_\\-]", "");
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }
}
