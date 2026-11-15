package com.tamdaptit.rentalmanagement.community;

import com.tamdaptit.rentalmanagement.common.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.tamdaptit.rentalmanagement.common.exception.ErrorCode.*;

@Component
@RequiredArgsConstructor
public class CommunityAccess {
    private final JdbcTemplate jdbc;
    public void active(String actor) {
        if(actor==null || jdbc.queryForObject("SELECT count(*) FROM users WHERE id=? AND status='ACTIVE'",Integer.class,actor)!=1)
            throw new AppException(UNAUTHORIZED);
    }
    public boolean admin(String actor) {
        return actor!=null && jdbc.queryForObject("SELECT count(*) FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.id=? AND u.status='ACTIVE' AND r.code='ADMIN'",Integer.class,actor)>0;
    }
    public void requireAdmin(String actor) { active(actor); if(!admin(actor)) throw new AppException(UNAUTHORIZED); }
    public void permission(String actor,String code) {
        requireAdmin(actor);
        if(!hasPermission(actor,code))throw new AppException(UNAUTHORIZED);
    }
    public boolean hasPermission(String actor,String code) {
        return jdbc.queryForObject("SELECT count(*) FROM user_roles ur JOIN role_permissions rp ON rp.role_id=ur.role_id JOIN permissions p ON p.id=rp.permission_id WHERE ur.user_id=? AND p.code=?",Integer.class,actor,code)>0;
    }
    public void lockActor(String actor) {
        active(actor);
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",String.class,actor);
    }
}
