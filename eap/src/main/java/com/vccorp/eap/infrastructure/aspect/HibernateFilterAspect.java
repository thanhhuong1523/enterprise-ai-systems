package com.vccorp.eap.infrastructure.aspect;

import com.vccorp.eap.infrastructure.security.SecurityContextHelper;
import com.vccorp.eap.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class HibernateFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.vccorp.eap.repository.DocumentRepository.*(..))")
    public void enableFilter() {
        try {
            User currentUser = SecurityContextHelper.getCurrentUser();
            if (currentUser != null && currentUser.getDepartmentId() != null) {
                Session session = entityManager.unwrap(Session.class);
                session.enableFilter("deptIsolationFilter")
                       .setParameter("userDeptId", currentUser.getDepartmentId());
            }
        } catch (RuntimeException e) {
            // Ignore if not authenticated or no department context (e.g. SYSTEM_ADMIN or startup scripts)
        }
    }
}
