package com.lcsc.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据库初始化验证配置
 *
 * @author lcsc-crawler
 */
@Component
public class DatabaseInitializationConfig implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        System.out.println("=================================");
        System.out.println("开始验证数据库初始化状态...");
        System.out.println("=================================");

        try {
            // 检查数据库连接
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                System.out.println("数据库连接成功！");
                System.out.println("数据库类型: " + metaData.getDatabaseProductName());
                System.out.println("数据库版本: " + metaData.getDatabaseProductVersion());
                System.out.println("数据库名称: " + connection.getCatalog());
                System.out.println();

                // 检查表是否存在
                List<String> tables = getExistingTables(connection);
                System.out.println("当前数据库中的表:");
                if (tables.isEmpty()) {
                    System.out.println("  没有找到任何表，可能初始化失败");
                } else {
                    tables.forEach(table -> System.out.println("  ✓ " + table));
                }
                System.out.println();

                // 验证关键表
                validateKeyTables(tables);

                // 检查数据记录数
                checkTableRecords();

                // 验证category_level1_id字段数据
                validateCategoryLevel1IdData();
            }

        } catch (Exception e) {
            System.err.println("数据库初始化验证失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("=================================");
        System.out.println("数据库初始化验证完成！");
        System.out.println("=================================");
    }

    private List<String> getExistingTables(Connection connection) throws Exception {
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private void validateKeyTables(List<String> existingTables) {
        String[] keyTables = {
            "category_level1_codes",
            "category_level2_codes",
            "crawler_config",
            "crawler_tasks",
            "image_links",
            "products",
            "shops",
            "task_logs"
        };

        System.out.println("关键表检查:");
        for (String table : keyTables) {
            if (existingTables.contains(table)) {
                System.out.println("  ✓ " + table + " - 存在");
            } else {
                System.out.println("  ✗ " + table + " - 缺失");
            }
        }
        System.out.println();
    }

    private void checkTableRecords() {
        try {
            String[] tablesToCheck = {
                "category_level1_codes",
                "category_level2_codes",
                "crawler_config",
                "crawler_tasks"
            };

            System.out.println("表记录数检查:");
            for (String table : tablesToCheck) {
                try {
                    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
                    if (count != null) {
                        System.out.println("  " + table + ": " + count + " 条记录");
                    }
                } catch (Exception e) {
                    System.out.println("  " + table + ": 查询失败 - " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("检查表记录数时出错: " + e.getMessage());
        }
    }

    private void validateCategoryLevel1IdData() {
        try {
            System.out.println("验证category_level1_id字段数据:");

            // 检查category_level2_codes表中category_level1_id字段的数据
            String sql = "SELECT id, category_level2_name, category_level1_id, catalog_id FROM category_level2_codes ORDER BY id DESC LIMIT 10";
            List<Map<String, Object>> recentRecords = jdbcTemplate.queryForList(sql);

            if (recentRecords.isEmpty()) {
                System.out.println("  category_level2_codes表中没有数据");
                return;
            }

            System.out.println("  最近10条二级分类记录:");
            for (Map<String, Object> record : recentRecords) {
                Integer id = (Integer) record.get("id");
                String name = (String) record.get("category_level2_name");
                Integer level1Id = (Integer) record.get("category_level1_id");
                String catalogId = (String) record.get("catalog_id");

                System.out.println(String.format("    ID: %d, Name: %s, L1_ID: %s, Catalog: %s",
                    id, name, level1Id, catalogId));
            }

            // 统计category_level1_id为NULL的记录数
            String nullCountSql = "SELECT COUNT(*) FROM category_level2_codes WHERE category_level1_id IS NULL";
            Integer nullCount = jdbcTemplate.queryForObject(nullCountSql, Integer.class);

            // 统计总记录数
            String totalCountSql = "SELECT COUNT(*) FROM category_level2_codes";
            Integer totalCount = jdbcTemplate.queryForObject(totalCountSql, Integer.class);

            System.out.println("  category_level1_id字段统计:");
            System.out.println("    总记录数: " + totalCount);
            System.out.println("    NULL值记录数: " + nullCount);
            System.out.println("    非NULL值记录数: " + (totalCount - nullCount));

            if (nullCount > 0) {
                System.out.println("  ⚠️  警告: 有 " + nullCount + " 条记录的category_level1_id为NULL!");

                // 显示NULL值的记录
                String nullRecordsSql = "SELECT id, category_level2_name, catalog_id FROM category_level2_codes WHERE category_level1_id IS NULL LIMIT 5";
                List<Map<String, Object>> nullRecords = jdbcTemplate.queryForList(nullRecordsSql);
                System.out.println("  前5条NULL值记录:");
                for (Map<String, Object> record : nullRecords) {
                    Integer id = (Integer) record.get("id");
                    String name = (String) record.get("category_level2_name");
                    String catalogId = (String) record.get("catalog_id");
                    System.out.println(String.format("    ID: %d, Name: %s, Catalog: %s", id, name, catalogId));
                }
            }

        } catch (Exception e) {
            System.err.println("验证category_level1_id字段数据时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}