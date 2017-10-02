package com.github.sosozhuang.service;

public class UnsupportedException extends Exception {
    private static final long serialVersionUID = 1967889586001169615L;

    public UnsupportedException() {
        super();
    }
    public UnsupportedException(String s) {
        super(s);
    }
    public UnsupportedException(Throwable cause) {
        super(cause);
    }
}
