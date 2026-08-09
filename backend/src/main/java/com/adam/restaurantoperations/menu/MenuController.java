package com.adam.restaurantoperations.menu;

import java.net.URI;
import java.util.List;

import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.common.config.OpenApiConfiguration;
import com.adam.restaurantoperations.menu.MenuDtos.Assignments;
import com.adam.restaurantoperations.menu.MenuDtos.CategoryResponse;
import com.adam.restaurantoperations.menu.MenuDtos.CategoryUpdate;
import com.adam.restaurantoperations.menu.MenuDtos.CategoryWrite;
import com.adam.restaurantoperations.menu.MenuDtos.GroupResponse;
import com.adam.restaurantoperations.menu.MenuDtos.GroupUpdate;
import com.adam.restaurantoperations.menu.MenuDtos.GroupWrite;
import com.adam.restaurantoperations.menu.MenuDtos.ItemResponse;
import com.adam.restaurantoperations.menu.MenuDtos.ItemUpdate;
import com.adam.restaurantoperations.menu.MenuDtos.ItemWrite;
import com.adam.restaurantoperations.menu.MenuDtos.OptionResponse;
import com.adam.restaurantoperations.menu.MenuDtos.OptionUpdate;
import com.adam.restaurantoperations.menu.MenuDtos.OptionWrite;
import com.adam.restaurantoperations.menu.MenuDtos.VersionedFlag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/menu")
@Tag(name="Menu management")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name=OpenApiConfiguration.BEARER_AUTHENTICATION)
@ApiResponses({
    @ApiResponse(responseCode="400",description="Validation or request-parameter failure"),
    @ApiResponse(responseCode="401",description="Authentication required"),
    @ApiResponse(responseCode="403",description="ADMIN authority required"),
    @ApiResponse(responseCode="404",description="Menu record not found"),
    @ApiResponse(responseCode="409",description="Duplicate, stale version, or unsafe modifier configuration")
})
public class MenuController {
    private final MenuService service;
    public MenuController(MenuService service){this.service=service;}

