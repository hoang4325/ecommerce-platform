package com.yashmerino.ecommerce.config;

import com.yashmerino.ecommerce.model.Cart;
import com.yashmerino.ecommerce.model.Category;
import com.yashmerino.ecommerce.model.Product;
import com.yashmerino.ecommerce.model.Role;
import com.yashmerino.ecommerce.model.User;
import com.yashmerino.ecommerce.repositories.CategoryRepository;
import com.yashmerino.ecommerce.repositories.ProductRepository;
import com.yashmerino.ecommerce.repositories.RoleRepository;
import com.yashmerino.ecommerce.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "seed.lotustowel.enabled", havingValue = "true")
public class LotusTowelSeedDataConfig implements ApplicationRunner {

    private static final String SELLER_USERNAME = "lotustowel";
    private static final String CATEGORY_NAME = "Towels and Linens";
    private static final String INACCESSIBLE_PASSWORD_HASH =
            "$2b$12$VEs/ny6C9Hx3jPhJpicQLOq3jaY3txvp0cwfUL25lyBCltVXNBCU6";

    private static final List<SeedProduct> PRODUCTS = List.of(
            new SeedProduct("Khăn Mặt, Khăn Gội, Khăn Tắm LotusTowel Luxury 100% Cotton", "16900.00", "-52%", "4.8", "n/a", "Hàng mới về"),
            new SeedProduct("Bộ khăn tắm, khăn mặt, khăn gội LOTUS 100% Cotton", "16900.00", "-56%", "4.7", "40k+", ""),
            new SeedProduct("Khăn Tắm Châu Âu 70x140cm, 60x120cm, 50x100cm LOTUSTOWEL", "59000.00", "-16%", "4.8", "100k+", ""),
            new SeedProduct("Khăn lau xe, Spa 30x30cm LOTUS 100% Microfiber Cao Cấp", "9900.00", "-38%", "4.8", "20k+", ""),
            new SeedProduct("Ly Gấu Dán Tường LotusTowel Cao Cấp", "15000.00", "-40%", "4.8", "10k+", ""),
            new SeedProduct("Bộ 3 Khăn Tắm, Gội, Lau mặt LOTUS 100% Cotton", "145000.00", "-3%", "4.8", "80k+", ""),
            new SeedProduct("Khăn mặt CHÂU ÂU 30x50cm, 100% COTTON - LOTUS TOWEL", "19900.00", "-48%", "4.8", "10k+", ""),
            new SeedProduct("Khăn Khách Sạn, Khăn Tắm Khăn Gội Đầu Khăn Mặt Lotustowel", "16900.00", "-44%", "4.8", "70k+", ""),
            new SeedProduct("Khăn Tắm, Gội, Lau Mặt Châu Âu LOTUS 100% Cotton", "16900.00", "-52%", "4.8", "700k+", ""),
            new SeedProduct("Khăn tắm Lotus 60x120cm, 50x100cm 100% Cotton", "59000.00", "-41%", "4.8", "10k+", ""),
            new SeedProduct("Khăn tắm nhỡ CHÂU ÂU 40x80cm - LOTUS TOWEL", "45000.00", "-25%", "4.8", "90k+", ""),
            new SeedProduct("Lẻ khăn Tắm, Gội, Lau Mặt LOTUS 100% Cotton", "16900.00", "-52%", "4.9", "10k+", ""),
            new SeedProduct("Bộ 2 khăn tắm nhỡ 50x100cm LOTUS", "104998.00", "-34%", "4.8", "6k+", ""),
            new SeedProduct("Khăn Tắm Cỡ Lớn 70x140cm LOTUS 100% Cotton Cao Cấp", "125000.00", "-22%", "4.8", "100k+", ""),
            new SeedProduct("Khăn tắm KHÁCH SẠN 70x140cm - LOTUSTOWEL", "105000.00", "-48%", "4.9", "20k+", ""),
            new SeedProduct("Bộ 5 khăn nhỡ 40x80cm Châu Âu LOTUS", "199000.00", "-10%", "4.8", "4k+", ""),
            new SeedProduct("Khăn tắm KHÁCH SẠN màu trắng 60x120cm - LOTUSTOWEL", "65000.00", "-35%", "4.8", "50k+", ""),
            new SeedProduct("Khăn mặt vuông màu trắng 34x34cm - LOTUSTOWEL", "19900.00", "-10%", "4.8", "547", ""),
            new SeedProduct("Khăn nhỡ KHÁCH SẠN màu trắng 35x70cm - LOTUSTOWEL", "35000.00", "-42%", "4.8", "10k+", ""),
            new SeedProduct("Bộ 5 Khăn Mặt 30x50cm Châu Âu LOTUS", "85000.00", "-23%", "4.8", "10k+", "")
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public LotusTowelSeedDataConfig(
            UserRepository userRepository,
            RoleRepository roleRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User seller = userRepository.findByUsername(SELLER_USERNAME).orElseGet(this::createSeller);
        if (productRepository.getProductsBySellerId(seller.getId(), PageRequest.of(0, 1)).hasContent()) {
            return;
        }

        Category category = categoryRepository.findByName(CATEGORY_NAME).orElseGet(this::createCategory);
        List<Product> products = PRODUCTS.stream()
                .map(seedProduct -> toProduct(seedProduct, seller, category))
                .toList();

        productRepository.saveAll(products);
    }

    private User createSeller() {
        Role sellerRole = roleRepository.findByName("SELLER").orElseGet(this::createSellerRole);

        User seller = new User();
        seller.setFirstName("Khăn Bông");
        seller.setLastName("Lotus");
        seller.setEmail("lotustowel@example.local");
        seller.setUsername(SELLER_USERNAME);
        seller.setPassword(INACCESSIBLE_PASSWORD_HASH);
        seller.setCart(new Cart());
        seller.setRoles(new HashSet<>(Set.of(sellerRole)));

        return userRepository.save(seller);
    }

    private Role createSellerRole() {
        Role role = new Role();
        role.setName("SELLER");
        return roleRepository.save(role);
    }

    private Category createCategory() {
        Category category = new Category();
        category.setName(CATEGORY_NAME);
        return categoryRepository.save(category);
    }

    private Product toProduct(SeedProduct seedProduct, User seller, Category category) {
        Product product = new Product();
        product.setName(seedProduct.name());
        product.setPrice(new BigDecimal(seedProduct.price()));
        product.setDescription(seedProduct.description());
        product.setUser(seller);
        product.setCategories(new HashSet<>(Set.of(category)));
        product.setOnHandQuantity(100);
        product.setReservedQuantity(0);
        product.setActive(true);
        return product;
    }

    private record SeedProduct(
            String name,
            String price,
            String discount,
            String rating,
            String sold,
            String note
    ) {
        private String description() {
            String description = "Shopee source: lotustowel. Discount: " + discount
                    + ". Rating: " + rating + ". Sold: " + sold + ".";
            return note.isBlank() ? description : description + " Note: " + note + ".";
        }
    }
}
