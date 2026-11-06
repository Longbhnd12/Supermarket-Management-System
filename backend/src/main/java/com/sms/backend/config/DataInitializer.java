package com.sms.backend.config;

import com.sms.backend.entity.Category;
import com.sms.backend.entity.Product;
import com.sms.backend.entity.Role;
import com.sms.backend.entity.User;
import com.sms.backend.enums.AccountStatus;
import com.sms.backend.enums.GeneralStatus;
import com.sms.backend.enums.RoleName;
import com.sms.backend.repository.CategoryRepository;
import com.sms.backend.repository.ProductRepository;
import com.sms.backend.repository.RoleRepository;
import com.sms.backend.repository.UserRepository;
import com.sms.backend.security.RolePermissions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initDefaultData() {
        return args -> {
            try {
                // Ensure all default roles exist
                for (RoleName roleName : RoleName.values()) {
                    roleRepository.findByRoleName(roleName).orElseGet(() -> {
                        Role role = new Role();
                        role.setRoleName(roleName);
                        role.setStatus(GeneralStatus.ACTIVE);
                        role.setDescription("Default " + roleName.name() + " role");
                        role.setPermissions(RolePermissions.format(RolePermissions.defaultsFor(roleName)));
                        log.info("Initialized default role: {}", roleName);
                        return roleRepository.save(role);
                    });
                }

                // Ensure a default admin account exists
                if (!userRepository.existsByUsername("admin")) {
                    Role adminRole = roleRepository.findByRoleName(RoleName.ADMIN).orElse(null);
                    if (adminRole != null) {
                        User admin = new User();
                        admin.setUsername("admin");
                        admin.setEmail("admin@sms.com");
                        admin.setPasswordHash(passwordEncoder.encode("admin123456"));
                        admin.setStatus(AccountStatus.ACTIVE);
                        admin.setFailedLoginAttempts(0);
                        admin.setRole(adminRole);
                        userRepository.save(admin);
                        log.info("Initialized default admin account: admin / admin123456");
                    }
                }

                // Ensure default product catalog exists for UC09 & related tests
                initProductCatalog();

            } catch (Exception e) {
                log.warn("Data initialization skipped or failed: {}", e.getMessage(), e);
            }
        };
    }

    private void initProductCatalog() {
        if (productRepository.count() > 0) {
            log.info("Product catalog already contains {} products. Skipping product seeding.", productRepository.count());
            return;
        }

        log.info("Seeding default categories and products for UC09...");

        // Seed Categories
        Map<String, Category> catMap = new HashMap<>();
        String[][] categoriesData = {
                {"Sữa & Trứng", "Sữa tươi, sữa chua, bơ, phô mai và các loại trứng gia cầm"},
                {"Mì ăn liền", "Mì gói, hủ tiếu, phở ăn liền, miến đóng gói"},
                {"Nước giải khát", "Nước ngọt, nước khoáng, bia, nước trái cây và cà phê đóng lon"},
                {"Gia vị & Nước sốt", "Dầu ăn, nước mắm, nước tương, đường, muối, hạt nêm và tương ớt"},
                {"Gạo & Nông sản", "Gạo đặc sản, đậu, nếp, bột và các loại nông sản khô"},
                {"Bánh kẹo", "Bánh quy, bánh ngọt, snack khoai tây, kẹo dẻo và sô-cô-la"},
                {"Đồ đông lạnh & Chế biến", "Xúc xích, cá viên, bò viên, thực phẩm chế biến sẵn đóng gói"},
                {"Chăm sóc cá nhân", "Dầu gội, sữa tắm, kem đánh răng, xà phòng"},
                {"Vệ sinh nhà cửa", "Nước giặt, nước xả, nước rửa chén, nước lau sàn"}
        };

        for (String[] catInfo : categoriesData) {
            String name = catInfo[0];
            String desc = catInfo[1];
            Category cat = categoryRepository.findByCategoryName(name).orElseGet(() -> {
                Category c = new Category();
                c.setCategoryName(name);
                c.setDescription(desc);
                c.setStatus(GeneralStatus.ACTIVE);
                c.setCreatedAt(LocalDateTime.now());
                return categoryRepository.save(c);
            });
            catMap.put(name, cat);
        }

        // Seed Products
        Object[][] productsData = {
                // Cat, Code, Barcode, Name, Unit, Price, Stock, MinStock, TrackExpiry, Status
                {"Sữa & Trứng", "SKU-8930124", "8934567890123", "Sữa tươi tiệt trùng Vinamilk 100% Không đường 1L", "Hộp", "38000", "150.000", "20.000", true, GeneralStatus.ACTIVE},
                {"Mì ăn liền", "SKU-8930512", "8935001234567", "Mì Hảo Hảo Tôm Chua Cay 75g", "Gói", "4500", "320.000", "50.000", true, GeneralStatus.ACTIVE},
                {"Nước giải khát", "SKU-8931108", "8936012345678", "Cà phê sữa đá Highlands Coffee lon 185ml", "Lon", "16000", "85.000", "24.000", true, GeneralStatus.ACTIVE},
                {"Gia vị & Nước sốt", "SKU-8932489", "8935023901124", "Nước tương Nam Dương Đậm Đặc 500ml", "Chai", "22000", "12.000", "15.000", false, GeneralStatus.ACTIVE},
                {"Gia vị & Nước sốt", "SKU-8933091", "8936034567891", "Nước mắm Nam Ngư Đệ Nhị 900ml", "Chai", "42000", "60.000", "15.000", false, GeneralStatus.ACTIVE},
                {"Nước giải khát", "SKU-8934002", "8935012398451", "Bia Heineken Silver lon cao 330ml (Thùng 24)", "Thùng", "415000", "4.000", "10.000", true, GeneralStatus.ACTIVE},
                {"Gạo & Nông sản", "SKU-8935198", "8935221004512", "Gạo thơm ST25 Neptune Thượng Hạng túi 5kg", "Túi", "195000", "45.000", "10.000", false, GeneralStatus.ACTIVE},
                {"Gia vị & Nước sốt", "SKU-8936614", "8934680010052", "Dầu ăn dinh dưỡng Simply Canola 1L", "Chai", "68000", "0.000", "20.000", false, GeneralStatus.ACTIVE},
                {"Bánh kẹo", "SKU-8937021", "8934752001923", "Bánh quy Cosy Marie Kinh Đô 336g", "Hộp", "46000", "95.000", "15.000", true, GeneralStatus.ACTIVE},
                {"Nước giải khát", "SKU-8938112", "8935049500018", "Nước ngọt Coca-Cola Không Đường lon 320ml", "Lon", "10000", "180.000", "30.000", true, GeneralStatus.ACTIVE},
                {"Nước giải khát", "SKU-8938201", "8935049500025", "Nước ngọt Pepsi lon 320ml", "Lon", "10000", "210.000", "30.000", true, GeneralStatus.ACTIVE},
                {"Nước giải khát", "SKU-8938215", "8935049500094", "Nước khoáng thiên nhiên Lavie 500ml", "Chai", "6000", "140.000", "24.000", true, GeneralStatus.ACTIVE},
                {"Sữa & Trứng", "SKU-8930135", "8934567890208", "Sữa chua uống Vinamilk Susu hương dâu 110ml", "Lốc", "24000", "8.000", "15.000", true, GeneralStatus.ACTIVE},
                {"Sữa & Trứng", "SKU-8930146", "8934567890314", "Trứng gà Ba Huân hộp 10 quả (Size L)", "Hộp", "34000", "50.000", "15.000", true, GeneralStatus.ACTIVE},
                {"Sữa & Trứng", "SKU-8930157", "8934567890421", "Bơ lạt tự nhiên TH true BUTTER 200g", "Khối", "55000", "0.000", "10.000", true, GeneralStatus.ACTIVE},
                {"Mì ăn liền", "SKU-8930523", "8935001234673", "Mì Omachi Xốt Bò Hầm 80g", "Gói", "8500", "120.000", "30.000", true, GeneralStatus.ACTIVE},
                {"Mì ăn liền", "SKU-8930534", "8935001234789", "Mì trộn tương đen Samyang Hàn Quốc 140g", "Gói", "28000", "5.000", "12.000", true, GeneralStatus.ACTIVE},
                {"Mì ăn liền", "SKU-8930545", "8935001234895", "Phở bò Đệ Nhất Acecook 68g", "Gói", "7500", "75.000", "20.000", true, GeneralStatus.ACTIVE},
                {"Gia vị & Nước sốt", "SKU-8932501", "8935023901230", "Tương ớt Chinsu chai 250g", "Chai", "14500", "110.000", "20.000", false, GeneralStatus.ACTIVE},
                {"Gia vị & Nước sốt", "SKU-8932512", "8935023901346", "Hạt nêm Knorr thịt thăn xương ống & tủy 900g", "Gói", "82000", "35.000", "10.000", false, GeneralStatus.ACTIVE},
                {"Bánh kẹo", "SKU-8937032", "8934752002036", "Bánh ChocoPie Orion hộp 12 cái 396g", "Hộp", "58000", "40.000", "10.000", true, GeneralStatus.ACTIVE},
                {"Bánh kẹo", "SKU-8937043", "8934752002142", "Kẹo dẻo Chupa Chups Hương Trái Cây 90g", "Gói", "13000", "6.000", "15.000", true, GeneralStatus.ACTIVE},
                {"Gạo & Nông sản", "SKU-8935209", "8935221004628", "Gạo lứt tím than ST25 Hoàng Kim 2kg", "Túi", "90000", "18.000", "5.000", false, GeneralStatus.ACTIVE},
                {"Chăm sóc cá nhân", "SKU-8939011", "8934868001019", "Dầu gội Clear Men Cool Sport Bạc Hà 630g", "Chai", "185000", "25.000", "5.000", false, GeneralStatus.ACTIVE},
                {"Chăm sóc cá nhân", "SKU-8939022", "8934868002023", "Kem đánh răng Colgate Total than hoạt tính 180g", "Tuýp", "49000", "45.000", "10.000", false, GeneralStatus.ACTIVE},
                {"Vệ sinh nhà cửa", "SKU-8939033", "8934868003037", "Nước giặt OMO Matic cửa trên túi 3.6kg", "Túi", "215000", "20.000", "5.000", false, GeneralStatus.ACTIVE},
                {"Vệ sinh nhà cửa", "SKU-8939044", "8934868004041", "Nước rửa chén Sunlight Chanh túi 3.6kg", "Túi", "110000", "0.000", "8.000", false, GeneralStatus.ACTIVE},
                {"Đồ đông lạnh & Chế biến", "SKU-8939999", "8939999999999", "Xúc xích Vissan Heo tiệt trùng 4 cây (Mẫu cũ)", "Gói", "18000", "0.000", "0.000", true, GeneralStatus.INACTIVE},
                {"Nước giải khát", "SKU-8939998", "8939999999982", "Trà sữa Kirin Tea Break chai 345ml (Tạm ngưng nhập)", "Chai", "15000", "15.000", "10.000", true, GeneralStatus.INACTIVE}
        };

        for (Object[] item : productsData) {
            String catName = (String) item[0];
            String code = (String) item[1];
            String barcode = (String) item[2];
            String name = (String) item[3];
            String unit = (String) item[4];
            BigDecimal price = new BigDecimal((String) item[5]);
            BigDecimal stock = new BigDecimal((String) item[6]);
            BigDecimal minStock = new BigDecimal((String) item[7]);
            Boolean trackExp = (Boolean) item[8];
            GeneralStatus status = (GeneralStatus) item[9];

            if (!productRepository.existsByProductCode(code)) {
                Category cat = catMap.get(catName);
                if (cat != null) {
                    Product p = new Product();
                    p.setCategory(cat);
                    p.setProductCode(code);
                    p.setBarcode(barcode);
                    p.setProductName(name);
                    p.setUnit(unit);
                    p.setSellingPrice(price);
                    p.setStockQuantity(stock);
                    p.setMinimumStockLevel(minStock);
                    p.setTrackExpiry(trackExp);
                    p.setStatus(status);
                    productRepository.save(p);
                }
            }
        }
        log.info("Initialized default product catalog successfully with {} products.", productRepository.count());
    }
}
