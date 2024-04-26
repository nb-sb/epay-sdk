package com.nbsb.epaysdk.epaybase.reflection.invoker;

import java.lang.reflect.Field;
/**
* author: Wanghaonan @戏人看戏
* description: setter 调用者
* create: 2024/4/12 15:52
*/
public class SetFieldInvoker implements Invoker {
    private Field field;

    public SetFieldInvoker(Field field) {
        this.field = field;
    }

    @Override
    public Object invoke(Object target, Object[] args) throws Exception {
        field.set(target, args[0]);
        return null;
    }

    @Override
    public Class<?> getType() {
        return field.getType();
    }
}
