package com.xiaozhi.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 验证码发送工具类
 * 统一管理邮件和短信验证码发送
 * 
 * @author Joey
 */
@Component
public class CaptchaUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(CaptchaUtils.class);
    
    @Resource
    private EmailUtils emailUtils;
    
    @Resource
    private SmsUtils smsUtils;
    
    /**
     * 验证码类型枚举
     */
    public enum CaptchaType {
        EMAIL("邮箱"),
        SMS("短信");
        
        private final String description;
        
        CaptchaType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 验证码发送结果
     */
    public static class CaptchaResult {
        private boolean success;
        private String message;
        
        public CaptchaResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public static CaptchaResult success() {
            return new CaptchaResult(true, "Отправка успешна");
        }
        
        public static CaptchaResult error(String message) {
            return new CaptchaResult(false, message);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    /**
     * 发送邮箱验证码
     * 
     * @param email 邮箱地址
     * @param code 验证码
     * @return 发送结果
     */
    public CaptchaResult sendEmailCaptcha(String email, String code) {
        try {
            // 验证邮箱格式
            if (!isValidEmail(email)) {
                logger.warn("Неверный формат электронной почты: {}", email);
                return CaptchaResult.error("Неверный формат электронной почты");
            }
            
            // 验证验证码
            if (!isValidCode(code)) {
                logger.warn("Неверный формат кода подтверждения: {}", code);
                return CaptchaResult.error("Неверный формат кода подтверждения");
            }
            
            // 发送邮件
            boolean success = emailUtils.sendCaptchaEmail(email, code);
            
            if (success) {
                logger.info("Код подтверждения по электронной почте успешно отправлен: {}", email);
                return CaptchaResult.success();
            } else {
                logger.error("Не удалось отправить код подтверждения по электронной почте: {}", email);
                return CaptchaResult.error("Не удалось отправить письмо, пожалуйста, проверьте настройки электронной почты");
            }
            
        } catch (Exception e) {
            logger.error("Исключение при отправке кода подтверждения по электронной почте: {}", e.getMessage(), e);
            return CaptchaResult.error("Отправка не удалась, пожалуйста, попробуйте позже");
        }
    }
    
    /**
     * 发送短信验证码
     * 
     * @param phoneNumber 手机号
     * @param code 验证码
     * @return 发送结果
     */
    public CaptchaResult sendSmsCaptcha(String phoneNumber, String code) {
        try {
            // 验证手机号格式
            if (!isValidPhoneNumber(phoneNumber)) {
                logger.warn("Неверный формат номера телефона: {}", phoneNumber);
                return CaptchaResult.error("Неверный формат номера телефона");
            }
            
            // 验证验证码
            if (!isValidCode(code)) {
                logger.warn("Неверный формат кода подтверждения: {}", code);
                return CaptchaResult.error("Неверный формат кода подтверждения");
            }
            
            // 发送短信
            boolean success = smsUtils.sendVerificationCodeSms(phoneNumber, code);
            
            if (success) {
                logger.info("Код подтверждения SMS успешно отправлен: {}", phoneNumber);
                return CaptchaResult.success();
            } else {
                logger.error("Не удалось отправить код подтверждения SMS: {}", phoneNumber);
                return CaptchaResult.error("Не удалось отправить SMS, пожалуйста, попробуйте позже");
            }
            
        } catch (Exception e) {
            logger.error("Исключение при отправке кода подтверждения SMS: {}", e.getMessage(), e);
            return CaptchaResult.error("Не удалось отправить SMS, пожалуйста, свяжитесь с администратором");
        }
    }
    
    /**
     * 验证邮箱格式
     * 
     * @param email 邮箱地址
     * @return 是否有效
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // 邮箱格式验证：包含@符号且@后面有.
        return email.matches("^[^@]+@[^@]+\\.[^@]+$");
    }
    
    /**
     * 验证手机号格式
     * 
     * @param phoneNumber 手机号
     * @return 是否有效
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }
        // 中国大陆手机号格式验证：11位数字，以1开头
        return phoneNumber.matches("^1\\d{10}$");
    }
    
    /**
     * 验证验证码格式
     * 
     * @param code 验证码
     * @return 是否有效
     */
    private boolean isValidCode(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        // 验证码通常是4-6位数字或字母
        return code.matches("^[0-9A-Za-z]{4,6}$");
    }
}

