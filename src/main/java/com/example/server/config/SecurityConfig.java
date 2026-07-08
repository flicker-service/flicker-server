package com.example.server.config;

import com.example.server.auth.CustomAuthenticationEntryPoint;
import com.example.server.auth.filter.JwtAuthenticationFilter;
import com.example.server.auth.filter.JwtAuthorizationFilter;
import com.example.server.auth.handler.CustomLogoutHandler;
import com.example.server.auth.repository.RefreshTokenRepository;
import com.example.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final CorsConfig corsConfig;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationConfiguration authConfig = http.getSharedObject(AuthenticationConfiguration.class);
        AuthenticationManager authenticationManager = authenticationManager(authConfig);

        JwtAuthenticationFilter jwtAuthenticationFilter = jwtAuthenticationFilter(authenticationManager);
        JwtAuthorizationFilter jwtAuthorizationFilter = jwtAuthorizationFilter(authenticationManager);

        http.exceptionHandling(exceptionHandling ->
                exceptionHandling.authenticationEntryPoint(new CustomAuthenticationEntryPoint())
        );

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource())) // CORS 설정 추가
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));

        http
                .logout(logout -> logout
                        .logoutUrl("/api/users/logout")
                        .logoutSuccessHandler(new CustomLogoutHandler(refreshTokenRepository))
                );

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/health").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/login", "/signup", "/mypage", "/userposts", "/createpost").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/feeds/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/follows/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/images/**").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/follows/**").authenticated()
                        .requestMatchers( "/api/feeds/**").authenticated()
                        .requestMatchers("/api/timeline/**").authenticated()
                        .requestMatchers("/api/like/**").authenticated()
                        .requestMatchers("/api/like/**").authenticated()
                        .requestMatchers("/api/images/**").authenticated()
                        .requestMatchers("/api/timeline/**").authenticated()
                        .anyRequest().denyAll()
                );

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(jwtAuthorizationFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(AuthenticationManager authenticationManager) {
        JwtAuthenticationFilter authenticationFilter = new JwtAuthenticationFilter(refreshTokenRepository);
        authenticationFilter.setAuthenticationManager(authenticationManager);

        SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
        authenticationFilter.setSecurityContextRepository(contextRepository);

        return authenticationFilter;
    }

    @Bean
    public JwtAuthorizationFilter jwtAuthorizationFilter(AuthenticationManager authenticationManager) {
        JwtAuthorizationFilter authorizationFilter = new JwtAuthorizationFilter(authenticationManager, userRepository, refreshTokenRepository);
        return authorizationFilter;
    }
}
