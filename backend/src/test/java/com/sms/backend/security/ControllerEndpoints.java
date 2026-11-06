package com.sms.backend.security;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpMethod;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Reads every endpoint of the controllers (@GetMapping, @PostMapping...) without starting Spring. */
final class ControllerEndpoints {

    record Endpoint(HttpMethod method, String path, String handler) {

        /** "/api/v1/sales/{id}/items" -> "/api/v1/sales/1/items" */
        String samplePath() {
            return path.replaceAll("\\{[^}]+}", "1");
        }

        @Override
        public String toString() {
            return method.name() + " " + path + " (" + handler + ")";
        }
    }

    private ControllerEndpoints() {
    }

    static List<Endpoint> scan() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Endpoint> endpoints = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.sms.backend.controller")) {
            Class<?> type = load(definition.getBeanClassName());
            if (type.getEnclosingClass() != null) {
                continue; // "Probe" controllers nested in the security tests, not real endpoints
            }
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String[] basePaths = base == null || base.path().length == 0 ? new String[]{""} : base.path();
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String[] paths = mapping.path().length == 0 ? new String[]{""} : mapping.path();
                RequestMethod[] verbs = mapping.method().length == 0 ? RequestMethod.values() : mapping.method();
                for (String basePath : basePaths) {
                    for (String path : paths) {
                        for (RequestMethod verb : verbs) {
                            endpoints.add(new Endpoint(HttpMethod.valueOf(verb.name()), basePath + path,
                                    type.getSimpleName() + "." + method.getName()));
                        }
                    }
                }
            }
        }
        return endpoints;
    }

    private static Class<?> load(String className) {
        try {
            return ClassUtils.forName(className, ControllerEndpoints.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
