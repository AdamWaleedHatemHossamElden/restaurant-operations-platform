package com.adam.restaurantoperations.menu;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.adam.restaurantoperations.audit.MenuAuditService;
import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.menu.MenuDtos.Assignment;
import com.adam.restaurantoperations.menu.MenuDtos.AssignmentResponse;
import com.adam.restaurantoperations.menu.MenuDtos.Assignments;
import com.adam.restaurantoperations.menu.MenuDtos.CategoryResponse;
import com.adam.restaurantoperations.menu.MenuDtos.CategorySummary;
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
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuService {
    private static final Set<String> CATEGORY_SORTS=Set.of("displayOrder","name");
    private static final Set<String> ITEM_SORTS=Set.of("displayOrder","name","code","basePrice");
    private static final Set<String> GROUP_SORTS=Set.of("displayOrder","name");
    private final MenuCategoryRepository categories;
    private final MenuItemRepository items;
    private final ModifierGroupRepository groups;
    private final ModifierOptionRepository options;
    private final MenuItemModifierGroupRepository assignments;
    private final MenuAuditService audit;

    public MenuService(MenuCategoryRepository categories,MenuItemRepository items,ModifierGroupRepository groups,
            ModifierOptionRepository options,MenuItemModifierGroupRepository assignments,MenuAuditService audit){
        this.categories=categories;this.items=items;this.groups=groups;this.options=options;this.assignments=assignments;this.audit=audit;
    }

    @Transactional(readOnly=true)
    public List<CategoryResponse> listCategories(Boolean active,String name,String sortBy,Sort.Direction direction){
        Sort sort=sort(CATEGORY_SORTS,sortBy,"displayOrder",direction);
        return categories.findAll(categoryFilters(active,name),sort).stream().map(CategoryResponse::from).toList();
    }
    @Transactional(readOnly=true) public CategoryResponse getCategory(Long id){return CategoryResponse.from(category(id));}
    @Transactional public CategoryResponse createCategory(CategoryWrite request,Long actor,RequestMetadata metadata){
        String name=name(request.name()); if(categories.existsByNameIgnoreCase(name))throw MenuManagementException.conflict("Category name already exists");
        MenuCategoryEntity saved=saveCategory(new MenuCategoryEntity(name,optional(request.description()),request.displayOrder()));
        audit.record("MENU_CATEGORY_CREATED",actor,"MENU_CATEGORY",saved.getId(),metadata.ipAddress()); return CategoryResponse.from(saved);
    }
    @Transactional public CategoryResponse updateCategory(Long id,CategoryUpdate request,Long actor,RequestMetadata metadata){
        MenuCategoryEntity entity=category(id);version(entity.getVersion(),request.version());String name=name(request.name());
        if(categories.existsByNameIgnoreCaseAndIdNot(name,id))throw MenuManagementException.conflict("Category name already exists");
        entity.update(name,optional(request.description()),request.displayOrder());MenuCategoryEntity saved=saveCategory(entity);
        audit.record("MENU_CATEGORY_UPDATED",actor,"MENU_CATEGORY",saved.getId(),metadata.ipAddress());return CategoryResponse.from(saved);
    }
    @Transactional public CategoryResponse activateCategory(Long id,VersionedFlag request,Long actor,RequestMetadata metadata){
        MenuCategoryEntity entity=category(id);version(entity.getVersion(),request.version());if(entity.isActive()==request.value())return CategoryResponse.from(entity);
        entity.setActive(request.value());MenuCategoryEntity saved=saveCategory(entity);audit.record(request.value()?"MENU_CATEGORY_REACTIVATED":"MENU_CATEGORY_DEACTIVATED",actor,"MENU_CATEGORY",id,metadata.ipAddress());return CategoryResponse.from(saved);
    }

    @Transactional(readOnly=true)
    public List<ItemResponse> listItems(Long categoryId,Boolean active,Boolean available,Boolean effective,String search,String sortBy,Sort.Direction direction){
        return items.findAll(itemFilters(categoryId,active,available,effective,search),sort(ITEM_SORTS,sortBy,"displayOrder",direction)).stream().map(this::itemResponse).toList();
    }
    @Transactional(readOnly=true) public ItemResponse getItem(Long id){return itemResponse(item(id));}
    @Transactional public ItemResponse createItem(ItemWrite request,Long actor,RequestMetadata metadata){
        String code=code(request.code());if(items.existsByCodeIgnoreCase(code))throw MenuManagementException.conflict("Menu item code already exists");
        MenuItemEntity saved=saveItem(new MenuItemEntity(category(request.categoryId()),code,name(request.name()),optional(request.description()),money(request.basePrice()),request.displayOrder()));
        audit.record("MENU_ITEM_CREATED",actor,"MENU_ITEM",saved.getId(),metadata.ipAddress());return itemResponse(saved);
    }
    @Transactional public ItemResponse updateItem(Long id,ItemUpdate request,Long actor,RequestMetadata metadata){
        MenuItemEntity entity=item(id);version(entity.getVersion(),request.version());String code=code(request.code());
        if(items.existsByCodeIgnoreCaseAndIdNot(code,id))throw MenuManagementException.conflict("Menu item code already exists");
        entity.update(category(request.categoryId()),code,name(request.name()),optional(request.description()),money(request.basePrice()),request.displayOrder());
        MenuItemEntity saved=saveItem(entity);audit.record("MENU_ITEM_UPDATED",actor,"MENU_ITEM",id,metadata.ipAddress());return itemResponse(saved);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public ItemResponse activateItem(Long id,VersionedFlag request,Long actor,RequestMetadata metadata){
        MenuItemEntity entity=item(id);version(entity.getVersion(),request.version());if(entity.isActive()==request.value())return itemResponse(entity);
        if(request.value())validateAssignedGroupsForReactivation(id);
        entity.setActive(request.value());MenuItemEntity saved=saveItem(entity);audit.record(request.value()?"MENU_ITEM_REACTIVATED":"MENU_ITEM_DEACTIVATED",actor,"MENU_ITEM",id,metadata.ipAddress());return itemResponse(saved);
    }
    @Transactional public ItemResponse setAvailability(Long id,VersionedFlag request,Long actor,RequestMetadata metadata){
        MenuItemEntity entity=item(id);version(entity.getVersion(),request.version());if(entity.isAvailableForSale()==request.value())return itemResponse(entity);
        entity.setAvailableForSale(request.value());MenuItemEntity saved=saveItem(entity);audit.record("MENU_ITEM_AVAILABILITY_CHANGED",actor,"MENU_ITEM",id,metadata.ipAddress());return itemResponse(saved);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public ItemResponse assignGroups(Long id,Assignments request,Long actor,RequestMetadata metadata){
        MenuItemEntity entity=item(id);version(entity.getVersion(),request.version());Set<Long> unique=new HashSet<>();Set<Integer> orders=new HashSet<>();
        for(Assignment assignment:request.assignments()){
            if(!unique.add(assignment.modifierGroupId()))throw MenuManagementException.conflict("A modifier group may be assigned only once");
            if(!orders.add(assignment.displayOrder()))throw MenuManagementException.badRequest("Modifier-group display orders must be unique");
        }
        Map<Long,ModifierGroupEntity> locked=lockGroups(unique);List<MenuItemModifierGroupEntity> replacements=new ArrayList<>();
        for(Assignment assignment:request.assignments()){
            ModifierGroupEntity group=locked.get(assignment.modifierGroupId());if(!group.isActive())throw MenuManagementException.invalidConfiguration();validateUsable(group,activeOptionCount(group.getId()));
            replacements.add(new MenuItemModifierGroupEntity(entity,group,assignment.displayOrder()));
        }
        entity.touch();saveItem(entity);assignments.deleteByMenuItemId(id);assignments.flush();assignments.saveAll(replacements);assignments.flush();
        audit.record("MENU_ITEM_MODIFIERS_UPDATED",actor,"MENU_ITEM",id,metadata.ipAddress());return itemResponse(entity);
    }

    @Transactional(readOnly=true)
    public List<GroupResponse> listGroups(Boolean active,SelectionType type,String name,Long assignedItemId,String sortBy,Sort.Direction direction){
        Set<Long> allowed=assignedItemId==null?null:assignments.findOrderedByMenuItemId(assignedItemId).stream().map(a->a.getModifierGroup().getId()).collect(java.util.stream.Collectors.toSet());
        return groups.findAll(groupFilters(active,type,name),sort(GROUP_SORTS,sortBy,"displayOrder",direction)).stream().filter(g->allowed==null||allowed.contains(g.getId())).map(this::groupResponse).toList();
    }
    @Transactional(readOnly=true) public GroupResponse getGroup(Long id){return groupResponse(group(id));}
    @Transactional public GroupResponse createGroup(GroupWrite request,Long actor,RequestMetadata metadata){
        validateGroup(request.selectionType(),request.minimumSelections(),request.maximumSelections());String name=name(request.name());if(groups.existsByNameIgnoreCase(name))throw MenuManagementException.conflict("Modifier group name already exists");
        ModifierGroupEntity saved=saveGroup(new ModifierGroupEntity(name,optional(request.description()),request.selectionType(),request.minimumSelections(),request.maximumSelections(),request.displayOrder()));
        audit.record("MODIFIER_GROUP_CREATED",actor,"MODIFIER_GROUP",saved.getId(),metadata.ipAddress());return groupResponse(saved);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public GroupResponse updateGroup(Long id,GroupUpdate request,Long actor,RequestMetadata metadata){
        ModifierGroupEntity entity=lockedGroup(id);version(entity.getVersion(),request.version());validateGroup(request.selectionType(),request.minimumSelections(),request.maximumSelections());String name=name(request.name());
        if(groups.existsByNameIgnoreCaseAndIdNot(name,id))throw MenuManagementException.conflict("Modifier group name already exists");
        if(entity.isActive()&&assignments.existsByModifierGroupIdAndMenuItemActiveTrue(id))validateUsable(request.minimumSelections(),request.maximumSelections(),activeOptionCount(id));
        entity.update(name,optional(request.description()),request.selectionType(),request.minimumSelections(),request.maximumSelections(),request.displayOrder());ModifierGroupEntity saved=saveGroup(entity);
        audit.record("MODIFIER_GROUP_UPDATED",actor,"MODIFIER_GROUP",id,metadata.ipAddress());return groupResponse(saved);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public GroupResponse activateGroup(Long id,VersionedFlag request,Long actor,RequestMetadata metadata){
        ModifierGroupEntity entity=lockedGroup(id);version(entity.getVersion(),request.version());if(entity.isActive()==request.value())return groupResponse(entity);
        if(request.value()&&assignments.existsByModifierGroupIdAndMenuItemActiveTrue(id))validateUsable(entity,activeOptionCount(id));entity.setActive(request.value());ModifierGroupEntity saved=saveGroup(entity);
        audit.record(request.value()?"MODIFIER_GROUP_REACTIVATED":"MODIFIER_GROUP_DEACTIVATED",actor,"MODIFIER_GROUP",id,metadata.ipAddress());return groupResponse(saved);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED) public OptionResponse createOption(Long groupId,OptionWrite request,Long actor,RequestMetadata metadata){
        ModifierGroupEntity group=lockedGroup(groupId);String name=name(request.name());if(options.existsByGroupIdAndNameIgnoreCase(groupId,name))throw MenuManagementException.conflict("Modifier option name already exists in this group");
        ModifierOptionEntity saved=saveOption(new ModifierOptionEntity(group,name,money(request.priceAdjustment()),request.displayOrder()));audit.record("MODIFIER_OPTION_CREATED",actor,"MODIFIER_OPTION",saved.getId(),metadata.ipAddress());return OptionResponse.from(saved);
    }
    @Transactional public OptionResponse updateOption(Long id,OptionUpdate request,Long actor,RequestMetadata metadata){
        ModifierOptionEntity entity=option(id);version(entity.getVersion(),request.version());String name=name(request.name());if(options.existsByGroupIdAndNameIgnoreCaseAndIdNot(entity.getGroup().getId(),name,id))throw MenuManagementException.conflict("Modifier option name already exists in this group");
        entity.update(name,money(request.priceAdjustment()),request.displayOrder());ModifierOptionEntity saved=saveOption(entity);audit.record("MODIFIER_OPTION_UPDATED",actor,"MODIFIER_OPTION",id,metadata.ipAddress());return OptionResponse.from(saved);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public OptionResponse activateOption(Long id,VersionedFlag request,Long actor,RequestMetadata metadata){
        Long groupId=options.findGroupIdById(id).orElseThrow(()->MenuManagementException.notFound("Modifier option"));ModifierGroupEntity group=lockedGroup(groupId);ModifierOptionEntity entity=option(id);version(entity.getVersion(),request.version());if(entity.isActive()==request.value())return OptionResponse.from(entity);
        long projected=activeOptionCount(group.getId())+(request.value()?1:-1);if(group.isActive()&&assignments.existsByModifierGroupIdAndMenuItemActiveTrue(group.getId()))validateUsable(group,projected);
        entity.setActive(request.value());ModifierOptionEntity saved=saveOption(entity);audit.record(request.value()?"MODIFIER_OPTION_REACTIVATED":"MODIFIER_OPTION_DEACTIVATED",actor,"MODIFIER_OPTION",id,metadata.ipAddress());return OptionResponse.from(saved);
    }

    private ItemResponse itemResponse(MenuItemEntity e){List<AssignmentResponse> assigned=assignments.findOrderedByMenuItemId(e.getId()).stream().map(a->{ModifierGroupEntity g=a.getModifierGroup();return new AssignmentResponse(g.getId(),g.getName(),g.getSelectionType(),g.getMinimumSelections(),g.getMaximumSelections(),a.getDisplayOrder(),g.isActive());}).toList();return new ItemResponse(e.getId(),CategorySummary.from(e.getCategory()),e.getCode(),e.getName(),e.getDescription(),e.getBasePrice().toPlainString(),e.getDisplayOrder(),e.isActive(),e.isAvailableForSale(),e.isEffectivelyAvailable(),assigned,e.getCreatedAt(),e.getUpdatedAt(),e.getVersion());}
    private GroupResponse groupResponse(ModifierGroupEntity e){return new GroupResponse(e.getId(),e.getName(),e.getDescription(),e.getSelectionType(),e.getMinimumSelections(),e.getMaximumSelections(),e.getDisplayOrder(),e.isActive(),assignments.countByModifierGroupId(e.getId()),options.findByGroupIdOrderByDisplayOrderAscNameAsc(e.getId()).stream().map(OptionResponse::from).toList(),e.getCreatedAt(),e.getUpdatedAt(),e.getVersion());}
    private Specification<MenuCategoryEntity> categoryFilters(Boolean active,String name){return(root,q,cb)->{Predicate p=cb.conjunction();if(active!=null)p=cb.and(p,cb.equal(root.get("active"),active));if(text(name))p=cb.and(p,cb.like(cb.lower(root.get("name")),"%"+name.trim().toLowerCase(Locale.ROOT)+"%"));return p;};}
    private Specification<MenuItemEntity> itemFilters(Long categoryId,Boolean active,Boolean available,Boolean effective,String search){return(root,q,cb)->{Predicate p=cb.conjunction();if(categoryId!=null)p=cb.and(p,cb.equal(root.get("category").get("id"),categoryId));if(active!=null)p=cb.and(p,cb.equal(root.get("active"),active));if(available!=null)p=cb.and(p,cb.equal(root.get("availableForSale"),available));if(effective!=null){Predicate e=cb.and(cb.isTrue(root.get("active")),cb.isTrue(root.get("availableForSale")),cb.isTrue(root.get("category").get("active")));p=cb.and(p,effective?e:cb.not(e));}if(text(search)){String term="%"+search.trim().toLowerCase(Locale.ROOT)+"%";p=cb.and(p,cb.or(cb.like(cb.lower(root.get("name")),term),cb.like(cb.lower(root.get("code")),term)));}return p;};}
    private Specification<ModifierGroupEntity> groupFilters(Boolean active,SelectionType type,String name){return(root,q,cb)->{Predicate p=cb.conjunction();if(active!=null)p=cb.and(p,cb.equal(root.get("active"),active));if(type!=null)p=cb.and(p,cb.equal(root.get("selectionType"),type));if(text(name))p=cb.and(p,cb.like(cb.lower(root.get("name")),"%"+name.trim().toLowerCase(Locale.ROOT)+"%"));return p;};}
    private Sort sort(Set<String> allowed,String requested,String fallback,Sort.Direction direction){String field=allowed.contains(requested)?requested:fallback;return Sort.by(direction,field).and(Sort.by("id"));}
    private void validateGroup(SelectionType type,int min,int max){if(min>max||(type==SelectionType.SINGLE&&max!=1))throw MenuManagementException.invalidConfiguration();}
    private void validateUsable(ModifierGroupEntity group,long count){validateUsable(group.getMinimumSelections(),group.getMaximumSelections(),count);}
    private void validateUsable(int min,int max,long count){if(count<min||count<max)throw MenuManagementException.invalidConfiguration();}
    private long activeOptionCount(Long groupId){return options.countByGroupIdAndActiveTrue(groupId);}
    private void validateAssignedGroupsForReactivation(Long itemId){List<MenuItemModifierGroupEntity> current=assignments.findOrderedByMenuItemId(itemId);Set<Long> ids=current.stream().map(a->a.getModifierGroup().getId()).collect(java.util.stream.Collectors.toSet());Map<Long,ModifierGroupEntity> locked=lockGroups(ids);for(MenuItemModifierGroupEntity assignment:current){ModifierGroupEntity group=locked.get(assignment.getModifierGroup().getId());if(group.isActive())validateUsable(group,activeOptionCount(group.getId()));}}
    private Map<Long,ModifierGroupEntity> lockGroups(Set<Long> ids){Map<Long,ModifierGroupEntity> locked=new HashMap<>();ids.stream().sorted().forEach(id->locked.put(id,lockedGroup(id)));return locked;}
    private ModifierGroupEntity lockedGroup(Long id){return groups.findByIdForUpdate(id).orElseThrow(()->MenuManagementException.notFound("Modifier group"));}
    private MenuCategoryEntity category(Long id){return categories.findById(id).orElseThrow(()->MenuManagementException.notFound("Menu category"));}
    private MenuItemEntity item(Long id){return items.findById(id).orElseThrow(()->MenuManagementException.notFound("Menu item"));}
    private ModifierGroupEntity group(Long id){return groups.findById(id).orElseThrow(()->MenuManagementException.notFound("Modifier group"));}
    private ModifierOptionEntity option(Long id){return options.findById(id).orElseThrow(()->MenuManagementException.notFound("Modifier option"));}
    private MenuCategoryEntity saveCategory(MenuCategoryEntity e){try{return categories.saveAndFlush(e);}catch(ObjectOptimisticLockingFailureException ex){throw MenuManagementException.stale();}catch(DataIntegrityViolationException ex){throw MenuManagementException.conflict("Category name already exists");}}
    private MenuItemEntity saveItem(MenuItemEntity e){try{return items.saveAndFlush(e);}catch(ObjectOptimisticLockingFailureException ex){throw MenuManagementException.stale();}catch(DataIntegrityViolationException ex){throw MenuManagementException.conflict("Menu item code already exists");}}
    private ModifierGroupEntity saveGroup(ModifierGroupEntity e){try{return groups.saveAndFlush(e);}catch(ObjectOptimisticLockingFailureException ex){throw MenuManagementException.stale();}catch(DataIntegrityViolationException ex){throw MenuManagementException.conflict("Modifier group name already exists");}}
    private ModifierOptionEntity saveOption(ModifierOptionEntity e){try{return options.saveAndFlush(e);}catch(ObjectOptimisticLockingFailureException ex){throw MenuManagementException.stale();}catch(DataIntegrityViolationException ex){throw MenuManagementException.conflict("Modifier option name already exists in this group");}}
    private void version(long current,Long supplied){if(current!=supplied)throw MenuManagementException.stale();}
    private String name(String value){return value.trim().replaceAll("\\s+"," ");} private String code(String value){return value.trim().replaceAll("\\s+","-").toUpperCase(Locale.ROOT);} private String optional(String value){return text(value)?value.trim():null;} private boolean text(String value){return value!=null&&!value.isBlank();} private BigDecimal money(BigDecimal value){return value.setScale(2);}
}
