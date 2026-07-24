package com.fundtracker.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailUsername;

    /** 生成 6 位数字验证码 */
    public String generateCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    /**
     * 发送验证码邮件
     * @param to 收件人邮箱
     * @param code 6 位验证码
     * @param type 验证码用途
     */
    public void sendVerificationCode(String to, String code, String type) {
        String subject;
        String content;

        switch (type) {
            case "login":
                subject = "种树 - 登录验证码";
                content = "您正在登录种树账号，验证码为：<b>" + code + "</b>，5 分钟内有效。如非本人操作，请忽略。";
                break;
            case "register":
                subject = "种树 - 注册验证码";
                content = "您正在注册种树账号，验证码为：<b>" + code + "</b>，5 分钟内有效。如非本人操作，请忽略。";
                break;
            case "set_password":
                subject = "种树 - 设置密码验证码";
                content = "您正在设置种树账号密码，验证码为：<b>" + code + "</b>，5 分钟内有效。如非本人操作，请忽略。";
                break;
            default:
                subject = "种树 - 验证码";
                content = "您的验证码为：<b>" + code + "</b>，5 分钟内有效。";
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailUsername);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true); // true = HTML
            mailSender.send(message);
            log.info("验证码邮件已发送至 {}", to);
        } catch (MessagingException e) {
            log.error("发送验证码邮件失败: to={}, error={}", to, e.getMessage());
            throw new RuntimeException("发送验证码失败，请检查邮箱地址或稍后重试", e);
        }
    }
}
