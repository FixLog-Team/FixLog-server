package com.fixlog.application.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendInvitationEmail(String toEmail, String token, String inviterName, String workspaceName) {
        try {
            String inviteLink = frontendUrl + "/invite/" + token;
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("[FixLog] " + workspaceName + " 워크스페이스에 초대되었습니다");
            helper.setText(buildHtml(inviterName, workspaceName, inviteLink), true);
            mailSender.send(message);
            log.info("[MAIL] 초대 이메일 발송 완료 to={}", toEmail);
        } catch (Exception e) {
            log.error("[MAIL] 초대 이메일 발송 실패 to={} reason={}", toEmail, e.getMessage(), e);
        }
    }

    private String buildHtml(String inviterName, String workspaceName, String inviteLink) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: sans-serif; background:#f5f5f5; padding:40px;">
                  <div style="max-width:480px; margin:0 auto; background:#fff; border-radius:8px; padding:32px;">
                    <h2 style="color:#1a1a1a; margin-bottom:8px;">워크스페이스 초대</h2>
                    <p style="color:#555;">
                      <strong>%s</strong>님이 <strong>%s</strong> 워크스페이스에 초대했습니다.
                    </p>
                    <a href="%s"
                       style="display:inline-block; margin-top:24px; padding:12px 24px;
                              background:#4F46E5; color:#fff; border-radius:6px;
                              text-decoration:none; font-weight:bold;">
                      초대 수락하기
                    </a>
                    <p style="margin-top:24px; color:#999; font-size:12px;">
                      이 초대 링크는 7일 후 만료됩니다.<br>
                      본인이 요청하지 않았다면 이 메일을 무시하세요.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(inviterName, workspaceName, inviteLink);
    }
}
