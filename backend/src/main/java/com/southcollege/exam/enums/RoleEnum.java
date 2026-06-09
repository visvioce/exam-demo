package com.southcollege.exam.enums;

import lombok.Getter;

/**
 * 用户角色枚举
 * <p>角色层级：ADMIN 拥有 TEACHER 的全部权限 + 用户管理 + 轮播图管理</p>
 */
@Getter
public enum RoleEnum {
    ADMIN("ADMIN", "管理员"),
    TEACHER("TEACHER", "教师"),
    STUDENT("STUDENT", "学生");

    private final String code;
    private final String description;

    RoleEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 判断当前角色是否拥有指定角色的权限（含角色层级）
     * <p>ADMIN 拥有 TEACHER 的全部权限，管理员 = 教师 + 用户管理 + 轮播图管理</p>
     */
    public boolean hasPermission(RoleEnum required) {
        if (this == ADMIN && required == TEACHER) {
            return true;
        }
        return this == required;
    }

    public static RoleEnum fromCode(String code) {
        for (RoleEnum role : values()) {
            if (role.getCode().equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知的角色: " + code);
    }
}
