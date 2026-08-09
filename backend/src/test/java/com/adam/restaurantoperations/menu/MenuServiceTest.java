package com.adam.restaurantoperations.menu;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.adam.restaurantoperations.audit.MenuAuditService;
import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.menu.MenuDtos.Assignment;
import com.adam.restaurantoperations.menu.MenuDtos.Assignments;
import com.adam.restaurantoperations.menu.MenuDtos.CategoryWrite;
import com.adam.restaurantoperations.menu.MenuDtos.GroupWrite;
import com.adam.restaurantoperations.menu.MenuDtos.ItemWrite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock private MenuCategoryRepository categories;
    @Mock private MenuItemRepository items;
    @Mock private ModifierGroupRepository groups;
    @Mock private ModifierOptionRepository options;
    @Mock private MenuItemModifierGroupRepository assignments;
    @Mock private MenuAuditService audit;
    private MenuService service;

    @BeforeEach
    void setUp() {
        service = new MenuService(categories, items, groups, options, assignments, audit);
    }

    @Test
    void normalizesCategoryNamesAndItemCodesAndPreservesDecimalMoney() {
        given(categories.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
        service.createCategory(new CategoryWrite("  Hot   Drinks ", "  Fresh  ", 2), 1L, metadata());
        ArgumentCaptor<MenuCategoryEntity> category = ArgumentCaptor.forClass(MenuCategoryEntity.class);
        verify(categories).saveAndFlush(category.capture());
        assertThat(category.getValue().getName()).isEqualTo("Hot Drinks");

        MenuCategoryEntity parent = category.getValue();
        given(categories.findById(4L)).willReturn(Optional.of(parent));
        given(items.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(assignments.findOrderedByMenuItemId(any())).willReturn(List.of());
        service.createItem(new ItemWrite(4L, "  iced  latte ", "Iced Latte", null,
                new BigDecimal("4.20"), 1), 1L, metadata());
        ArgumentCaptor<MenuItemEntity> item = ArgumentCaptor.forClass(MenuItemEntity.class);
        verify(items).saveAndFlush(item.capture());
        assertThat(item.getValue().getCode()).isEqualTo("ICED-LATTE");
        assertThat(item.getValue().getBasePrice()).isEqualByComparingTo("4.20");
    }

    @Test
    void rejectsDuplicateNamesCodesInvalidSelectionRulesAndDuplicateAssignments() {
        given(categories.existsByNameIgnoreCase("Desserts")).willReturn(true);
        assertThatThrownBy(() -> service.createCategory(
                new CategoryWrite("Desserts", null, 0), 1L, metadata()))
                .isInstanceOf(MenuManagementException.class)
                .hasMessage("Category name already exists");

        given(items.existsByCodeIgnoreCase("CAKE")).willReturn(true);
        assertThatThrownBy(() -> service.createItem(new ItemWrite(
                1L, "cake", "Cake", null, BigDecimal.ONE, 0), 1L, metadata()))
                .isInstanceOf(MenuManagementException.class)
                .hasMessage("Menu item code already exists");

        assertThatThrownBy(() -> service.createGroup(new GroupWrite(
                "Sauces", null, SelectionType.SINGLE, 0, 2, 0), 1L, metadata()))
                .isInstanceOf(MenuManagementException.class)
                .hasMessageContaining("cannot satisfy");

        MenuItemEntity item = new MenuItemEntity(new MenuCategoryEntity("Food", null, 0),
                "BURGER", "Burger", null, BigDecimal.TEN, 0);
        given(items.findById(8L)).willReturn(Optional.of(item));
        assertThatThrownBy(() -> service.assignGroups(8L,
                new Assignments(List.of(new Assignment(2L, 0), new Assignment(2L, 1)), 0L),
                1L, metadata()))
                .isInstanceOf(MenuManagementException.class);

        assertThatThrownBy(() -> service.assignGroups(8L,
                new Assignments(List.of(new Assignment(2L, 0), new Assignment(3L, 0)), 0L),
                1L, metadata()))
                .isInstanceOf(MenuManagementException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    private RequestMetadata metadata() {
        return new RequestMetadata("127.0.0.1", "unit-test");
    }
}
