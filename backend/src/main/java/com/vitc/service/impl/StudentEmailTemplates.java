package com.vitc.service.impl;

/**
 * Plain HTML email bodies. Kept table/inline-style based so they render in
 * Gmail, Outlook and mobile clients. No external CSS, no images required.
 */
final class StudentEmailTemplates {

    private StudentEmailTemplates() {
    }

    private static final String BRAND = "#0b3d91";
    private static final String ACCENT = "#f7a41d";

    static String credentials(String studentName,
                              String studentLoginId,
                              String temporaryPassword,
                              String courseTitle,
                              String orderCode,
                              String amount,
                              String loginUrl,
                              String supportEmail) {
        String body = """
                <h2 style="margin:0 0 12px;color:%1$s;font-size:20px;">Welcome to VITC, %2$s!</h2>
                <p style="margin:0 0 16px;">Your payment was successful and your student account is ready.</p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="border:1px solid #e3e8f0;border-radius:10px;margin:0 0 20px;">
                  <tr><td style="padding:16px 18px;">
                    <p style="margin:0 0 10px;font-weight:700;color:%1$s;">Course purchased</p>
                    <p style="margin:0 0 4px;">%3$s</p>
                    <p style="margin:0;color:#5a6a85;font-size:13px;">Order %4$s &nbsp;|&nbsp; Amount paid: %5$s</p>
                  </td></tr>
                </table>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="border:2px solid %6$s;border-radius:10px;margin:0 0 20px;background:#fffdf6;">
                  <tr><td style="padding:16px 18px;">
                    <p style="margin:0 0 10px;font-weight:700;color:%1$s;">Your Student Admin Login</p>
                    <p style="margin:0 0 6px;">Student Login ID: <strong>%7$s</strong></p>
                    <p style="margin:0 0 6px;">Temporary password: <strong>%8$s</strong></p>
                    <p style="margin:10px 0 0;color:#8a5a00;font-size:13px;">
                      For your security, please change this password right after your first login.
                    </p>
                  </td></tr>
                </table>

                <p style="margin:0 0 8px;font-weight:700;color:%1$s;">How to log in</p>
                <ol style="margin:0 0 20px;padding-left:20px;color:#33415c;">
                  <li>Open the VITC website and scroll to the footer.</li>
                  <li>Click the <strong>Student Admin Login</strong> button.</li>
                  <li>Enter your Student Login ID and the temporary password above.</li>
                  <li>Set a new password when prompted, then start your course.</li>
                </ol>

                <p style="margin:0 0 24px;">
                  <a href="%9$s" style="background:%1$s;color:#ffffff;text-decoration:none;padding:12px 22px;
                     border-radius:999px;display:inline-block;font-weight:700;">Go to Student Admin Login</a>
                </p>

                <p style="margin:0 0 6px;color:#5a6a85;font-size:13px;">
                  Never share your password with anyone. VITC staff will never ask you for it.
                </p>
                <p style="margin:0;color:#5a6a85;font-size:13px;">
                  Need help? Write to <a href="mailto:%10$s" style="color:%1$s;">%10$s</a>.
                </p>
                """.formatted(BRAND, esc(studentName), esc(courseTitle), esc(orderCode), esc(amount),
                ACCENT, esc(studentLoginId), esc(temporaryPassword), esc(loginUrl), esc(supportEmail));
        return wrap("Welcome to VITC — Your Student Account Details", body);
    }

    static String courseAdded(String studentName,
                              String studentLoginId,
                              String courseTitle,
                              String orderCode,
                              String amount,
                              String loginUrl,
                              String supportEmail) {
        String body = """
                <h2 style="margin:0 0 12px;color:%1$s;font-size:20px;">Hi %2$s, your new course is unlocked</h2>
                <p style="margin:0 0 16px;">Your payment was successful and the course has been added to your
                   existing VITC student account.</p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="border:1px solid #e3e8f0;border-radius:10px;margin:0 0 20px;">
                  <tr><td style="padding:16px 18px;">
                    <p style="margin:0 0 10px;font-weight:700;color:%1$s;">Course purchased</p>
                    <p style="margin:0 0 4px;">%3$s</p>
                    <p style="margin:0;color:#5a6a85;font-size:13px;">Order %4$s &nbsp;|&nbsp; Amount paid: %5$s</p>
                  </td></tr>
                </table>

                <p style="margin:0 0 16px;">Sign in with your existing Student Login ID
                   <strong>%6$s</strong> and your current password - it has not changed.</p>

                <p style="margin:0 0 24px;">
                  <a href="%7$s" style="background:%1$s;color:#ffffff;text-decoration:none;padding:12px 22px;
                     border-radius:999px;display:inline-block;font-weight:700;">Go to Student Admin Login</a>
                </p>

                <p style="margin:0;color:#5a6a85;font-size:13px;">
                  Forgot your password? Contact <a href="mailto:%8$s" style="color:%1$s;">%8$s</a>.
                </p>
                """.formatted(BRAND, esc(studentName), esc(courseTitle), esc(orderCode), esc(amount),
                esc(studentLoginId), esc(loginUrl), esc(supportEmail));
        return wrap("Your new VITC course is ready", body);
    }

