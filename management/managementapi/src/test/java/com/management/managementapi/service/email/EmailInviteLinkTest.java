package com.management.managementapi.service.email;

import com.management.managementapi.repository.email.EmailProviderRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O link do convite era construído sobre um `http://localhost:5173` hardcoded, o que
 * tornava o email inutilizável em produção — quem o recebesse ficava a apontar para a
 * própria máquina. O URL passa a vir de `app.frontend.url` (`APP_FRONTEND_URL`), e é
 * isso que estes testes fixam: o que sai no HTML é o que está configurado.
 */
@ExtendWith(MockitoExtension.class)
class EmailInviteLinkTest {

    @Mock private EmailProviderRepository emailProviderRepository;

    private static final String TOKEN = "1f0b2c3d-4e5f-6789-abcd-ef0123456789";

    private EmailService serviceWithFrontendUrl(String frontendUrl) {
        EmailService service = new EmailService(emailProviderRepository);
        ReflectionTestUtils.setField(service, "frontendUrl", frontendUrl);
        return service;
    }

    private String inviteHtml(EmailService service) {
        return ReflectionTestUtils.invokeMethod(service, "buildInviteEmailHtml", TOKEN, "Ana");
    }

    @Test
    @DisplayName("o link do convite usa o domínio configurado, não localhost")
    void usaDominioConfigurado() {
        String html = inviteHtml(serviceWithFrontendUrl("https://worksite.example.com"));

        assertThat(html).contains("https://worksite.example.com/accept-invite?token=" + TOKEN);
        assertThat(html).doesNotContain("localhost");
    }

    @Test
    @DisplayName("o token do convite viaja no link e o nome de quem convidou aparece no corpo")
    void incluiTokenEQuemConvidou() {
        String html = inviteHtml(serviceWithFrontendUrl("http://localhost:5173"));

        assertThat(html).contains("/accept-invite?token=" + TOKEN);
        assertThat(html).contains("Ana convidou-te");
    }
}
