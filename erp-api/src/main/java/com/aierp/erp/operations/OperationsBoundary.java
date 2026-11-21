package com.aierp.erp.operations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import java.util.Set;

/** Legacy endpoints have not all adopted row-level scope. Scoped accounts cannot bypass through them. */
@Configuration
@RequiredArgsConstructor
public class OperationsBoundary implements WebMvcConfigurer {
    private final OperationsService operations;
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                if(!(handler instanceof HandlerMethod method)) return true;
                OperationsService.Actor actor;
                try {actor=OperationsController.actor();} catch(RuntimeException ex) {return true;}
                if(!operations.scoped(actor)) return true;
                String controller=method.getBeanType().getSimpleName();
                if(Set.of("OperationsController","ChatController","MemoryController","AiTraceController","SysLoginController").contains(controller)) return true;
                throw new AccessDeniedException("此账号按商户/店铺/仓库范围授权，请使用业务工作台；旧模块尚未支持此数据范围");
            }
        }).addPathPatterns("/api/**");
    }
}