    @GetMapping("/categories") @Operation(summary="List and filter menu categories")
    public List<CategoryResponse> categories(@RequestParam(required=false) Boolean active,@RequestParam(required=false) String name,@RequestParam(defaultValue="displayOrder") String sortBy,@RequestParam(defaultValue="ASC") Sort.Direction direction){return service.listCategories(active,name,sortBy,direction);}
    @GetMapping("/categories/{id}") @Operation(summary="Get a menu category") public CategoryResponse category(@PathVariable Long id){return service.getCategory(id);}
    @PostMapping("/categories") @ApiResponses({@ApiResponse(responseCode="201"),@ApiResponse(responseCode="409",description="Duplicate category")})
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryWrite request,@AuthenticationPrincipal Jwt jwt,HttpServletRequest servlet){CategoryResponse response=service.createCategory(request,actor(jwt),RequestMetadata.from(servlet));return ResponseEntity.created(URI.create("/api/v1/menu/categories/"+response.id())).body(response);}
    @PutMapping("/categories/{id}") @Operation(summary="Update a category using its current version") public CategoryResponse updateCategory(@PathVariable Long id,@Valid @RequestBody CategoryUpdate request,@AuthenticationPrincipal Jwt jwt,HttpServletRequest servlet){return service.updateCategory(id,request,actor(jwt),RequestMetadata.from(servlet));}
    @PatchMapping("/categories/{id}/activation") @Operation(summary="Soft-deactivate or reactivate a category",description="Item flags are preserved; an inactive category makes its items effectively unavailable") public CategoryResponse activateCategory(@PathVariable Long id,@Valid @RequestBody VersionedFlag request,@AuthenticationPrincipal Jwt jwt,HttpServletRequest servlet){return service.activateCategory(id,request,actor(jwt),RequestMetadata.from(servlet));}

    @GetMapping("/items") @Operation(summary="List menu items and effective availability")
    public List<ItemResponse> items(@RequestParam(required=false) Long categoryId,@RequestParam(required=false) Boolean active,@RequestParam(required=false) Boolean availableForSale,@RequestParam(required=false) Boolean effectivelyAvailable,@RequestParam(required=false) String search,@RequestParam(defaultValue="displayOrder") String sortBy,@RequestParam(defaultValue="ASC") Sort.Direction direction){return service.listItems(categoryId,active,availableForSale,effectivelyAvailable,search,sortBy,direction);}
    @GetMapping("/items/{id}") @Operation(summary="Get a menu item with effective availability and ordered modifiers") public ItemResponse item(@PathVariable Long id){return service.getItem(id);}
    @PostMapping("/items") @Operation(summary="Create a menu item with a backend-normalized unique code") public ResponseEntity<ItemResponse> createItem(@Valid @RequestBody ItemWrite request,@AuthenticationPrincipal Jwt jwt,HttpServletRequest servlet){ItemResponse response=service.createItem(request,actor(jwt),RequestMetadata.from(servlet));return ResponseEntity.created(URI.create("/api/v1/menu/items/"+response.id())).body(response);}
    @PutMapping("/items/{id}") @Operation(summary="Update a menu item using its current version") public ItemResponse updateItem(@PathVariable Long id,@Valid @RequestBody ItemUpdate request,@AuthenticationPrincipal Jwt jwt,HttpServletRequest servlet){return service.updateItem(id,request,actor(jwt),RequestMetadata.from(servlet));}
    @PatchMapping("/items/{id}/activation") @Operation(summary="Soft-deactivate or reactivate a menu item") public ItemResponse activateItem(@PathVariable Long id,@Valid @RequestBody VersionedFlag request,@AuthenticationPrincipal Jwt jwt,HttpServletRequest servlet){return service.activateItem(id,request,actor(jwt),RequestMetadata.from(servlet));}
    @PatchMapping("/items/{id}/availability") @Operation(summary="Change the independent available-for-sale flag") public ItemResponse availability(@PathVariable Long id,@Valid @RequestBody VersionedFlag request,@AuthenticationPrincipal Jwt jwt,HttpServletRequest servlet){return service.setAvailability(id,request,actor(jwt),RequestMetadata.from(servlet));}
    @PutMapping("/items/{id}/modifier-groups") @Operation(summary="Replace ordered modifier-group assignments",description="Requires the current item version; newly assigned groups must be active and usable") public ItemResponse assignments(@PathVariable Long id,@Valid @RequestBody Assignments request,@AuthenticationPrincipal Jwt jwt,HttpServletRequest servlet){return service.assignGroups(id,request,actor(jwt),RequestMetadata.from(servlet));}

    @GetMapping("/modifier-groups") @Operation(summary="List reusable modifier groups and options")
    public List<GroupResponse> groups(@RequestParam(required=false) Boolean active,@RequestParam(required=false) SelectionType selectionType,@RequestParam(required=false) String name,@RequestParam(required=false) Long assignedMenuItemId,@RequestParam(defaultValue="displayOrder") String sortBy,@RequestParam(defaultValue="ASC") Sort.Direction direction){return service.listGroups(active,selectionType,name,assignedMenuItemId,sortBy,direction);}
    @GetMapping("/modifier-groups/{id}") @Operation(summary="Get a modifier group and its ordered options") public GroupResponse group(@PathVariable Long id){return service.getGroup(id);}
    @PostMapping("/modifier-groups") @Operation(summary="Create a reusable SINGLE or MULTIPLE modifier group") public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody GroupWrite request,@AuthenticationPrincipal Jwt jwt,HttpServletRequest servlet){GroupResponse response=service.createGroup(request,actor(jwt),RequestMetadata.from(servlet));return ResponseEntity.created(URI.create("/api/v1/menu/modifier-groups/"+response.id())).body(response);}
    @PutMapping("/modifier-groups/{id}") @Operation(summary="Update modifier selection rules using the current version") public GroupResponse updateGroup(@PathVariable Long id,@Valid @RequestBody GroupUpdate request,@AuthenticationPrincipal Jwt jwt,HttpServletRequest servlet){return service.updateGroup(id,request,actor(jwt),RequestMetadata.from(servlet));}
    @PatchMapping("/modifier-groups/{id}/activation") @Operation(summary="Soft-deactivate or safely reactivate a modifier group") public GroupResponse activateGroup(@PathVariable Long id,@Valid @RequestBody VersionedFlag request,@AuthenticationPrincipal Jwt jwt,HttpServletRequest servlet){return service.activateGroup(id,request,actor(jwt),RequestMetadata.from(servlet));}
    @PostMapping("/modifier-groups/{groupId}/options") @Operation(summary="Create a non-negative decimal modifier option") public ResponseEntity<OptionResponse> createOption(@PathVariable Long groupId,@Valid @RequestBody OptionWrite request,@AuthenticationPrincipal Jwt jwt,HttpServletRequest servlet){OptionResponse response=service.createOption(groupId,request,actor(jwt),RequestMetadata.from(servlet));return ResponseEntity.created(URI.create("/api/v1/menu/modifier-options/"+response.id())).body(response);}
    @PutMapping("/modifier-options/{id}") @Operation(summary="Update or reorder a modifier option using its current version") public OptionResponse updateOption(@PathVariable Long id,@Valid @RequestBody OptionUpdate request,@AuthenticationPrincipal Jwt jwt,HttpServletRequest servlet){return service.updateOption(id,request,actor(jwt),RequestMetadata.from(servlet));}
    @PatchMapping("/modifier-options/{id}/activation") @Operation(summary="Safely deactivate or reactivate a modifier option",description="Returns 409 when an actively assigned group would no longer satisfy its selection rules") public OptionResponse activateOption(@PathVariable Long id,@Valid @RequestBody VersionedFlag request,@AuthenticationPrincipal Jwt jwt,HttpServletRequest servlet){return service.activateOption(id,request,actor(jwt),RequestMetadata.from(servlet));}
    private Long actor(Jwt jwt){return Long.valueOf(jwt.getSubject());}
}
