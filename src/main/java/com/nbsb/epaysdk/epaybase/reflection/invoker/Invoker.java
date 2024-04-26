package com.nbsb.epaysdk.epaybase.reflection.invoker;
/**
* author: Wanghaonan @戏人看戏
* description: 调用者
* create: 2024/4/12 15:52
*/
public interface Invoker {
    Object invoke(Object target, Object[] args) throws Exception;

    Class<?> getType();
}
