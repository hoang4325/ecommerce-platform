package com.yashmerino.ecommerce.exceptions;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException() { super("insufficient_stock"); }
}
