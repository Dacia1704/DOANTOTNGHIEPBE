package com.tamdaptit.rentalmanagement.service.impl;

import com.tamdaptit.rentalmanagement.service.*;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserDeletionGuardImpl implements UserDeletionGuard {
    private final JdbcTemplate jdbc;

    // Inspect all actual foreign keys so deleting a user cannot cascade business records.
    // Authentication mappings are deleted explicitly by UserService.
    @Override
    public boolean hasReferences(String userId) {
        var keys = jdbc.queryForList("""
                SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_SCHEMA = DATABASE()
                  AND REFERENCED_TABLE_NAME = 'users'
                  AND TABLE_NAME NOT IN ('user_roles', 'refresh_tokens')
                """);
        for (var key : keys) {
            String table = key.get("TABLE_NAME").toString().replace("`", "``");
            String column = key.get("COLUMN_NAME").toString().replace("`", "``");
            Integer count = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM `" + table
                    + "` WHERE `" + column + "` = ?)", Integer.class, userId);
            if (count != null && count > 0) return true;
        }
        return false;
    }
}
