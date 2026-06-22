package com.team6.minidiscord.auth;

import com.team6.minidiscord.common.error.ApiException;
import com.team6.minidiscord.common.error.ErrorCode;
import com.team6.minidiscord.user.UserDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetEmailService {
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public PasswordResetEmailService(
            JavaMailSender mailSender,
            @Value("${app.password-reset.mail-from}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendResetLink(UserDocument user, String resetLink, long expiresInMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(user.email);
        message.setSubject("Mini Discord password reset");
        message.setText("""
                We received a request to reset your Mini Discord password.

                Open this link to set a new password:
                %s

                This link expires in %d minutes. If you did not request this, you can ignore this email.
                """.formatted(resetLink, expiresInMinutes));
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Không thể gửi email đặt lại mật khẩu. Vui lòng kiểm tra cấu hình SMTP.");
        }
    }
}
