package com.adam.restaurantoperations.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.adam.restaurantoperations.audit.InventoryAuditService;
import com.adam.restaurantoperations.audit.StaffAuditService;
import com.adam.restaurantoperations.inventory.InventoryService;
import com.adam.restaurantoperations.inventory.PurchaseOrderService;
import com.adam.restaurantoperations.inventory.RecipeService;
import com.adam.restaurantoperations.inventory.StockConsumptionService;
import com.adam.restaurantoperations.inventory.SupplierService;
import com.adam.restaurantoperations.staff.StaffService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@MockitoBean(types = {
    InventoryService.class,
    RecipeService.class,
    SupplierService.class,
    PurchaseOrderService.class,
    StockConsumptionService.class,
    InventoryAuditService.class,
    StaffService.class,
    StaffAuditService.class
})
public @interface MockInventoryBeans {
}