    /** Part 4 - "forgot password" one-time reset code. */
    static String resetCode(String studentName,
                            String code,
                            long expiresInMinutes,
                            String loginUrl,
                            String supportEmail) {
        String greetingName = (studentName == null || studentName.isBlank()) ? "there" : studentName;
        String body = """
                <h2 style="margin:0 0 12px;color:%1$s;font-size:20px;">Reset your VITC Student Portal password</h2>
                <p style="margin:0 0 16px;">Hi %2$s, we received a request to reset the password for your
                   VITC Student Portal account. Use the code below to finish resetting it.</p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="border:2px solid %3$s;border-radius:10px;margin:0 0 20px;background:#fffdf6;">
                  <tr><td style="padding:16px 18px;text-align:center;">
                    <p style="margin:0 0 8px;font-weight:700;color:%1$s;">Your reset code</p>
                    <p style="margin:0;font-size:26px;font-weight:700;letter-spacing:4px;color:#1b2436;">%4$s</p>
                    <p style="margin:10px 0 0;color:#8a5a00;font-size:13px;">
                      This code expires in %5$s minutes and can be used only once.
                    </p>
                  </td></tr>
                </table>

                <p style="margin:0 0 20px;">
                  Go back to the Student Login page, choose <strong>Forgot Password</strong>, and enter this
                  code along with your new password.
                </p>

                <p style="margin:0 0 24px;">
                  <a href="%6$s" style="background:%1$s;color:#ffffff;text-decoration:none;padding:12px 22px;
                     border-radius:999px;display:inline-block;font-weight:700;">Go to Student Admin Login</a>
                </p>

                <p style="margin:0 0 6px;color:#5a6a85;font-size:13px;">
                  If you did not request this, you can safely ignore this email - your password will not change
                  unless this code is used.
                </p>
                <p style="margin:0;color:#5a6a85;font-size:13px;">
                  Need help? Write to <a href="mailto:%7$s" style="color:%1$s;">%7$s</a>.
                </p>
                """.formatted(BRAND, esc(greetingName), ACCENT, esc(code), expiresInMinutes,
                esc(loginUrl), esc(supportEmail));
        return wrap("Your VITC Student Portal password reset code", body);
    }

    /** Assignment purchase paid - the order is received and the project is being prepared. */
    static String assignmentConfirmed(String customerName,
                                      String assignmentTitle,
                                      String orderCode,
                                      String amount,
                                      String deliveryDays,
                                      String supportEmail) {
        String eta = (deliveryDays == null || deliveryDays.isBlank())
                ? "as soon as it is ready"
                : "within " + deliveryDays;
        String body = """
                <h2 style="margin:0 0 12px;color:%1$s;font-size:20px;">Thank you, %2$s - your order is confirmed</h2>
                <p style="margin:0 0 16px;">We have received your payment and our team has started preparing
                   your project.</p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="border:1px solid #e3e8f0;border-radius:10px;margin:0 0 20px;">
                  <tr><td style="padding:16px 18px;">
                    <p style="margin:0 0 10px;font-weight:700;color:%1$s;">Assignment purchased</p>
                    <p style="margin:0 0 4px;">%3$s</p>
                    <p style="margin:0;color:#5a6a85;font-size:13px;">Order %4$s &nbsp;|&nbsp; Amount paid: %5$s</p>
                  </td></tr>
                </table>

                <p style="margin:0 0 8px;font-weight:700;color:%1$s;">What happens next</p>
                <ol style="margin:0 0 20px;padding-left:20px;color:#33415c;">
                  <li>Our mentors prepare your project package: source code, documentation and setup guide.</li>
                  <li>You receive a second email with a private download link, %6$s.</li>
                  <li>Download the package and follow the setup guide to run the project.</li>
                </ol>

                <p style="margin:0 0 16px;">Keep your order ID <strong>%4$s</strong> handy for any support request.</p>
                <p style="margin:0;color:#5a6a85;font-size:13px;">
                  Questions about your project? Write to <a href="mailto:%7$s" style="color:%1$s;">%7$s</a>.
                </p>
                """.formatted(BRAND, esc(customerName), esc(assignmentTitle), esc(orderCode), esc(amount),
                esc(eta), esc(supportEmail));
        return wrap("Your VITC assignment order is confirmed", body);
    }

