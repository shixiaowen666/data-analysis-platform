package com.chatbi.chat.enums;

/**
 * 数据类型映射
 * 匹配olap模型的数据类型
 * https://prestodb.io/docs/current/language/types.html
 * https://clickhouse.tech/docs/en/sql-reference/data-types/
 * @Version: 0.0.1
 * @Author: guoxp
 * @Date: 2021/3/11 11:51 上午
 */
public enum ColumnTypeEnum {
    //array
    ARRAY(0,"array","array","Array","ARRAY",12),
    MAP(1,"map","map","Map","MAP",12),
    STRUCT(2,"struct","struct","Nested","",12),
    BINARY(3,"binary","binary","","VARBINARY",12),
    BOOLEAN(4,"boolean","boolean","Boolean","BOOLEAN",11),
    DECIMAL(5,"decimal","decimal(20,4)","Decimal(20,4)","DECIMAL(20,4)",5),
    DOUBLE(6,"double","double","Float64","DOUBLE",5),
    FLOAT(7,"float","float","Float32","REAL",5),
    INT(8,"int","int","Int32","INTEGER",11),
    SMALLINT(9,"smallint","smallint","Int16","SMALLINT",11),
    TINYINT(10,"tinyint","tinyint","Int8","TINYINT",11),
    BIGINT(11,"bigint","bigint","Int64","BIGINT",11),
    STRING(12,"string","string","String","VARCHAR",12),
    CHAR(13,"char","char","String","CHAR",12),
    VARCHAR(14,"varchar","varchar","Varchar","VARCHAR",12),
    TIMESTAMP(15,"timestamp","timestamp","DateTime","TIMESTAMP",12),
    DATE(16,"date","date","Date","DATE",12);
    private Integer key;
    /**
     * 名称 以hive中为准
     */
    private String name;
    /**
     * hive 中的类型
     */
    private String hiveType;
    /**
     * clickhouse 的类型
     */
    private String ckType;
    /**
     * persto 中的类型
     */
    private String perstoType;
    /**
     * olap建模转换后的类型
     */
    private Integer olapType;

    ColumnTypeEnum(Integer key, String name, String hiveType, String ckType, String perstoType, Integer olapType) {
        this.key = key;
        this.name = name;
        this.hiveType = hiveType;
        this.ckType = ckType;
        this.perstoType = perstoType;
        this.olapType = olapType;
    }



    public Integer getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public static ColumnTypeEnum getByName(String name) {
        if (isBlank(name)){
            return null;
        }
        String typeName = typeTrim(name);
        for (ColumnTypeEnum type : ColumnTypeEnum.values()) {
            if (typeTrim(type.hiveType).equalsIgnoreCase(typeName)) {
                return type;
            }
        }
        return null;
    }

    public static ColumnTypeEnum getByKey(Integer key) {
        if (key==null){
            return null;
        }
        for (ColumnTypeEnum type : ColumnTypeEnum.values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 匹配hive类型
     * @param hiveType  hiveType
     * @return ColumnTypeEnum
     */
    public static ColumnTypeEnum getByHiveType(String hiveType) {
       return getByName(hiveType);
    }

    /**
     * 匹配clickhouse类型
     * @param ckType clickhouse类型
     * @return ColumnTypeEnum
     */
    public static ColumnTypeEnum getByCkType(String ckType) {
        if (isBlank(ckType)){
            return null;
        }
        String typeName = typeTrim(ckType);
        for (ColumnTypeEnum type : ColumnTypeEnum.values()) {
            if (typeTrim(type.ckType).equalsIgnoreCase(typeName)) {
                return type;
            }
        }
        return null;
    }
    /**
     * 匹配persto类型
     * @param perstoType persto类型
     * @return ColumnTypeEnum
     */
    public static ColumnTypeEnum getByPerstoType(String perstoType) {
        if (isBlank(perstoType)){
            return null;
        }
        String typeName = typeTrim(perstoType);
        for (ColumnTypeEnum type : ColumnTypeEnum.values()) {
            if (typeTrim(type.perstoType).equalsIgnoreCase(typeName)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 换转为olap模型需要的数据类型
     * @param orignalType 原始类型
     * @return ColumnTypeEnum
     */
    public static ColumnTypeEnum transformToOlapType(ColumnTypeEnum orignalType) {
       return ColumnTypeEnum.getByKey(orignalType.getOlapType());
    }

    /**
     * 只去最前面的类型
     * @param typeName
     * @return
     */
    private static String typeTrim(String typeName){
        //return typeName.replaceAll("(\\(.*\\))|(<.*>)", "");
        return typeName;
    }

    private   static boolean isBlank(CharSequence cs) {
        int strLen;
        if (cs != null && (strLen = cs.length()) != 0) {
            for(int i = 0; i < strLen; ++i) {
                if (!Character.isWhitespace(cs.charAt(i))) {
                    return false;
                }
            }

            return true;
        } else {
            return true;
        }
    }

    public String getHiveType() {
        return hiveType;
    }

    public String getCkType() {
        return ckType;
    }


    public String getPerstoType() {
        return perstoType;
    }

    public Integer getOlapType() {
        return olapType;
    }

    @Override
    public String toString() {
        return "ColumnTypeEnum{"+this.name() +
                ":[key=" + key +
                ", hiveType='" + hiveType + "']}";
    }

    public static void main(String[] args) {
        String typeName = "decimal(10,2)";
        System.out.println(ColumnTypeEnum.transformToOlapType(ColumnTypeEnum.getByCkType("varchar")));
        System.out.println(ColumnTypeEnum.getByHiveType(typeName).getCkType());
        System.out.println(ColumnTypeEnum.getByHiveType(typeName).getPerstoType());

    }


}
