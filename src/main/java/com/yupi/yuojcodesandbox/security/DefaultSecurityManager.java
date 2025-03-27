package com.yupi.yuojcodesandbox.security;

import java.security.Permission;

/**
 * 默认安全管理器
 */
public class DefaultSecurityManager extends SecurityManager{

    //检查所有的权限,管理器里最大的一个方法
    @Override
    public void checkPermission(Permission perm) {
        System.out.println("默认不做任何权限限制");
        System.out.println(perm);
//        super.checkPermission(perm);
    }
}
