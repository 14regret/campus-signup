package com.campus.signup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 校园活动报名系统启动类。
 *
 * <p>{@code @ConfigurationPropertiesScan} 用来发现 {@code JwtProperties} 这类 record 配置；
 * {@code @EnableScheduling} 现在打开，第四步的活动状态推进 / 报名超时取消 / 计数对账
 * 三个定时任务直接落在这个上下文里，不用再改启动类。
 */
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ActivitySignupApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActivitySignupApplication.class, args);
    }
}
