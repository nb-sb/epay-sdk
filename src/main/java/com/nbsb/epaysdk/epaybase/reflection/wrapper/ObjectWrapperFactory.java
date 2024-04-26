package com.nbsb.epaysdk.epaybase.reflection.wrapper;

import com.nbsb.epaysdk.epaybase.reflection.MetaObject;

/**
 * author: Wanghaonan @戏人看戏
 * description: 对象包装工厂
 * create: 2024/4/12 14:59
 */
public interface ObjectWrapperFactory {
    /**
     * 判断有没有包装器
     */
    boolean hasWrapperFor(Object object);

    /**
     * 得到包装器
     */
    ObjectWrapper getWrapperFor(MetaObject metaObject, Object object);
}
