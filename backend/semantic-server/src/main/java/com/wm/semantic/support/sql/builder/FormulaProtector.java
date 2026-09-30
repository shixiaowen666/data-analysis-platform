package com.wm.semantic.support.sql.builder;

/**
 * 计算指标公式除零防护。
 *
 * 把公式中每个除法的分母整段包 NULLIF(expr, 0)，除零时结果为 NULL 而不是报错。
 * 分母是"紧跟顶层 '/' 之后、到下一个顶层运算符为止"的整段子表达式，
 * 因此复合分母（如 ({a}+{b})/({c}+{d})）也能被整体防护，
 * 而非仅防护紧跟 '/' 的单个占位符。
 */
public final class FormulaProtector {

    private FormulaProtector() {
    }

    /**
     * 对公式做除零防护。
     *
     * 两遍处理：
     * 1. 递归处理括号组内部，保证嵌套除法（如 a/(b/c)）也被防护；
     * 2. 处理顶层 '/'：右侧整段子表达式（分母）包 NULLIF(denominator, 0)。
     */
    public static String protect(String expr) {
        if (expr == null || expr.isEmpty()) {
            return expr;
        }

        // 第一遍：递归处理括号组内部
        StringBuilder flat = new StringBuilder();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == '(') {
                int end = findMatchingParen(expr, i);
                flat.append('(').append(protect(expr.substring(i + 1, end))).append(')');
                i = end + 1;
            } else {
                flat.append(c);
                i++;
            }
        }
        String processed = flat.toString();

        // 第二遍：顶层 '/' 的分母整段包 NULLIF
        StringBuilder out = new StringBuilder();
        i = 0;
        int depth = 0;
        while (i < processed.length()) {
            char c = processed.charAt(i);
            if (c == '(') {
                depth++;
                out.append(c);
                i++;
            } else if (c == ')') {
                depth--;
                out.append(c);
                i++;
            } else if (c == '/' && depth == 0) {
                int start = i + 1;
                int j = start;
                // 分母起始的 + / - 视为一元符号（如 a/-b）
                if (j < processed.length()
                        && (processed.charAt(j) == '+' || processed.charAt(j) == '-')) {
                    j++;
                }
                int denominatorDepth = 0;
                while (j < processed.length()) {
                    char cj = processed.charAt(j);
                    if (cj == '(') {
                        denominatorDepth++;
                    } else if (cj == ')') {
                        denominatorDepth--;
                    } else if (denominatorDepth == 0
                            && (cj == '+' || cj == '-' || cj == '*' || cj == '/')) {
                        break;
                    }
                    j++;
                }
                out.append("/NULLIF(").append(processed, start, j).append(", 0)");
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int findMatchingParen(String expr, int openIdx) {
        int depth = 1;
        for (int i = openIdx + 1; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return expr.length() - 1;
    }
}