    /** The purchased assignment's project package is ready - carries the private download link. */
    static String assignmentDelivered(String customerName,
                                      String assignmentTitle,
                                      String orderCode,
                                      String fileName,
                                      String fileSize,
                                      String note,
                                      String downloadUrl,
                                      String expiresOn,
                                      String supportEmail) {
        String noteBlock = (note == null || note.isBlank()) ? "" : """
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="border:2px solid %1$s;border-radius:10px;margin:0 0 20px;background:#fffdf6;">
                  <tr><td style="padding:16px 18px;">
                    <p style="margin:0 0 8px;font-weight:700;color:%2$s;">A note from the VITC team</p>
                    <p style="margin:0;">%3$s</p>
                  </td></tr>
                </table>
                """.formatted(ACCENT, BRAND, esc(note).replace("\n", "<br>"));
        String body = """
                <h2 style="margin:0 0 12px;color:%1$s;font-size:20px;">Hi %2$s, your project is ready</h2>
                <p style="margin:0 0 16px;">The project package for your VITC order is ready to download.</p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="border:1px solid #e3e8f0;border-radius:10px;margin:0 0 20px;">
                  <tr><td style="padding:16px 18px;">
                    <p style="margin:0 0 10px;font-weight:700;color:%1$s;">%3$s</p>
                    <p style="margin:0 0 4px;">File: %5$s (%6$s)</p>
                    <p style="margin:0;color:#5a6a85;font-size:13px;">Order %4$s</p>
                  </td></tr>
                </table>

                %7$s

                <p style="margin:0 0 24px;">
                  <a href="%8$s" style="background:%1$s;color:#ffffff;text-decoration:none;padding:12px 22px;
                     border-radius:999px;display:inline-block;font-weight:700;">Download your project</a>
                </p>

                <p style="margin:0 0 6px;color:#5a6a85;font-size:13px;">
                  This private link is for you only and works until %9$s. Please do not forward it.
                  If it has expired, write to us and we will send a new one.
                </p>
                <p style="margin:0;color:#5a6a85;font-size:13px;">
                  Need help running the project? Write to <a href="mailto:%10$s" style="color:%1$s;">%10$s</a>.
                </p>
                """.formatted(BRAND, esc(customerName), esc(assignmentTitle), esc(orderCode), esc(fileName),
                esc(fileSize), noteBlock, esc(downloadUrl), esc(expiresOn), esc(supportEmail));
        return wrap("Your VITC project is ready to download", body);
    }

    /** Job Portal - the applicant's application was received. */
    static String jobApplicationReceived(String applicantName,
                                         String jobTitle,
                                         String companyName,
                                         String reference,
                                         String supportEmail) {
        String body = """
                <h2 style="margin:0 0 12px;color:%1$s;font-size:20px;">Thank you for applying, %2$s</h2>
                <p style="margin:0 0 16px;">We have received your application. Here is a copy for your records.</p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="border:1px solid #e3e8f0;border-radius:10px;margin:0 0 20px;">
                  <tr><td style="padding:16px 18px;">
                    <p style="margin:0 0 10px;font-weight:700;color:%1$s;">%3$s</p>
                    <p style="margin:0 0 4px;">%4$s</p>
                    <p style="margin:0;color:#5a6a85;font-size:13px;">Application number %5$s</p>
                  </td></tr>
                </table>

                <p style="margin:0 0 8px;font-weight:700;color:%1$s;">What happens next</p>
                <ol style="margin:0 0 20px;padding-left:20px;color:#33415c;">
                  <li>Our placement team reviews your profile and resume.</li>
                  <li>If you are shortlisted, we contact you by phone or email to schedule an interview.</li>
                  <li>You hear back from us about the outcome either way.</li>
                </ol>

                <p style="margin:0;color:#5a6a85;font-size:13px;">
                  Questions about your application? Write to <a href="mailto:%6$s" style="color:%1$s;">%6$s</a>
                  and mention your application number.
                </p>
                """.formatted(BRAND, esc(applicantName), esc(jobTitle), esc(companyName), esc(reference),
                esc(supportEmail));
        return wrap("We received your job application", body,
                "This is an automated message about your job application to VITC.");
    }

