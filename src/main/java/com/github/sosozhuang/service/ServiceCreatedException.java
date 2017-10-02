package com.github.sosozhuang.service;

public class ServiceCreatedException extends Exception {
    private static final long serialVersionUID = 4677793107357491875L;

    public ServiceCreatedException() {
        super();
    }
    public ServiceCreatedException(String s) {
        super(s);
    }
    public ServiceCreatedException(Throwable cause) {
        super(cause);
    }
}
