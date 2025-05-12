package com.johnyowoy.compare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 保持 HTTPS
                .requiresChannel(channel -> channel
                        .anyRequest().requiresSecure()
                )
                // 授權請求
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/index.html",
                                "/html/**",
                                "/css/**",
                                "/js/**",
                                "/api/upload",
                                "/api/files",
                                "/api/files/**",
                                "/api/compare", // 允許公開訪問
                                "/login",
                                "/error"
                        ).permitAll()
                        .requestMatchers("/api/download").authenticated()
                        .anyRequest().permitAll()
                )
                // 禁用 CSRF
                .csrf(csrf -> csrf.disable())
                // 表單登入（保留，但不強制使用）
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/html/compare.html", true)
                        .permitAll()
                )
                // 登出
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/index.html")
                        .permitAll()
                )
                // 會話管理
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                );

        return http.build();
    }
}