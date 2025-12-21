package com.xiaozhi.utils;

import io.github.biezhi.ome.OhMyEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static io.github.biezhi.ome.OhMyEmail.SMTP_QQ;

/**
 * 邮件发送工具类
 * 
 * @author Joey
 */
@Component
public class EmailUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailUtils.class);
    
    @Value("${email.smtp.username}")
    private String emailUsername;
    
    @Value("${email.smtp.password}")
    private String emailPassword;
    
    /**
     * 发送邮件
     * 
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @return 发送结果
     */
    public boolean sendEmail(String to, String subject, String content) {
        return sendEmail(to, subject, content, "Платформа управления Xiaozhi IoT");
    }
    
    /**
     * 发送邮件
     * 
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @param fromName 发件人名称
     * @return 发送结果
     */
    public boolean sendEmail(String to, String subject, String content, String fromName) {
        try {
            // 验证邮箱格式
            if (!isValidEmail(to)) {
                logger.error("Неверный формат электронной почты: {}", to);
                return false;
            }
            
            // 检查邮箱配置
            if (!StringUtils.hasText(emailUsername) || !StringUtils.hasText(emailPassword)) {
                logger.error("Не настроена информация для аутентификации сторонней электронной почты");
                return false;
            }
            
            // 配置邮件发送
            OhMyEmail.config(SMTP_QQ(false), emailUsername, emailPassword);
            
            // 发送邮件
            OhMyEmail.subject(subject)
                    .from(fromName)
                    .to(to)
                    .html(content)
                    .send();
            
            logger.info("Письмо успешно отправлено: {} -> {}", fromName, to);
            return true;
            
        } catch (Exception e) {
            String errorMsg = getErrorMessage(e);
            logger.error("Не удалось отправить письмо: {} -> {}, ошибка: {}", fromName, to, errorMsg, e);
            return false;
        }
    }
    
    /**
     * 发送验证码邮件
     * 
     * @param to 收件人邮箱
     * @param code 验证码
     * @return 发送结果
     */
    public boolean sendCaptchaEmail(String to, String code) {
        String subject = "Xiaozhi ESP32 - Платформа управления умным интернетом вещей";
        String content = "Уважаемый пользователь! Ваш код подтверждения: <h3>" + code + "</h3>Если это не вы, проигнорируйте это письмо. (Срок действия 10 минут)";
        return sendEmail(to, subject, content);
    }
        
    /**
     * 简单验证邮箱格式
     * 
     * @param email 邮箱地址
     * @return 是否有效
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // 简单的邮箱格式验证，包含@符号且@后面有.
        return email.matches("^[^@]+@[^@]+\\.[^@]+$");
    }
    
    /**
     * 根据异常类型获取错误信息
     * 
     * @param e 异常
     * @return 错误信息
     */
    private String getErrorMessage(Exception e) {
        if (e.getMessage() == null) {
            return "Отправка не удалась";
        }
        
        String message = e.getMessage();
        if (message.contains("non-existent account") ||
                message.contains("550") ||
                message.contains("recipient")) {
            return "Адрес электронной почты не существует или недействителен";
        } else if (message.contains("Authentication failed")) {
            return "Ошибка аутентификации почтового сервиса, пожалуйста, свяжитесь с администратором";
        } else if (message.contains("timed out")) {
            return "Превышено время ожидания отправки письма, пожалуйста, попробуйте позже";
        }
        
        return "Отправка не удалась: " + message;
    }
}
