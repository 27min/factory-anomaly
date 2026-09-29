package io.github.min27.factoryanomaly.common;

public class ReadingNotFoundException extends RuntimeException {

    public ReadingNotFoundException(long id) {
        super("측정값을 찾을 수 없습니다: " + id);
    }
}
