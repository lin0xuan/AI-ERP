package com.aierp.erp.datacenter;

import com.aierp.security.common.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;

/** These permissions grant access to the entire ERP data domain, not a single shop. */
public final class DataCenterAccess {
    private DataCenterAccess() {}
    public static boolean allowed(String operation) {
        try {
            var user = SecurityUtils.getLoginUser();
            return user != null && (SecurityUtils.isAdmin(user.getUserId()) ||
                    user.getPermissions() != null && (user.getPermissions().contains("*:*:*") ||
                    user.getPermissions().contains("data:center:" + operation)));
        } catch (RuntimeException ex) { return false; }
    }
    public static void require(String operation) {
        if (!allowed(operation)) throw new AccessDeniedException("需要全域数据中台权限：data:center:" + operation);
    }
}
