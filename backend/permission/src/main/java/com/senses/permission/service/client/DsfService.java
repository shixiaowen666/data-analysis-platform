// package com.senses.permission.service.client;
//
// import com.senses.permission.model.ResultData;
// import com.senses.permission.model.param.DsfAuditLog;
// import io.swagger.annotations.ApiOperation;
// import org.springframework.cloud.openfeign.FeignClient;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestBody;
//
// import java.util.List;
//
// /**
//  * 安全审计
//  */
// @FeignClient(name = "service-dsf")
// public interface DsfService {
//     @PostMapping(value = "/dsf/security/log/saveRoleLog")
//     @Operation(summary = "安全策略-安全审计-功能角色-数据角色数据保存")
//     public ResultData saveRoleLog(@RequestBody List<DsfAuditLog> auditLog);
//
//     @PostMapping(value = "/dsf/security/log/saveSelfRoleLog")
//     @Operation(summary = "安全策略-安全审计-个人权限保存")
//     public ResultData saveSelfRoleLog(@RequestBody List<DsfAuditLog> auditLog);
//
// }
