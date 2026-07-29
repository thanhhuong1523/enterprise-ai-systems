package com.vccorp.eap.infrastructure.aspect;

import com.vccorp.eap.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class HibernateFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.vccorp.eap.repository.DocumentRepository.*(..))")
    public void enableFilter() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof User) {
            User currentUser = (User) authentication.getPrincipal();
            if (currentUser.getDepartmentId() != null) {
                Session session = entityManager.unwrap(Session.class);
                session.enableFilter("deptIsolationFilter")
                       .setParameter("userDeptId", currentUser.getDepartmentId());
            }
        }
    }
}
