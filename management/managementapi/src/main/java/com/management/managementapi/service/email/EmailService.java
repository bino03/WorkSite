package com.management.managementapi.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import java.util.Objects;

import com.management.managementapi.model.email.EmailProvider;
import com.management.managementapi.repository.email.EmailProviderRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.dto.error.ErrorCode;

import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

@Service
@Slf4j
public class EmailService {

    private final EmailProviderRepository emailProviderRepository;

    public EmailService(EmailProviderRepository emailProviderRepository) {
        this.emailProviderRepository = emailProviderRepository;
    }

    public void sendInviteEmail(String toEmail, String inviteToken, String invitedByName) {
        try {
            EmailProvider provider = emailProviderRepository.findDefaultProvider()
                    .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Nenhum provedor de email configurado"));

            if (!provider.getIsActive()) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Provedor de email não está ativo");
            }

            JavaMailSenderImpl mailSender = createMailSender(provider);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(Objects.requireNonNull(provider.getFromEmail(), "fromEmail"),
                           Objects.requireNonNull(provider.getFromName(), "fromName"));
            helper.setTo(Objects.requireNonNull(toEmail, "toEmail"));
            helper.setSubject("Convite para aceder à plataforma");

            String htmlContent = buildInviteEmailHtml(inviteToken, invitedByName);
            helper.setText(Objects.requireNonNull(htmlContent, "htmlContent"), true);

            mailSender.send(message);
            log.info("Email de convite enviado para: {}", toEmail);

        } catch (Exception e) {
            log.error("Erro ao enviar email de convite para: {}", toEmail, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Falha ao enviar email de convite: " + e.getMessage());
        }
    }

    private JavaMailSenderImpl createMailSender(EmailProvider provider) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(provider.getHost());
        mailSender.setPort(provider.getPort());
        mailSender.setUsername(provider.getUsername());
        mailSender.setPassword(provider.getPassword());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");

        if ("tls".equalsIgnoreCase(provider.getEncryption())) {
            props.put("mail.smtp.starttls.enable", "true");
        } else if ("ssl".equalsIgnoreCase(provider.getEncryption())) {
            props.put("mail.smtp.ssl.enable", "true");
        }

        return mailSender;
    }

    private String buildInviteEmailHtml(String inviteToken, String invitedByName) {
        String frontendUrl = "http://localhost:5173"; // ou usa uma property
        String acceptUrl = frontendUrl + "/accept-invite?token=" + inviteToken;

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2>Convite para a Plataforma</h2>
                    <p>Olá,</p>
                    <p>%s convidou-te para aceder à plataforma de gestão.</p>
                    <p>Para aceitar o convite e criar a tua conta, clica no botão abaixo:</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background-color: #1890ff; color: white; padding: 12px 30px;
                                  text-decoration: none; border-radius: 4px; display: inline-block;">
                            Aceitar Convite
                        </a>
                    </div>
                    <p style="color: #666; font-size: 14px;">
                        Este convite expira em 24 horas.
                    </p>
                    <p style="color: #666; font-size: 14px;">
                        Se não solicitaste este convite, podes ignorar este email.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(invitedByName, acceptUrl);
    }
}
