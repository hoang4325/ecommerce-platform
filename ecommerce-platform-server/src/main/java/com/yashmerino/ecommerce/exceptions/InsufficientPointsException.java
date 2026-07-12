package com.yashmerino.ecommerce.exceptions;

public class InsufficientPointsException extends RuntimeException {
    public InsufficientPointsException() { super("insufficient_points"); }
}
