package com.blog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.blog.security.CsrfProtectionFilter;
import com.blog.security.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final CsrfProtectionFilter csrfProtectionFilter;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      CsrfProtectionFilter csrfProtectionFilter) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.csrfProtectionFilter = csrfProtectionFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/set-cookie-test").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/**").permitAll()
            .requestMatchers("/api/auth/register").permitAll()
            .requestMatchers("/api/auth/login").permitAll()
            .requestMatchers("/api/auth/refresh").permitAll()
            .requestMatchers("/api/**").authenticated()
            .anyRequest().permitAll());

    http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    http.addFilterAfter(csrfProtectionFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
