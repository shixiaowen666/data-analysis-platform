package com.senses.permission.model.dataroleVo;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "绑定用户VO")
public class UserBindVo {
    @Schema(description = "授权对象名称")
    private String bindName;
    @Schema(description = "绑定类型：0.用户绑定1.部门绑定2.用户组绑定",example = "1212")
    private Integer bindType;//
    @Schema(description = "用户id",example = "1212")
    private Long userId;
    /** 用户名 */
    @Schema(description = "用户名")
    private String username;
    /** 姓名 */
    @Schema(description = "姓名")
    private String name;
    /** 部门名称 */
    @Schema(description = "部门名称")
    private String deptName;



}
