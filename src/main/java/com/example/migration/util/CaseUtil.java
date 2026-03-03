package com.example.migration.util;

public class CaseUtil {

    /**
     * 转换为小驼峰命名 (lowerCamelCase)
     * 例如: user_name, user-name, user name -> userName
     */
    public static String toLowerCamelCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        // 分割字符串：支持 _, -, 空格
        String[] parts = str.split("[_\\-\\s]+");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;

            // 首字母转大写
            String capitalized = part.substring(0, 1).toUpperCase() + part.substring(1).toLowerCase();

            if (i == 0) {
                // 第一个单词首字母小写
                sb.append(Character.toLowerCase(capitalized.charAt(0)));
                if (capitalized.length() > 1) {
                    sb.append(capitalized.substring(1));
                }
            } else {
                sb.append(capitalized);
            }
        }
        return sb.toString();
    }

    /**
     * 转换为大驼峰命名 (UpperCamelCase)
     * 例如: user_name, user-name, user name -> UserName
     */
    public static String toUpperCamelCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        String lowerCamel = toLowerCamelCase(str);
        if (lowerCamel.isEmpty()) {
            return lowerCamel;
        }
        // 将首字母转为大写
        return Character.toUpperCase(lowerCamel.charAt(0)) + lowerCamel.substring(1);
    }

    // 测试示例
    public static void main(String[] args) {
        String input1 = "user_info_list";
        String input2 = "order-item-detail";
        String input3 = "customer data";

        System.out.println(toLowerCamelCase(input1)); // 输出: userInfoList
        System.out.println(toLowerCamelCase(input2)); // 输出: orderItemDetail
        System.out.println(toLowerCamelCase(input3)); // 输出: customerData

        System.out.println(toUpperCamelCase(input1)); // 输出: UserInfoList
    }
}
