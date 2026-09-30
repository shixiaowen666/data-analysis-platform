import java.sql.*;

/**
 * 验证 /parse 接口 JDBC 流程
 */
public class ParseTest {

    static final String USERNAME = "hydwsdb";
    static final String PASSWORD = "jlsdws@2025HY";
    static final String JDBC_URL = "jdbc:postgresql://ip:port/szcams_dws?currentSchema=ams_app";
    static final String SQL = "WITH day_series AS (\n" +
            "    -- 按天展开，仅用于分组，绝不参与分区大表扫描条件\n" +
            "    SELECT generate_series(\n" +
            "               '2027-07-07 00:00:00'::timestamp,\n" +
            "               '2027-07-11 00:00:00'::timestamp,\n" +
            "               interval '1day'\n" +
            "           ) AS d\n" +
            "),\n" +
            "pv_power_stat_raw AS (\n" +
            "    -- 分区大表一次性静态裁剪：\n" +
            "    -- 下界 = 遍历区间起点所在年份的年初（覆盖所有天各自的年累计起点中最早者）\n" +
            "    -- 上界 = 遍历区间终点次日（半开区间）\n" +
            "    -- 两侧均为占位符字面量运算，替换后为纯字面量，data_time 为裸列 -> 触发 Static Prune\n" +
            "    SELECT\n" +
            "        dc_no,\n" +
            "        dc_name,\n" +
            "        supply_org_no,\n" +
            "        data_time,\n" +
            "        generated_electricity_amount,\n" +
            "        online_electricity_consumption\n" +
            "    FROM hydwsdb.mk_mc_pv_power_statistics AS pv_power_stat\n" +
            "    WHERE pv_power_stat.delete_flag = 1\n" +
            "      AND pv_power_stat.data_time >= date_trunc('year', '2026-07-01 00:00:00'::timestamp)\n" +
            "      AND pv_power_stat.data_time <  '2026-07-01 00:00:00'::timestamp + interval '1day'\n" +
            "),\n" +
            "daily_annual_cum AS (\n" +
            "    -- 在已裁剪小表上，对每天 d 做年累计归属判断：\n" +
            "    -- data_time ∈ [date_trunc('year', d), d + interval '1day')\n" +
            "    SELECT\n" +
            "        ds.d,\n" +
            "        r.supply_org_no,\n" +
            "        r.dc_no,\n" +
            "        r.dc_name,\n" +
            "        SUM(r.generated_electricity_amount) AS sum_generation,\n" +
            "        SUM(r.online_electricity_consumption) AS sum_grid_connection\n" +
            "    FROM day_series ds\n" +
            "    JOIN pv_power_stat_raw r\n" +
            "      ON r.data_time >= date_trunc('year', ds.d)\n" +
            "     AND r.data_time <  ds.d + interval '1day'\n" +
            "    GROUP BY ds.d, r.supply_org_no, r.dc_no, r.dc_name\n" +
            "),\n" +
            "org_levels AS (\n" +
            "    -- 组织层级维表：预先计算三个组织名称字段，供关联使用\n" +
            "    SELECT\n" +
            "        org_no,\n" +
            "        city_org_short_name,\n" +
            "        dis_org_short_name,\n" +
            "        CASE\n" +
            "            WHEN city_org_short_name IS NULL THEN NULL\n" +
            "            WHEN dis_org_short_name = org_name THEN NULL\n" +
            "            WHEN LENGTH(org_name) = 3 THEN NULL\n" +
            "            ELSE org_name\n" +
            "        END AS org_name\n" +
            "    FROM ams_app.mk_mc_sys_org_levels org\n" +
            "    WHERE org.delete_flag = '1'\n" +
            ")\n" +
            "SELECT\n" +
            "    dac.d AS data_time,                                   -- 真实业务时间维度（该行归属的天）\n" +
            "    dac.supply_org_no,                                    -- 供电单位编码维度\n" +
            "    ol.city_org_short_name,                               -- 市级组织简称\n" +
            "    ol.dis_org_short_name,                                -- 区县级组织简称\n" +
            "    ol.org_name,                                          -- 组织名称（按规则处理后）\n" +
            "    -- 年累计发电量(万kWh)\n" +
            "    dac.sum_generation AS annual_cumulative_generation,\n" +
            "    -- 年累计上网电量(万kWh)\n" +
            "    dac.sum_grid_connection AS annual_cumulative_grid_connection,\n" +
            "    -- 年累计光伏自用电量(万kWh)\n" +
            "    dac.sum_generation - dac.sum_grid_connection AS annual_cumulative_self_consumption\n" +
            "FROM daily_annual_cum dac\n" +
            "LEFT JOIN org_levels ol\n" +
            "  ON ol.org_no = dac.supply_org_no\n" +
            "ORDER BY dac.d, dac.supply_org_no, dac.dc_no, dac.dc_name";

    public static void main(String[] args) {
        System.out.println("JDBC_URL: " + JDBC_URL);
        // 先查数据，再拿元数据（数据返回时元数据也带回来了）
        String wrappedSql =   SQL + " LIMIT 3";
        System.out.println("SQL: " + wrappedSql);
        long t0 = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(wrappedSql)) {
            System.out.println("连接+执行: " + (System.currentTimeMillis() - t0) + "ms");
            // 1. 先读数据
            long t = System.currentTimeMillis();
            ResultSetMetaData meta = rs.getMetaData();
            int n = meta.getColumnCount();
            int rowCount = 0;
            while (rs.next()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= n; i++) {
                    if (i > 1) sb.append(" | ");
                    sb.append(rs.getString(i));
                }
                System.out.println("  行" + (rowCount + 1) + ": " + sb);
                rowCount++;
            }
            System.out.println("读数据: " + rowCount + " 行, " + (System.currentTimeMillis() - t) + "ms");
            // 2. 再拿元数据详情（数据已返回，列信息缓存好了）
            t = System.currentTimeMillis();
            for (int i = 1; i <= n; i++) {
                System.out.println("  字段" + i + ": " + meta.getColumnLabel(i) + " (" + meta.getColumnTypeName(i) + ")");
            }
            System.out.println("获取元数据详情: " + (System.currentTimeMillis() - t) + "ms");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("========== 总耗时: " + (System.currentTimeMillis() - t0) + "ms ==========");
    }
}
