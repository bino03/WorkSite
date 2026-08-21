package com.management.managementapi.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

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

    /**
     * Base dos links enviados por email. Configurável por `APP_FRONTEND_URL` — em produção
     * tem de apontar para o domínio real do Backoffice, senão o convite sai a apontar para
     * localhost e é inutilizável.
     */
    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public EmailService(EmailProviderRepository emailProviderRepository) {
        this.emailProviderRepository = emailProviderRepository;
    }

    // ── emails do produto ─────────────────────────────────────

    public void sendInviteEmail(String toEmail, String inviteToken, String invitedByName) {
        send(defaultProvider(), toEmail, "Convite para aceder à plataforma",
                buildInviteEmailHtml(inviteToken, invitedByName));
        log.info("Email de convite enviado para: {}", toEmail);
    }

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        send(defaultProvider(), toEmail, "Recuperação de password",
                buildPasswordResetEmailHtml(resetToken));
        log.info("Email de recuperação de password enviado para: {}", toEmail);
    }

    /**
     * Email de teste de um provedor <b>específico</b> — o ponto é confirmar aquelas
     * credenciais, por isso não passa pelo predefinido nem exige que o provedor
     * esteja ativo.
     */
    public void sendTestEmail(EmailProvider provider, String toEmail) {
        send(provider, toEmail, "Teste de configuração de email", buildTestEmailHtml(provider));
        log.info("Email de teste enviado para: {} via provedor {}", toEmail, provider.getProviderName());
    }

    // ── envio ─────────────────────────────────────────────────

    /**
     * O provedor a usar para os emails do produto.
     *
     * Fora do try/catch do {@link #send} de propósito: não haver provedor nenhum, ou o
     * predefinido estar desligado, é configuração em falta — não uma falha de envio — e a
     * mensagem que chega ao Backoffice tem de o dizer. Antes ficava tudo no mesmo
     * try/catch e saía como "erro interno do servidor", que não diz a ninguém que falta
     * configurar o SMTP.
     */
    private EmailProvider defaultProvider() {
        EmailProvider provider = emailProviderRepository.findDefaultProvider()
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_PROVIDER_NONE_CONFIGURED));

        if (!Boolean.TRUE.equals(provider.getIsActive())) {
            throw new BusinessException(ErrorCode.EMAIL_PROVIDER_INACTIVE);
        }
        return provider;
    }

    private void send(EmailProvider provider, String toEmail, String subject, String htmlContent) {
        try {
            JavaMailSenderImpl mailSender = createMailSender(provider);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // `from_name` é nullable na V7 — sem este fallback, um provedor gravado sem
            // nome de remetente rebentava aqui em vez de enviar.
            String fromName = provider.getFromName() != null && !provider.getFromName().isBlank()
                    ? provider.getFromName()
                    : provider.getFromEmail();

            helper.setFrom(provider.getFromEmail(), fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);

        } catch (Exception e) {
            log.error("Erro ao enviar email para: {}", toEmail, e);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED,
                    "Falha ao enviar email: " + e.getMessage());
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

    // ── corpos ────────────────────────────────────────────────

    private String buildInviteEmailHtml(String inviteToken, String invitedByName) {
        String acceptUrl = frontendUrl + "/accept-invite?token=" + inviteToken;

        return layout("Convite para a Plataforma", """
                <p>Olá,</p>
                <p>%s convidou-te para aceder à plataforma de gestão.</p>
                <p>Para aceitar o convite e criar a tua conta, clica no botão abaixo:</p>
                %s
                <p style="color: #666; font-size: 14px;">
                    Este convite expira em 24 horas.
                </p>
                <p style="color: #666; font-size: 14px;">
                    Se não solicitaste este convite, podes ignorar este email.
                </p>
                """.formatted(invitedByName, button(acceptUrl, "Aceitar Convite")));
    }

    private String buildPasswordResetEmailHtml(String resetToken) {
        String resetUrl = frontendUrl + "/reset-password?token=" + resetToken;

        return layout("Recuperação de Password", """
                <p>Olá,</p>
                <p>Foi pedida a recuperação da password da tua conta.</p>
                <p>Para definires uma password nova, clica no botão abaixo:</p>
                %s
                <p style="color: #666; font-size: 14px;">
                    Este link expira em 1 hora e só pode ser usado uma vez.
                </p>
                <p style="color: #666; font-size: 14px;">
                    Se não foste tu que pediste, ignora este email — a password atual continua válida.
                </p>
                """.formatted(button(resetUrl, "Definir nova password")));
    }

    private String buildTestEmailHtml(EmailProvider provider) {
        return layout("Teste de Configuração de Email", """
                <p>Este email confirma que o provedor <strong>%s</strong> (%s:%d) está a
                conseguir enviar.</p>
                <p style="color: #666; font-size: 14px;">
                    Enviado a partir das Definições do Backoffice. Não é preciso responder.
                </p>
                """.formatted(provider.getProviderName(), provider.getHost(), provider.getPort()));
    }

    private String button(String url, String label) {
        return """
                <div style="text-align: center; margin: 30px 0;">
                    <a href="%s"
                       style="background-color: #1890ff; color: white; padding: 12px 30px;
                              text-decoration: none; border-radius: 4px; display: inline-block;">
                        %s
                    </a>
                </div>
                """.formatted(url, label);
    }

    private String layout(String title, String body) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2>%s</h2>
                    %s
                </div>
            </body>
            </html>
            """.formatted(title, body);
    }
}
