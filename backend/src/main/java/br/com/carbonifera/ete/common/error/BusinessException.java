package br.com.carbonifera.ete.common.error;

public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
