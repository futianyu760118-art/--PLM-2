package com.hjgd.plm.log.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.log.annotation.OperationLog;
import com.hjgd.plm.system.entity.SysOperationLog;
import com.hjgd.plm.system.mapper.SysOperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final SysOperationLogMapper logMapper;
    private final ObjectMapper objectMapper;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        SysOperationLog record = new SysOperationLog();
        record.setOperation(operationLog.value());
        record.setCreatedAt(LocalDateTime.now());
        try {
            fillOperator(record);
            fillRequest(record);
            record.setParams(buildParams(joinPoint));
            fillSpel(record, joinPoint, operationLog);
        } catch (Exception e) {
            log.warn("操作日志预处理失败", e);
        }
        Object result;
        try {
            result = joinPoint.proceed();
            record.setResult(1);
        } catch (Throwable e) {
            record.setResult(0);
            record.setErrorMsg(e.getMessage());
            throw e;
        } finally {
            record.setCostMs((int) (System.currentTimeMillis() - start));
            try {
                logMapper.insert(record);
            } catch (Exception e) {
                log.error("操作日志保存失败", e);
            }
        }
        return result;
    }

    private void fillOperator(SysOperationLog record) {
        try {
            LoginUser user = SecurityUtils.getCurrentUser();
            record.setOperator(user.getRealName());
            record.setUserId(user.getUserId());
            record.setUsername(user.getUsername());
        } catch (Exception ignored) {
        }
    }

    private void fillRequest(SysOperationLog record) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                record.setIp(getClientIp(request));
                record.setDevice(request.getHeader("User-Agent"));
                record.setMethod(request.getMethod() + " " + request.getRequestURI());
            }
        } catch (Exception ignored) {
        }
    }

    private void fillSpel(SysOperationLog record, ProceedingJoinPoint joinPoint, OperationLog ann) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = discoverer.getParameterNames(method);
        if (paramNames == null) {
            return;
        }
        EvaluationContext ctx = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length && i < args.length; i++) {
            ctx.setVariable(paramNames[i], args[i]);
        }
        try {
            if (!ann.partNo().isEmpty()) {
                Expression exp = parser.parseExpression(ann.partNo());
                Object val = exp.getValue(ctx);
                if (val != null) {
                    record.setPartNo(val.toString());
                }
            }
            if (!ann.fileVersion().isEmpty()) {
                Expression exp = parser.parseExpression(ann.fileVersion());
                Object val = exp.getValue(ctx);
                if (val != null) {
                    record.setFileVersion(val.toString());
                }
            }
        } catch (Exception e) {
            log.debug("SpEL解析失败: {}", e.getMessage());
        }
    }

    private String buildParams(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Object[] args = joinPoint.getArgs();
            return objectMapper.writeValueAsString(args.length > 0 ? args[0] : "");
        } catch (Exception e) {
            return null;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip == null ? "" : (ip.contains(",") ? ip.split(",")[0].trim() : ip);
    }
}
