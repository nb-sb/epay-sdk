package com.nbsb.epaysdk.epaybase.reflection.invoker;

import java.lang.reflect.Field;
/**
* @author: Wanghaonan @戏人看戏
* @description: getter 调用者
* @create: 2024/4/12 15:53
*/
public class GetFieldInvoker implements Invoker {
    private Field field;

    public GetFieldInvoker(Field field) {
        this.field = field;
    }

    @Override
    public Object invoke(Object target, Object[] args) throws Exception {
        return field.get(target);
    }

    @Override
    public Class<?> getType() {
        return field.getType();
    }
}
