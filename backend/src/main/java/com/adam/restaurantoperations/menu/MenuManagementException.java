package com.adam.restaurantoperations.menu;

import org.springframework.http.HttpStatus;

public class MenuManagementException extends RuntimeException {
    private final HttpStatus status;
    private MenuManagementException(HttpStatus status,String message){super(message);this.status=status;}
    public static MenuManagementException badRequest(String message){return new MenuManagementException(HttpStatus.BAD_REQUEST,message);}
    public static MenuManagementException notFound(String record){return new MenuManagementException(HttpStatus.NOT_FOUND,record+" not found");}
    public static MenuManagementException conflict(String message){return new MenuManagementException(HttpStatus.CONFLICT,message);}
    public static MenuManagementException invalidConfiguration(){return conflict("Modifier configuration cannot satisfy its active selection rules");}
    public static MenuManagementException stale(){return conflict("Menu record was changed by another request; reload and retry");}
    public HttpStatus getStatus(){return status;}
}
