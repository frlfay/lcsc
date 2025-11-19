package com.lcsc.service.crawler;

/**
 * 立创商城API端点统一管理类
 * 包含所有API接口的URL定义和常量
 * 
 * @author lcsc-crawler
 * @since 2025-09-05
 */
public final class LcscApiEndpoints {
    
    /**
     * API基础URL
     */
    public static final String BASE_URL = "https://wmsc.lcsc.com/ftps/wm/product";
    
    /**
     * API端点路径
     */
    public static final class Paths {
        /**
         * 获取所有一二级分类目录
         */
        public static final String CATALOG_LIST = "/catalog/list";
        
        /**
         * 获取筛选条件
         */
        public static final String QUERY_PARAM_GROUP = "/query/param/group";
        
        /**
         * 获取产品列表
         */
        public static final String QUERY_LIST = "/query/list";
        
    }
    
    /**
     * 完整的API URL
     */
    public static final class Urls {
        /**
         * 获取所有一二级分类目录的完整URL
         */
        public static final String CATALOG_LIST = BASE_URL + Paths.CATALOG_LIST;
        
        /**
         * 获取筛选条件的完整URL
         */
        public static final String QUERY_PARAM_GROUP = BASE_URL + Paths.QUERY_PARAM_GROUP;
        
        /**
         * 获取产品列表的完整URL
         */
        public static final String QUERY_LIST = BASE_URL + Paths.QUERY_LIST;
        
    }
    
    /**
     * HTTP方法定义
     */
    public static final class Methods {
        public static final String GET = "GET";
        public static final String POST = "POST";
    }
    
    /**
     * API配置常量
     */
    public static final class Config {
        /**
         * 请求间隔（毫秒）
         */
        public static final long REQUEST_INTERVAL = 2000L;
        
        /**
         * 最大重试次数
         */
        public static final int MAX_RETRIES = 3;
        
        /**
         * 默认页面大小
         */
        public static final int DEFAULT_PAGE_SIZE = 25;
        
        /**
         * 默认起始页码
         */
        public static final int DEFAULT_CURRENT_PAGE = 1;
    }
    
    /**
     * HTTP请求头常量
     */
    public static final class Headers {
        public static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";
        public static final String REFERER = "https://www.lcsc.com/";
        public static final String ORIGIN = "https://www.lcsc.com";
        public static final String ACCEPT = "application/json, text/plain, */*";
        public static final String ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en;q=0.8";
        public static final String ACCEPT_ENCODING = "gzip, deflate, br, zstd";
        public static final String CACHE_CONTROL = "no-cache";
        public static final String PRAGMA = "no-cache";
        public static final String X_REQUESTED_WITH = "XMLHttpRequest";

        // 现代浏览器安全头
        public static final String SEC_CH_UA = "\"Google Chrome\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\"";
        public static final String SEC_CH_UA_MOBILE = "?0";
        public static final String SEC_CH_UA_PLATFORM = "\"macOS\"";
        public static final String SEC_FETCH_DEST = "document";
        public static final String SEC_FETCH_MODE = "navigate";
        public static final String SEC_FETCH_SITE = "same-origin";
        public static final String PRIORITY = "u=1, i";
    }
    
    /**
     * API筛选参数常量
     */
    public static final class FilterParams {
        /**
         * 筛选条件字段名
         */
        public static final String KEYWORD = "keyword";
        public static final String CATALOG_ID_LIST = "catalogIdList";
        public static final String BRAND_ID_LIST = "brandIdList";
        public static final String ENCAP_VALUE_LIST = "encapValueList";
        public static final String IS_STOCK = "isStock";
        public static final String IS_OTHER_SUPPLIERS = "isOtherSuppliers";
        public static final String IS_ASIAN_BRAND = "isAsianBrand";
        public static final String IS_DEALS = "isDeals";
        public static final String IS_ENVIRONMENT = "isEnvironment";
        public static final String PARAM_NAME_VALUE_MAP = "paramNameValueMap";
        public static final String PRODUCT_ARRANGE_LIST = "productArrangeList";
        public static final String CURRENT_PAGE = "currentPage";
        public static final String PAGE_SIZE = "pageSize";
        
        /**
         * 默认筛选值
         */
        public static final boolean DEFAULT_IS_STOCK = false;
        public static final boolean DEFAULT_IS_OTHER_SUPPLIERS = false;
        public static final boolean DEFAULT_IS_ASIAN_BRAND = false;
        public static final boolean DEFAULT_IS_DEALS = false;
        public static final boolean DEFAULT_IS_ENVIRONMENT = false;
        public static final String DEFAULT_KEYWORD = "";
    }
    
    // 私有构造函数，防止实例化
    private LcscApiEndpoints() {
        throw new AssertionError("LcscApiEndpoints should not be instantiated");
    }
}