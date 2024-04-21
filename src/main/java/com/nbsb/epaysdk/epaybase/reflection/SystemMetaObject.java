package com.nbsb.epaysdk.epaybase.reflection;

import com.nbsb.epaysdk.epaybase.reflection.factory.DefaultObjectFactory;
import com.nbsb.epaysdk.epaybase.reflection.factory.ObjectFactory;
import com.nbsb.epaysdk.epaybase.reflection.wrapper.DefaultObjectWrapperFactory;
import com.nbsb.epaysdk.epaybase.reflection.wrapper.ObjectWrapperFactory;

/**
* @author: Wanghaonan @戏人看戏
* @description: 系统级别的元素
* @create: 2024/4/12 14:51
*/
public class SystemMetaObject {

    public static final ObjectFactory DEFAULT_OBJECT_FACTORY = new DefaultObjectFactory();
    public static final ObjectWrapperFactory DEFAULT_OBJECT_WRAPPER_FACTORY = new DefaultObjectWrapperFactory();
    public static final MetaObject NULL_META_OBJECT = MetaObject.forObject(NullObject.class, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY);

    private SystemMetaObject() {
        // Prevent Instantiation of Static Class
    }

    /**
     * 空对象
     */
    private static class NullObject {
    }

    public static MetaObject forObject(Object object) {
        return MetaObject.forObject(object, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY);
    }
}
