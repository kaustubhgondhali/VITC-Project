package com.vitc.service.impl;

import com.vitc.entity.enums.RecoveryPortal;

/**
 * Plain HTML email bodies for password reset OTP delivery.
 * Cross-client compatible table layout with inline CSS for Gmail, Outlook, and mobile clients.
 */
public final class OtpEmailTemplates {

    private OtpEmailTemplates() {
    }

    private static final String BRAND = "#0b3d91";
    private static final String ACCENT = "#f7a41d";

    public static String buildOtpEmail(String recipientName,
                                       String otp,
                                       int expiresInMinutes,
                                       RecoveryPortal portal,
                                       String supportEmail) {
        String greetingName = (recipientName == null || recipientName.isBlank()) ? "User" : recipientName.trim();
        String portalLabel = switch (portal) {
            case ADMIN -> "Main Admin Panel";
            case TEACHER -> "Teacher Admin Portal";
            case STUDENT -> "Student Portal";
        };

        String body = """
                <h2 style="margin:0 0 12px;color:%1$s;font-size:20px;">Password Reset Verification</h2>
                <p style="margin:0 0 16px;">Hello <strong>%2$s</strong>,</p>
                <p style="margin:0 0 16px;">We received a request to reset your password for your <strong>%3$s</strong> account. Please use the 6-digit verification OTP below to complete the password recovery process.</p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="border:2px solid %4$s;border-radius:10px;margin:20px 0;background:#fffdf6;">
                  <tr><td style="padding:20px;text-align:center;">
                    <p style="margin:0 0 8px;font-size:13px;text-transform:uppercase;letter-spacing:1px;font-weight:700;color:%1$s;">Your One-Time Password (OTP)</p>
                    <p style="margin:0;font-size:32px;font-weight:700;letter-spacing:8px;color:#1b2436;font-family:'Courier New',Courier,monospace;">%5$s</p>
                    <p style="margin:12px 0 0;color:#8a5a00;font-size:13px;">
                      ⏰ This OTP is valid for <strong>%6$d minutes</strong> and can only be used once.
                    </p>
                  </td></tr>
                </table>

                <div style="background:#fef2f2;border-left:4px solid #ef4444;padding:12px 16px;border-radius:4px;margin-bottom:20px;">
                  <p style="margin:0;font-size:13px;color:#991b1b;font-weight:600;">
                    🛡️ Security Warning: Never share this OTP with anyone. VITC staff and administrators will never ask for your OTP or password.
                  </p>
                </div>

                <p style="margin:0 0 14px;color:#475569;font-size:13.5px;">
                  If you did not request a password reset, you can safely ignore this email. Your current password will remain unchanged.
                </p>

                <p style="margin:16px 0 0;color:#64748b;font-size:12.5px;border-top:1px solid #e2e8f0;padding-top:12px;">
                  Need assistance? Contact our support team at <a href="mailto:%7$s" style="color:%1$s;font-weight:600;">%7$s</a>.
                </p>
                """.formatted(BRAND, esc(greetingName), portalLabel, ACCENT, esc(otp), expiresInMinutes, esc(supportEmail));

        return wrap("Your Password Reset OTP — " + portalLabel, body);
    }

    private static String wrap(String preheader, String inner) {
        return """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#f4f6fb;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                  <span style="display:none;max-height:0;overflow:hidden;opacity:0;">%2$s</span>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6fb;">
                    <tr><td align="center" style="padding:28px 12px;">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                             style="max-width:600px;width:100%%;background:#ffffff;border-radius:14px;
                                    overflow:hidden;box-shadow:0 4px 12px rgba(0,0,0,0.06);color:#1b2436;">
                        <tr><td style="background:%1$s;padding:20px 24px;color:#ffffff;font-size:19px;font-weight:700;letter-spacing:0.5px;">
                          VITC &mdash; Vertex IT Career
                        </td></tr>
                        <tr><td style="padding:28px 24px;font-size:15px;line-height:1.6;">%3$s</td></tr>
                        <tr><td style="background:#f8fafc;padding:16px 24px;color:#94a3b8;font-size:12px;text-align:center;border-top:1px solid #edf2f7;">
                          &copy; Vertex IT Career. All rights reserved. Secure Password Recovery System.
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(BRAND, esc(preheader), inner);
    }

    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

