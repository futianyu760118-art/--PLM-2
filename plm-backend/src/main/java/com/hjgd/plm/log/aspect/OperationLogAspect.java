package com.hjgd.plm.log.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    /** 敏感字段的落库掩码 */
    private static final String SENSITIVE_MASK = "******";

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
            Object[] args = joinPoint.getArgs();
            if (args.length == 0) {
                return objectMapper.writeValueAsString("");
            }
            // 先脱敏再落库：口令类字段绝不写入操作日志（R3 / AC11.2）
            JsonNode node = objectMapper.valueToTree(args[0]);
            maskSensitive(node);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    /** 递归把敏感字段值替换为掩码，字段名命中即处理，不依赖具体 DTO 类型 */
    void maskSensitive(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            List<String> names = new ArrayList<>();
            obj.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (isSensitiveName(name)) {
                    obj.put(name, SENSITIVE_MASK);
                } else {
                    maskSensitive(obj.get(name));
                }
            }
        } else if (node instanceof ArrayNode arr) {
            arr.forEach(this::maskSensitive);
        }
    }

    boolean isSensitiveName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("password") || n.contains("secret")
                || n.contains("token") || n.contains("credential");
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