    /** Owner notification - an employer submitted the Job Portal "hire trained professionals" form. */
    static String employerEnquiryNotification(String reference,
                                              String companyName,
                                              String contactPerson,
                                              String email,
                                              String phone,
                                              String whatsappNumber,
                                              String jobTitle,
                                              String openings,
                                              String experience,
                                              String location,
                                              String skills,
                                              String message,
                                              String adminUrl) {
        StringBuilder rows = new StringBuilder();
        detailRow(rows, "Company", esc(companyName));
        detailRow(rows, "Contact person", esc(contactPerson));
        detailRow(rows, "Email", "<a href=\"mailto:" + esc(email) + "\" style=\"color:" + BRAND + ";\">" + esc(email) + "</a>");
        detailRow(rows, "Phone", "<a href=\"tel:" + esc(phone) + "\" style=\"color:" + BRAND + ";\">" + esc(phone) + "</a>");
        detailRow(rows, "Hiring for", esc(jobTitle));
        detailRow(rows, "Openings", esc(openings));
        detailRow(rows, "Experience", esc(experience));
        detailRow(rows, "Location", esc(location));
        detailRow(rows, "Skills", esc(skills));

        String replySubject = java.net.URLEncoder.encode("Your hiring enquiry with VITC (" + reference + ")",
                java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        String whatsappButton = whatsappNumber == null ? "" : """
                <a href="https://wa.me/%1$s" style="background:#25D366;color:#ffffff;text-decoration:none;padding:12px 22px;
                   border-radius:999px;display:inline-block;font-weight:700;margin:0 8px 8px 0;">Chat on WhatsApp</a>
                """.formatted(esc(whatsappNumber));

        String body = """
                <h2 style="margin:0 0 12px;color:%1$s;font-size:20px;">New employer enquiry</h2>
                <p style="margin:0 0 16px;"><strong>%2$s</strong> from <strong>%3$s</strong> wants to hire through VITC.
                   Enquiry %4$s, submitted on the Job Portal.</p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="border:1px solid #e3e8f0;border-radius:10px;margin:0 0 20px;">
                  %5$s
                </table>

                <p style="margin:0 0 8px;font-weight:700;color:%1$s;">Message</p>
                <p style="margin:0 0 22px;padding:14px 16px;background:#f4f6fb;border-radius:10px;">%6$s</p>

                <p style="margin:0 0 16px;">
                  <a href="mailto:%7$s?subject=%8$s" style="background:%1$s;color:#ffffff;text-decoration:none;padding:12px 22px;
                     border-radius:999px;display:inline-block;font-weight:700;margin:0 8px 8px 0;">Reply by email</a>
                  %9$s
                </p>
                <p style="margin:0;color:#5a6a85;font-size:13px;">
                  All enquiries are also listed in the admin panel:
                  <a href="%10$s" style="color:%1$s;">Admin &rarr; Job Portal &rarr; Employer Enquiries</a>.
                </p>
                """.formatted(BRAND, esc(contactPerson), esc(companyName), esc(reference), rows,
                esc(message).replace("\n", "<br>"), esc(email), replySubject, whatsappButton, esc(adminUrl));
        return wrap("New employer enquiry from " + companyName, body,
                "This is an automated notification from the VITC website.");
    }

    /** Owner notification - a visitor submitted the Enquiry Form (contact / course enquiry). */
    static String contactMessageNotification(String reference,
                                             String name,
                                             String email,
                                             String phone,
                                             String whatsappNumber,
                                             String subject,
                                             String message,
                                             String adminUrl) {
        StringBuilder rows = new StringBuilder();
        detailRow(rows, "Name", esc(name));
        detailRow(rows, "Email", "<a href=\"mailto:" + esc(email) + "\" style=\"color:" + BRAND + ";\">" + esc(email) + "</a>");
        if (phone != null && !phone.isBlank()) {
            detailRow(rows, "Phone", "<a href=\"tel:" + esc(phone) + "\" style=\"color:" + BRAND + ";\">" + esc(phone) + "</a>");
        }
        if (subject != null && !subject.isBlank()) {
            detailRow(rows, "Subject / Course", esc(subject));
        }

        String replySubject = java.net.URLEncoder.encode("Re: " + (subject != null && !subject.isBlank() ? subject : "Your enquiry with VITC") + " (" + reference + ")",
                java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        String whatsappButton = whatsappNumber == null ? "" : """
                <a href="https://wa.me/%1$s" style="background:#25D366;color:#ffffff;text-decoration:none;padding:12px 22px;
                   border-radius:999px;display:inline-block;font-weight:700;margin:0 8px 8px 0;">Chat on WhatsApp</a>
                """.formatted(esc(whatsappNumber));

        String body = """
                <h2 style="margin:0 0 12px;color:%1$s;font-size:20px;">New website enquiry</h2>
                <p style="margin:0 0 16px;"><strong>%2$s</strong> submitted a new enquiry (%3$s) on the VITC website.</p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="border:1px solid #e3e8f0;border-radius:10px;margin:0 0 20px;">
                  %4$s
                </table>

                <p style="margin:0 0 8px;font-weight:700;color:%1$s;">Message</p>
                <p style="margin:0 0 22px;padding:14px 16px;background:#f4f6fb;border-radius:10px;">%5$s</p>

                <p style="margin:0 0 16px;">
                  <a href="mailto:%6$s?subject=%7$s" style="background:%1$s;color:#ffffff;text-decoration:none;padding:12px 22px;
                     border-radius:999px;display:inline-block;font-weight:700;margin:0 8px 8px 0;">Reply by email</a>
                  %8$s
                </p>
                <p style="margin:0;color:#5a6a85;font-size:13px;">
                  View all activities in the admin dashboard:
                  <a href="%9$s" style="color:%1$s;">Admin Dashboard</a>.
                </p>
                """.formatted(BRAND, esc(name), esc(reference), rows,
                (message != null && !message.isBlank() ? esc(message).replace("\n", "<br>") : "<em>No message provided</em>"),
                esc(email), replySubject, whatsappButton, esc(adminUrl));
        return wrap("New website enquiry from " + name, body,
                "This is an automated notification from the VITC website.");
    }

    private static void detailRow(StringBuilder rows, String label, String valueHtml) {
        if (valueHtml == null || valueHtml.isBlank()) {
            return;
        }
        rows.append("<tr><td style=\"padding:9px 18px;color:#5a6a85;font-size:13px;width:140px;vertical-align:top;")
                .append("border-bottom:1px solid #eef1f6;\">").append(label).append("</td>")
                .append("<td style=\"padding:9px 18px;font-size:14px;border-bottom:1px solid #eef1f6;\">")
                .append(valueHtml).append("</td></tr>");
    }

    static String test(String loginUrl) {
        String body = """
                <h2 style="margin:0 0 12px;color:%1$s;font-size:20px;">SMTP test successful</h2>
                <p style="margin:0 0 16px;">If you can read this, VITC can send student credential emails.</p>
                <p style="margin:0;color:#5a6a85;font-size:13px;">Student portal: %2$s</p>
                """.formatted(BRAND, esc(loginUrl));
        return wrap("VITC email configuration test", body);
    }

    private static String wrap(String preheader, String inner) {
        return wrap(preheader, inner, "This is an automated message about your VITC purchase.");
    }

    private static String wrap(String preheader, String inner, String footer) {
        return """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#f4f6fb;">
                  <span style="display:none;max-height:0;overflow:hidden;opacity:0;">%2$s</span>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6fb;">
                    <tr><td align="center" style="padding:24px 12px;">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                             style="max-width:600px;width:100%%;background:#ffffff;border-radius:14px;
                                    overflow:hidden;font-family:Arial,Helvetica,sans-serif;color:#1b2436;">
                        <tr><td style="background:%1$s;padding:18px 24px;color:#ffffff;font-size:18px;font-weight:700;">
                          VITC &mdash; Vertex IT Career
                        </td></tr>
                        <tr><td style="padding:24px;font-size:15px;line-height:1.6;">%3$s</td></tr>
                        <tr><td style="background:#f4f6fb;padding:16px 24px;color:#7b879c;font-size:12px;">
                          %4$s
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(BRAND, esc(preheader), inner, esc(footer));
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
