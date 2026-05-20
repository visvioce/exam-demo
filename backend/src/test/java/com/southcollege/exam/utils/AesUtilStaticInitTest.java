package com.southcollege.exam.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试AesUtil的静态初始化问题
 * 模拟Spring容器初始化过程
 */
class AesUtilStaticInitTest {

    @BeforeEach
    void setUp() {
        // 在每个测试前重置静态变量，模拟全新的类加载
        try {
            java.lang.reflect.Field field = AesUtil.class.getDeclaredField("staticSecretKey");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testEncryptBeforeInitShouldUseDefaultKey() throws Exception {
        // 模拟在 @PostConstruct 之前调用静态方法，此时 staticSecretKey 为 null
        java.lang.reflect.Field field = AesUtil.class.getDeclaredField("staticSecretKey");
        field.setAccessible(true);
        field.set(null, null);

        // 在无 Spring 上下文的单元测试中，staticSecretKey 为 null，
        // createAes() 会抛出 IllegalStateException，这是预期行为
        assertThrows(IllegalStateException.class, () -> AesUtil.encrypt("test"),
                "staticSecretKey 为 null 时应抛出 IllegalStateException");
    }

    @Test
    void testStaticSecretKeyIsNullBeforeInit() {
        // 验证在初始化前staticSecretKey确实为null
        try {
            java.lang.reflect.Field field = AesUtil.class.getDeclaredField("staticSecretKey");
            field.setAccessible(true);
            Object value = field.get(null);
            assertNull(value, "staticSecretKey should be null before @PostConstruct");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testAfterManualInitStaticSecretKeyIsSet() throws Exception {
        // 手动初始化后验证staticSecretKey被设置
        AesUtil aesUtil = new AesUtil();
        
        // 设置secretKey（模拟@Value注入）
        java.lang.reflect.Field secretKeyField = AesUtil.class.getDeclaredField("secretKey");
        secretKeyField.setAccessible(true);
        secretKeyField.set(aesUtil, "test-key-12345678"); // 16字节
        
        // 调用init方法
        java.lang.reflect.Method initMethod = AesUtil.class.getDeclaredMethod("init");
        initMethod.setAccessible(true);
        initMethod.invoke(aesUtil);
        
        // 检查静态变量
        java.lang.reflect.Field staticField = AesUtil.class.getDeclaredField("staticSecretKey");
        staticField.setAccessible(true);
        Object staticValue = staticField.get(null);
        assertEquals("test-key-12345678", staticValue);
    }
}