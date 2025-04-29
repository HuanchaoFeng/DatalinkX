package com.datalinkx.driver.dsdriver;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import com.datalinkx.common.utils.ConnectIdUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * DsDriverFactory 类是一个工厂类，用于根据连接 ID 创建不同类型的数据库驱动实例。
 * 它使用了反射机制来动态加载驱动类，并调用其构造函数创建实例。
 * 这种设计模式使得代码更加灵活和可扩展，可以轻松添加新的数据库驱动支持。
 * 同时，通过记录错误日志和抛出异常，确保了驱动加载过程中的问题能够被及时发现和处理。
 */

@Slf4j
public final class DsDriverFactory {

    //定义了一个私有的构造函数，防止外部直接实例化这个工厂类。
    private DsDriverFactory() {

    }
    private static final String PACKAGE_PREFIX = "com.datalinkx.driver.dsdriver.";

    private static String getDriverClass(String driverName) {
        return PACKAGE_PREFIX + driverName.toLowerCase() + "driver" + "." + ConnectIdUtils.toPascalCase(driverName) + "Driver";
    }

    //getDriver 方法依赖于 ConnectIdUtils 和 getDriverClass 方法来确定应该加载和实例化哪个驱动类。
    public static IDsDriver getDriver(String connectId) throws Exception {
        String dsType = ConnectIdUtils.getDsType(connectId);
        //拼接地址，也就是包的地址，来获取对应的类名，并返回给DsServiceImpl类，让他知道用的是哪个实现类，这就是用来替代无数个if else的关键地方
        String driverClassName = getDriverClass(dsType);
        Class<?> driverClass = Class.forName(driverClassName);
        Constructor<?> constructor = driverClass.getDeclaredConstructor(String.class);
        return (IDsDriver) constructor.newInstance(connectId);
    }

    public static IStreamDriver getStreamDriver(String connectId) throws Exception {
        String dsType = ConnectIdUtils.getDsType(connectId);
        String driverClassName = getDriverClass(dsType);
        Class<?> driverClass = Class.forName(driverClassName);
        Constructor<?> constructor = driverClass.getDeclaredConstructor(String.class);
        return (IStreamDriver) constructor.newInstance(connectId);
    }

    public static IDsReader getDsReader(String connectId) throws Exception {
        /**
         * getDriver(connectId) 返回的是一个 IDsDriver 类型的对象，然后将其强制转换为 IDsReader 类型。这种转换能够成功执行的前提是：
         * IDsReader 接口继承自 IDsDriver 接口：如果 IDsReader 接口是 IDsDriver 接口的子接口，那么所有的 IDsReader 实现也必然是 IDsDriver 的实现。
         * 这种情况下，转换是合法的，因为 IDsReader 是 IDsDriver 的子类型。
         * 实现类的兼容性：调用 getDriver(connectId) 方法返回的具体实现类必须同时实现了 IDsDriver 和 IDsReader 接口。这是类型转换能够成功的关键。
         */
        try {
            try {
                return (IDsReader) getDriver(connectId);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                log.error("driver load error", e);
            }
        } catch (NoSuchMethodException e) {
            log.error("driver load error", e);
        }

        throw new Exception("can not initialize driver");
    }

    public static IDsWriter getDsWriter(String connectId) throws Exception {
        try {
            try {
                return (IDsWriter) getDriver(connectId);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                log.error("driver load error", e);
            }
        } catch (NoSuchMethodException e) {
            log.error("driver load error", e);
        }

        throw new Exception("can not initialize driver");
    }
}
