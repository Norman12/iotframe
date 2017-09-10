package com.praski.marcin.iotframe.Http;

import com.google.gson.annotations.SerializedName;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Created by marcin on 07.09.17.
 */

public class Response<T> implements ParameterizedType {
    @SerializedName("content")
    private T content;

    @SerializedName("error")
    private String error;

    private Class<?> wrapped;

    public Response(Class<T> clazz) {
        wrapped = clazz;
    }

    public static Type getType(final Class<?> parameter) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return new Type[]{parameter};
            }

            @Override
            public Type getRawType() {
                return Response.class;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
    }

    public T getContent() {
        return content;
    }

    public String getError() {
        return error;
    }

    @Override
    public Type[] getActualTypeArguments() {
        return new Type[]{wrapped};
    }

    @Override
    public Type getRawType() {
        return Response.class;
    }

    @Override
    public Type getOwnerType() {
        return null;
    }
}
