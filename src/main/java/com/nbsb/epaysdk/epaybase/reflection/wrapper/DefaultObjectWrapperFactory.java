package com.nbsb.epaysdk.epaybase.reflection.wrapper;


import com.nbsb.epaysdk.epaybase.reflection.MetaObject;

/**
* author: Wanghaonan @戏人看戏
* description: 对象包装工厂
* create: 2024/4/12 15:01
*/
public class DefaultObjectWrapperFactory implements ObjectWrapperFactory{
    @Override
    public boolean hasWrapperFor(Object object) {
        return false;
    }

    @Override
    public ObjectWrapper getWrapperFor(MetaObject metaObject, Object object) {
        throw new RuntimeException("The DefaultObjectWrapperFactory should never be called to provide an ObjectWrapper.");
    }
}
