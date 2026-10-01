/* eslint-disable */
// ===================== 错误类型字典 =====================
window.ERROR_DICT = [
  { code: 'INTENT',      name: '意图识别错误', user: true,  admin: true,  stage: '拆解-理解问题', hint: '没听懂我在问什么（时间、对象、口径理解偏了）',
    evidence: '②拆解：steps 的 description / 识别时间范围 与原问题对比', fix: '拆解提示词、智能体知识库' },
  { code: 'TABLE_SELECT', name: '选表错误',   user: true,  admin: true,  stage: '召回 → 拆解选表', hint: '查的数据表不对',
    evidence: '①召回命中表 vs ③拆解 expected_table vs 实际表', fix: '表描述、召回配置、领域知识' },
  { code: 'METRIC_DIM',  name: '指标/维度提取错误', user: false, admin: true, stage: '取数映射', hint: '',
    evidence: '③取数映射：拆解列 → 映射到的指标/维度 ID，未映射列', fix: '指标/维度别名、描述、agg_type' },
  { code: 'METRIC_DIM.METRIC',    name: '指标错误', user: true, admin: true, parent: 'METRIC_DIM', stage: '取数映射', hint: '统计的指标不是我要的（如要线损率给了线损电量）',
    evidence: '同上（细分）', fix: '同上' },
  { code: 'METRIC_DIM.DIMENSION', name: '维度错误', user: true, admin: true, parent: 'METRIC_DIM', stage: '取数映射', hint: '分组/筛选的维度不对（如要区局给了变电站）',
    evidence: '同上（细分）', fix: '同上' },
  { code: 'DECOMPOSE',   name: '拆分错误',     user: true,  admin: true,  stage: '拆解-步骤/计算', hint: '步骤或计算逻辑不对（同比算成环比、少了一步）',
    evidence: '②拆解 DAG / compute 函数参数、④执行日志失败步骤', fix: '拆解提示词、计算函数库' },
  { code: 'SUMMARY',     name: '总结错误',     user: true,  admin: true,  stage: '总结', hint: '数据是对的，但文字结论说错了',
    evidence: '⑤送入总结的数据 vs 答案文本', fix: '总结提示词' },
  { code: 'RESULT',      name: '结果错误',     user: true,  admin: false, stage: '兜底', hint: '结果不对，但说不清是哪一环出了问题',
    evidence: '由管理员定位后细化为具体类型', fix: '—' },
];

// ===================== 表清单（供应选表下拉） =====================
window.ALL_TABLES = [
  'view_gdl','view_net_local_elec','view_zgdl_elec','view_pv_online_power','view_yongdian_user_scale',
  'view_line_loss_substation_fenqu','view_line_loss_management_view_by_voltage','view_line_loss_substation_rate',
  'view_Management_LineLoss_ManagementView_ZoneLineLossStatistics_ZoneLineLossRate','view_mgmt_line_loss_volt_month',
  'view_mk_mc_metering_coverage','view_power_management_consumption_overview','view_power_supply_overview_max_load',
  'view_electricity_auto_copy_rate_home','view_mgmt_line_loss_branch_io_elec',
];

// ===================== 会话数据 =====================
const ORGS = ['罗湖供电局','福田供电局','南山供电局','宝安供电局','龙岗供电局'];

function rowsFenqu() {
  return ORGS.map((o,i)=>({ ptdate:'2026-07-31', dis_org_name:o, ll_rate:(3.2+i*0.35).toFixed(2) }));
}
function rowsVolt() {
  return ['交流110kV','交流10kV','交流380V'].map((v,i)=>({ ptdate:'2026-07-31', voltage_level:v, ll_rate:(1.1+i*1.6).toFixed(2) }));
}

window.SESSIONS = [
  {
    chatId: 'c9f1-…-7a21', sessionId: 's-01', time: '2026-09-29 10:42', user: '张工', agent: '深圳供电局运营分析智能体',
    question: '深圳各区局7月分压线损率排名，哪个区局最高？',
    status: 'success', elapsed: 18.4,
    answer: '2026年7月，深圳各区局线损率排名：龙岗供电局 4.60% 最高，其次宝安 4.25%、南山 3.90%、福田 3.55%，罗湖 3.20% 最低。龙岗供电局线损率最高，建议重点关注。',
    feedback: { user:'张工', time:'2026-09-29 10:50', rating:-1, status:0, errorTypes:['TABLE_SELECT','METRIC_DIM.METRIC'],
      description:'我问的是「分压」线损率（按电压等级），返回的是分区线损率，表选错了。' },
    diagnosis: null,
    steps: [
      { stepId:'step_1', type:'query', description:'查询2026年7月各区局线损率', table:'view_line_loss_substation_fenqu', rowCount:5, status:'success',
        columns:['ptdate','dis_org_name','ll_rate'], rows: rowsFenqu() },
      { stepId:'step_2', type:'compute', description:'按线损率降序排序', func:'top_n', params:'sort_by=ll_rate, n=10', dependsOn:['step_1'], status:'success',
        columns:['dis_org_name','ll_rate'], rows: rowsFenqu().sort((a,b)=>b.ll_rate-a.ll_rate).map(r=>({dis_org_name:r.dis_org_name, ll_rate:r.ll_rate})) },
      { stepId:'step_3', type:'summarize', description:'总结排名结果', dependsOn:['step_2'], status:'success', text:'（见最终答案）' },
    ],
    trace: {
      model:'qwen3-235b-a22b', promptVersion:'prompt-main v1.0.5', currentDate:'2026-09-29', timeRange:'2026-07-01 ~ 2026-07-31', decomposeRetries:0,
      recall:{ enabled:true, elapsed:412, metaBefore:70, metaAfter:6,
        tables:['view_line_loss_substation_fenqu','view_Management_LineLoss_ManagementView_ZoneLineLossStatistics_ZoneLineLossRate','view_line_loss_substation_rate','view_mgmt_line_loss_branch_io_elec','view_management_view_mgmt_line_loss_month','view_gdl'],
        metrics:['分区线损率 ll_rate','分台区线损率 ll_rate','分区输入电量','分区输出电量','月度线损率','日供电量'],
        dims:['dis_org_name 区局','gdj 供电局','ptdate 日期','tq_name 台区'] },
      usedTables:['view_line_loss_substation_fenqu'],
      stepTraces:[
        { stepId:'step_1', queryDescription:'查询2026年7月各区局线损率', expectedTable:'view_line_loss_substation_fenqu', resolvedTable:'view_line_loss_substation_fenqu', resolvedTableId:1032, rowCount:5, elapsed:860,
          columns:[
            { name:'ptdate', role:'dimension', filter:'2026-07-01 ~ 2026-07-31', resolved:{ id:501, name:'日期 ptdate' } },
            { name:'dis_org_name', role:'dimension', filter:'', resolved:{ id:512, name:'区局名称 dis_org_name' } },
            { name:'ll_rate', role:'measure', filter:'', resolved:{ id:2087, name:'分区线损率 ll_rate', aggType:'avg' } },
          ],
          sql:"SELECT ptdate, dis_org_name, AVG(ll_rate) AS ll_rate\nFROM view_line_loss_substation_fenqu\nWHERE ptdate BETWEEN '2026-07-01' AND '2026-07-31'\nGROUP BY ptdate, dis_org_name\nORDER BY ptdate ASC\nLIMIT 5000" },
      ],
      execLog:[ {stepId:'step_1',status:'success',elapsed:860},{stepId:'step_2',status:'success',elapsed:12},{stepId:'step_3',status:'success',elapsed:6200} ],
      summaryInputRows:5, truncHead:50, truncTail:20,
    },
  },
  {
    chatId: 'b3e0-…-19cd', sessionId:'s-02', time:'2026-09-29 09:15', user:'李芳', agent:'深圳供电局运营分析智能体',
    question:'今年以来深圳月累计供电量同比增长多少？',
    status:'success', elapsed: 24.1,
    answer:'2026年1-9月深圳累计供电量 1,203.6 亿千瓦时，较2025年同期增长 62.3%。',
    feedback:{ user:'李芳', time:'2026-09-29 09:22', rating:-1, status:1, errorTypes:['RESULT'], description:'增长62%明显不对，往年都是个位数增长。' },
    diagnosis:{ primary:'DECOMPOSE', secondary:['METRIC_DIM.METRIC'], errorStepId:'step_3', fixStatus:1, by:'王管理员', at:'2026-09-29 14:30',
      rootCause:'monthpowersupply 为 cumulative 类型，模型对 1-9 月每月的月累计值又做了 sum，导致重复累加；且对比期取了 2025 全年而非 1-9 月。', fixType:'PROMPT_DECOMPOSE' },
    steps:[
      { stepId:'step_1', type:'query', description:'查询2026年1月1日至今月累计供电量', table:'view_gdl', rowCount:272, status:'success', columns:['ptdate','monthpowersupply'], rows:[{ptdate:'2026-01-31',monthpowersupply:'118.2'},{ptdate:'2026-02-28',monthpowersupply:'105.7'},{ptdate:'…',monthpowersupply:'…'}] },
      { stepId:'step_2', type:'query', description:'查询2025年全年月累计供电量', table:'view_gdl', rowCount:365, status:'success', columns:['ptdate','monthpowersupply'], rows:[{ptdate:'2025-01-31',monthpowersupply:'109.4'},{ptdate:'…',monthpowersupply:'…'}] },
      { stepId:'step_3', type:'compute', description:'汇总两期累计并计算增长率', func:'aggregate → subtract → divide', params:'method=sum, column=monthpowersupply', dependsOn:['step_1','step_2'], status:'success', columns:['period','total','growth_rate'], rows:[{period:'2026',total:'1203.6',growth_rate:'62.3%'},{period:'2025',total:'741.5',growth_rate:''}] },
      { stepId:'step_4', type:'summarize', description:'总结', dependsOn:['step_3'], status:'success', text:'（见最终答案）' },
    ],
    trace:{ model:'qwen3-235b-a22b', promptVersion:'prompt-main v1.0.5', currentDate:'2026-09-29', timeRange:'2026-01-01 ~ 2026-09-28 / 2025-01-01 ~ 2025-12-31', decomposeRetries:1,
      recall:{ enabled:true, elapsed:388, metaBefore:70, metaAfter:3, tables:['view_gdl','view_net_local_elec','view_power_management_consumption_overview'], metrics:['日供电量 daypowersupply','月累计供电量 monthpowersupply','年累计供电量 yearpowersupply','网供电量'], dims:['ptdate','gdj 供电局','city_org_name'] },
      usedTables:['view_gdl'],
      stepTraces:[
        { stepId:'step_1', queryDescription:'查询2026年1月1日至今月累计供电量', expectedTable:'view_gdl', resolvedTable:'view_gdl', resolvedTableId:1001, rowCount:272, elapsed:1240, columns:[ {name:'ptdate',role:'dimension',filter:'2026-01-01 ~ 2026-09-28',resolved:{id:501,name:'日期 ptdate'}}, {name:'monthpowersupply',role:'measure',filter:'',resolved:{id:2001,name:'月累计供电量',aggType:'cumulative'}} ], sql:"SELECT ptdate, monthpowersupply FROM view_gdl WHERE ptdate BETWEEN '2026-01-01' AND '2026-09-28' AND gdj='深圳供电局'" },
        { stepId:'step_2', queryDescription:'查询2025年全年月累计供电量', expectedTable:'view_gdl', resolvedTable:'view_gdl', resolvedTableId:1001, rowCount:365, elapsed:1310, columns:[ {name:'ptdate',role:'dimension',filter:'2025-01-01 ~ 2025-12-31',resolved:{id:501,name:'日期 ptdate'}}, {name:'monthpowersupply',role:'measure',filter:'',resolved:{id:2001,name:'月累计供电量',aggType:'cumulative'}} ], sql:"SELECT ptdate, monthpowersupply FROM view_gdl WHERE ptdate BETWEEN '2025-01-01' AND '2025-12-31' AND gdj='深圳供电局'" },
      ],
      execLog:[ {stepId:'step_1',status:'success',elapsed:1240},{stepId:'step_2',status:'success',elapsed:1310},{stepId:'step_3',status:'success',elapsed:35},{stepId:'step_4',status:'success',elapsed:5800} ],
      summaryInputRows:2, truncHead:50, truncTail:20 },
  },
  {
    chatId:'7d4a-…-c0e8', sessionId:'s-03', time:'2026-09-28 16:03', user:'陈明', agent:'深圳供电局运营分析智能体',
    question:'福田供电局昨天的终端覆盖率和覆盖用户数分别是多少？',
    status:'partial', elapsed: 31.7,
    answer:'福田供电局 2026-09-27 终端覆盖率 98.7%；覆盖用户数数据获取失败。',
    feedback:null, diagnosis:null,
    steps:[
      { stepId:'step_1', type:'query', description:'查询福田供电局昨日终端覆盖率与覆盖用户数', table:'view_mk_mc_metering_coverage', rowCount:1, status:'success', columns:['ptdate','org_name','coverage_rate','cover_user_cnt'], rows:[{ptdate:'2026-09-27',org_name:'福田供电局',coverage_rate:'98.7',cover_user_cnt:'412,380'}] },
      { stepId:'step_2', type:'compute', description:'快照指标取最新值', func:'latest_data', params:'time_column=ptdate, data=step_1', dependsOn:['step_1'], status:'failed', error:"latest_data: 输入含非快照列 coverage_rate（agg_type=avg），已剔除；输出列 cover_user_cnt 不存在于 step_1（实际列名 cover_user_count）" },
      { stepId:'step_3', type:'summarize', description:'总结', dependsOn:['step_1','step_2'], status:'success', text:'（见最终答案）' },
    ],
    trace:{ model:'qwen3-235b-a22b', promptVersion:'prompt-main v1.0.5', currentDate:'2026-09-28', timeRange:'2026-09-27', decomposeRetries:2,
      recall:{ enabled:true, elapsed:455, metaBefore:70, metaAfter:4, tables:['view_mk_mc_metering_coverage','view_mk_mc_integrity_rate','view_yongdian_user_scale','view_mk_mc_archive_scale_stat'], metrics:['终端覆盖率 coverage_rate','覆盖用户数 cover_user_count','未覆盖用户数','用户数 usercount'], dims:['org_name 机构','ptdate'] },
      usedTables:['view_mk_mc_metering_coverage'],
      stepTraces:[ { stepId:'step_1', queryDescription:'查询福田供电局昨日终端覆盖率与覆盖用户数', expectedTable:'view_mk_mc_metering_coverage', resolvedTable:'view_mk_mc_metering_coverage', resolvedTableId:1077, rowCount:1, elapsed:640,
        columns:[ {name:'ptdate',role:'dimension',filter:'= 2026-09-27',resolved:{id:501,name:'日期 ptdate'}}, {name:'org_name',role:'dimension',filter:'= 福田供电局',resolved:{id:530,name:'机构名称 org_name'}}, {name:'coverage_rate',role:'measure',filter:'',resolved:{id:2210,name:'终端覆盖率',aggType:'avg'}}, {name:'cover_user_cnt',role:'measure',filter:'',resolved:null} ],
        sql:"SELECT ptdate, org_name, coverage_rate FROM view_mk_mc_metering_coverage WHERE ptdate='2026-09-27' AND org_name='福田供电局'" } ],
      execLog:[ {stepId:'step_1',status:'success',elapsed:640},{stepId:'step_2',status:'failed',elapsed:8,error:'列 cover_user_cnt 不存在'},{stepId:'step_3',status:'success',elapsed:4900} ],
      summaryInputRows:1, truncHead:50, truncTail:20 },
  },
  {
    chatId:'e21b-…-55f0', sessionId:'s-04', time:'2026-09-28 11:20', user:'张工', agent:'线损管理智能体',
    question:'上个月各电压等级线损率对比',
    status:'success', elapsed:15.2,
    answer:'2026年8月各电压等级线损率：交流380V 4.30% 最高，交流10kV 2.70%，交流110kV 1.10% 最低。低压线损明显高于中高压。',
    feedback:{ user:'张工', time:'2026-09-28 11:30', rating:1, status:2, errorTypes:[], description:'' }, diagnosis:null,
    steps:[
      { stepId:'step_1', type:'query', description:'查询2026年8月各电压等级线损率', table:'view_line_loss_management_view_by_voltage', rowCount:3, status:'success', columns:['ptdate','voltage_level','ll_rate'], rows: rowsVolt() },
      { stepId:'step_2', type:'summarize', description:'总结', dependsOn:['step_1'], status:'success', text:'（见最终答案）' },
    ],
    trace:{ model:'qwen3-235b-a22b', promptVersion:'prompt-main v1.0.5', currentDate:'2026-09-28', timeRange:'2026-08-01 ~ 2026-08-31', decomposeRetries:0,
      recall:{ enabled:true, elapsed:370, metaBefore:70, metaAfter:2, tables:['view_line_loss_management_view_by_voltage','view_mgmt_line_loss_volt_month'], metrics:['分压线损率 ll_rate'], dims:['voltage_level 电压等级','ptdate'] }, usedTables:['view_line_loss_management_view_by_voltage'],
      stepTraces:[ { stepId:'step_1', queryDescription:'查询2026年8月各电压等级线损率', expectedTable:'view_line_loss_management_view_by_voltage', resolvedTable:'view_line_loss_management_view_by_voltage', resolvedTableId:1040, rowCount:3, elapsed:720, columns:[ {name:'ptdate',role:'dimension',filter:'2026-08-01 ~ 2026-08-31',resolved:{id:501,name:'日期 ptdate'}}, {name:'voltage_level',role:'dimension',filter:'',resolved:{id:540,name:'电压等级'}}, {name:'ll_rate',role:'measure',filter:'',resolved:{id:2090,name:'分压线损率',aggType:'avg'}} ], sql:"SELECT voltage_level, AVG(ll_rate) ll_rate FROM view_line_loss_management_view_by_voltage WHERE ptdate BETWEEN '2026-08-01' AND '2026-08-31' GROUP BY voltage_level" } ],
      execLog:[ {stepId:'step_1',status:'success',elapsed:720},{stepId:'step_2',status:'success',elapsed:4100} ], summaryInputRows:3, truncHead:50, truncTail:20 },
  },
  {
    chatId:'40aa-…-8e12', sessionId:'s-05', time:'2026-09-27 15:48', user:'王强', agent:'深圳供电局运营分析智能体',
    question:'深圳9月日供电量最高的一天是哪天',
    status:'success', elapsed:12.9,
    answer:'2026年9月截至27日，深圳日供电量最高的一天是 9月3日，达 4.82 亿千瓦时。',
    feedback:{ user:'王强', time:'2026-09-27 16:01', rating:-1, status:2, errorTypes:['SUMMARY'], description:'表格里最高的是9月5日 4.91，总结写成了9月3日。' },
    diagnosis:{ primary:'SUMMARY', secondary:[], errorStepId:'step_3', fixStatus:2, by:'王管理员', at:'2026-09-27 17:10', rootCause:'总结截断规则 head 50 行导致排序后的首行未进入总结上下文；总结模型引用了截断前的行。', fixType:'PROMPT_SUMMARY' },
    steps:[
      { stepId:'step_1', type:'query', description:'查询2026年9月深圳日供电量', table:'view_gdl', rowCount:27, status:'success', columns:['ptdate','daypowersupply'], rows:[{ptdate:'2026-09-01',daypowersupply:'4.62'},{ptdate:'2026-09-03',daypowersupply:'4.82'},{ptdate:'2026-09-05',daypowersupply:'4.91'},{ptdate:'…',daypowersupply:'…'}] },
      { stepId:'step_2', type:'compute', description:'取最高一天', func:'top_n', params:'sort_by=daypowersupply, n=1', dependsOn:['step_1'], status:'success', columns:['ptdate','daypowersupply'], rows:[{ptdate:'2026-09-05',daypowersupply:'4.91'}] },
      { stepId:'step_3', type:'summarize', description:'总结', dependsOn:['step_2'], status:'success', text:'（见最终答案）' },
    ],
    trace:{ model:'qwen3-235b-a22b', promptVersion:'prompt-main v1.0.5', currentDate:'2026-09-27', timeRange:'2026-09-01 ~ 2026-09-27', decomposeRetries:0,
      recall:{ enabled:true, elapsed:350, metaBefore:70, metaAfter:2, tables:['view_gdl','view_net_local_elec'], metrics:['日供电量','月累计供电量'], dims:['ptdate','gdj'] }, usedTables:['view_gdl'],
      stepTraces:[ { stepId:'step_1', queryDescription:'查询2026年9月深圳日供电量', expectedTable:'view_gdl', resolvedTable:'view_gdl', resolvedTableId:1001, rowCount:27, elapsed:590, columns:[ {name:'ptdate',role:'dimension',filter:'2026-09-01 ~ 2026-09-27',resolved:{id:501,name:'日期 ptdate'}}, {name:'daypowersupply',role:'measure',filter:'',resolved:{id:2000,name:'日供电量',aggType:'sum'}} ], sql:"SELECT ptdate, daypowersupply FROM view_gdl WHERE ptdate BETWEEN '2026-09-01' AND '2026-09-27' AND gdj='深圳供电局'" } ],
      execLog:[ {stepId:'step_1',status:'success',elapsed:590},{stepId:'step_2',status:'success',elapsed:5},{stepId:'step_3',status:'success',elapsed:3900} ], summaryInputRows:1, truncHead:50, truncTail:20 },
  },
  {
    chatId:'91c3-…-2b77', sessionId:'s-06', time:'2026-09-27 10:05', user:'李芳', agent:'深圳供电局运营分析智能体',
    question:'公变客户和专变客户8月用户数各多少，环比如何',
    status:'success', elapsed:22.6,
    answer:'2026年8月公变客户 2,145,300 户、专变客户 68,420 户；公变环比 +0.4%，专变环比 +0.9%。',
    feedback:{ user:'李芳', time:'2026-09-27 10:20', rating:-1, status:0, errorTypes:['METRIC_DIM.DIMENSION','RESULT'], description:'公变客户户数比实际少了一半左右，可能少算了「公线专变」。' },
    diagnosis:null,
    steps:[
      { stepId:'step_1', type:'query', description:'查询8月公变/专变客户用户数', table:'view_yongdian_user_scale', rowCount:62, status:'success', columns:['ptdate','user_type','usercount'], rows:[{ptdate:'2026-08-31',user_type:'公变客户',usercount:'2,145,300'},{ptdate:'2026-08-31',user_type:'专线专变客户',usercount:'68,420'}] },
      { stepId:'step_2', type:'query', description:'查询7月公变/专变客户用户数', table:'view_yongdian_user_scale', rowCount:62, status:'success', columns:['ptdate','user_type','usercount'], rows:[{ptdate:'2026-07-31',user_type:'公变客户',usercount:'2,136,700'}] },
      { stepId:'step_3', type:'compute', description:'快照取最新 + 环比', func:'latest_data → change', params:'group_by=user_type', dependsOn:['step_1','step_2'], status:'success', columns:['user_type','usercount','change_rate'], rows:[{user_type:'公变客户',usercount:'2,145,300',change_rate:'0.4%'},{user_type:'专线专变客户',usercount:'68,420',change_rate:'0.9%'}] },
      { stepId:'step_4', type:'summarize', description:'总结', dependsOn:['step_3'], status:'success', text:'（见最终答案）' },
    ],
    trace:{ model:'qwen3-235b-a22b', promptVersion:'prompt-main v1.0.5', currentDate:'2026-09-27', timeRange:'2026-08-01~08-31 / 2026-07-01~07-31', decomposeRetries:0,
      recall:{ enabled:true, elapsed:402, metaBefore:70, metaAfter:3, tables:['view_yongdian_user_scale','view_hydwsdb_mk_mc_user_scale','view_dashboard_industry_expansion_stats'], metrics:['用户数 usercount（快照）','合同容量'], dims:['user_type 用户类型','ptdate','gdj'] }, usedTables:['view_yongdian_user_scale'],
      stepTraces:[ { stepId:'step_1', queryDescription:'查询8月公变/专变客户用户数', expectedTable:'view_yongdian_user_scale', resolvedTable:'view_yongdian_user_scale', resolvedTableId:1005, rowCount:62, elapsed:700, columns:[ {name:'ptdate',role:'dimension',filter:'2026-08-01 ~ 2026-08-31',resolved:{id:501,name:'日期 ptdate'}}, {name:'user_type',role:'dimension',filter:'in [公变客户, 专线专变客户]',resolved:{id:560,name:'用户类型 user_type'}}, {name:'usercount',role:'measure',filter:'',resolved:{id:2300,name:'用户数',aggType:'snapshot'}} ], sql:"SELECT ptdate, user_type, usercount FROM view_yongdian_user_scale WHERE ptdate BETWEEN '2026-08-01' AND '2026-08-31' AND user_type IN ('公变客户','专线专变客户')" } ],
      execLog:[ {stepId:'step_1',status:'success',elapsed:700},{stepId:'step_2',status:'success',elapsed:690},{stepId:'step_3',status:'success',elapsed:22},{stepId:'step_4',status:'success',elapsed:5100} ], summaryInputRows:2, truncHead:50, truncTail:20 },
  },
];

window.MOCK_META = JSON.stringify({
  available_metrics: [
    { metric_name:'分区线损率', metric_code:'ll_rate', unit:'%', agg_type:'avg', table:'view_line_loss_substation_fenqu' },
    { metric_name:'分台区线损率', metric_code:'ll_rate', unit:'%', agg_type:'avg', table:'view_line_loss_substation_rate' },
    { metric_name:'分区输入电量', metric_code:'input_elec', unit:'万kWh', agg_type:'sum' },
    { metric_name:'日供电量', metric_code:'daypowersupply', unit:'亿千瓦时', agg_type:'sum' },
  ],
  available_dimensions: [
    { dimension_name:'区局名称', dimension_code:'dis_org_name', possible_values:['罗湖供电局','福田供电局','…'] },
    { dimension_name:'供电局', dimension_code:'gdj' },
    { dimension_name:'日期', dimension_code:'ptdate' },
  ],
  table_summaries: [
    { table_name:'view_line_loss_substation_fenqu', description:'管理线损_管理视图_分区统计包含深圳的分区线损率、分区输入电量、分区输出电量…', columns:['ptdate','dis_org_name','ll_rate','input_elec','output_elec'] },
    { table_name:'view_line_loss_substation_rate', description:'管理线损_管理视图_分台线损率', columns:['ptdate','tq_name','ll_rate'] },
    { table_name:'view_gdl', description:'供电量表-市级区级供电量日级数据表…', columns:['ptdate','gdj','daypowersupply','monthpowersupply','yearpowersupply'] },
  ],
  business_context: ['区局指罗湖/福田/南山/盐田/宝安/龙岗/坪山/光明/龙华/大鹏供电局', '线损率 =（输入电量-输出电量）/输入电量'],
  _note: '⚠ 注意：召回结果中没有 view_line_loss_management_view_by_voltage（分压线损表）',
}, null, 2);

window.MOCK_LOG = `[2026-09-29 10:42:11] [1024] [张工]
问题17：
1、用户问题：深圳各区局7月分压线损率排名，哪个区局最高？

2、执行提示词：
System Prompt：
你是一个数据分析任务拆解引擎。你的职责是将用户的自然语言数据查询问题拆解为可执行的结构化步骤。
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
★★★ 最高优先级铁律（违反即为严重错误） ★★★
【铁律 - 禁止编造表名和列名】
expected_table只能填写"可用数据库元信息"中明确列出的真实表名……
（共 18,420 字，此处省略）

User Prompt：
【当前日期】2026-09-29
【用户问题】深圳各区局7月分压线损率排名，哪个区局最高？
【可用数据库元信息】
{ "available_metrics": [ {"metric_name":"分区线损率","metric_code":"ll_rate",...} ], ... }

3、拆解过程及每一步的执行结果
step_1-查询
执行计划：
{
  "step_id": "step_1",
  "step_type": "query",
  "description": "查询2026年7月各区局线损率",
  "params": {
    "expected_table": "view_line_loss_substation_fenqu",
    "expected_columns": [
      {"name": "ptdate", "role": "dimension", "filter": [{"operator": "between", "values": ["2026-07-01","2026-07-31"]}]},
      {"name": "dis_org_name", "role": "dimension"},
      {"name": "ll_rate", "role": "measure", "agg_type": "比率"}
    ]
  }
}
执行结果：
5 行 × 3 列 [ptdate, dis_org_name, ll_rate]
...

4、错误描述：
（无）
`;

// ===================== 可调优资产元信息 =====================
window.ASSET_META = {
  TABLE_DESC:      { name:'表描述',       module:'bi-manager',  color:'#2b5cff', effect:'写库 → recall 元数据同步' },
  ENTITY_ALIAS:    { name:'指标/维度别名', module:'bi-manager',  color:'#f59e0b', effect:'写库 → recall 元数据同步' },
  AGG_TYPE:        { name:'聚合类型',     module:'bi-manager',  color:'#8b5cf6', effect:'写库，下次提问生效' },
  DIM_VALUES:      { name:'维度枚举值',   module:'bi-manager',  color:'#f59e0b', effect:'写库 → recall 元数据同步' },
  KNOWLEDGE:       { name:'智能体知识库', module:'chat-server', color:'#06b6d4', effect:'写库，下次提问生效' },
  AGENT_TABLE:     { name:'智能体绑定表', module:'bi-manager',  color:'#06b6d4', effect:'写库，下次提问生效' },
  PROMPT_DECOMPOSE:{ name:'拆解提示词',   module:'System B',    color:'#ef4444', effect:'新建版本 → prompts/switch 激活' },
  PROMPT_SUMMARY:  { name:'总结提示词',   module:'System B',    color:'#ef4444', effect:'新建版本 → prompts/switch 激活' },
  RECALL_CONFIG:   { name:'召回配置',     module:'recall',      color:'#64748b', effect:'写库，30s 热刷新' },
};

// ===================== 提示词样例（编辑器基线） =====================
window.PROMPT_MAIN_V105 = `你是一个数据分析任务拆解引擎。你的职责是将用户的自然语言数据查询问题拆解为可执行的结构化步骤。
【当前日期】{{current_date}}
【可用数据库元信息】{{database_meta}}

规则一：你必须按照预定义的JSON Schema输出结构化的步骤拆解结果。每个子步骤包含step_id、step_type、description、depends_on、params。
规则二：每个子步骤之间的数据引用关系必须通过depends_on字段明确体现。
规则三：对于数据计算步骤(compute)，你必须精确指定需要调用的计算函数名称及参数。
规则六：所有时间条件必须转换为绝对时间范围(YYYY-MM-DD格式)。

规则十四【聚合与日期处理规范】：
- 需要按月/年/周聚合时，应先使用extract_year_month/extract_year/extract_month/extract_week函数派生日期维度列，再用aggregate的group_by对派生列分组。
- 【同比计算方法】：
  方式一：分别查询两个年份的数据（两个query步骤），用subtract计算变动量，再用divide(multiply_by_100=true)计算增长率
  方式二：一次查询两年数据，用extract_year按年分组聚合，用filter分别取出两年数据，再subtract+divide
- 【环比计算方法】：
  方式一：分别查询当期和上期的数据，用subtract计算变动量，再用divide计算增长率

规则十四之二【快照类指标处理-强制】：
- 【判别方式-JSON字段】每个指标列的聚合类型已由系统治理为元信息JSON中该列的权威字段 "agg_type"（取值：snapshot / cumulative / avg / sum / null）。
- 【累计类指标处理-强制】元信息JSON中 "agg_type": "cumulative" 的指标（如 monthpowersupply 月累计供电量）本身已是系统逐期累加的结果，取期末行即得该期值，严禁再对多行求和或用日值重新累加。
- 【avg/比率类指标处理-强制】元信息JSON中 "agg_type": "avg" 的指标为比率类，严禁对多行求和。

规则二十【输出格式】：
严格输出 JSON，不得包含任何解释性文字。
{
  "steps": [ { "step_id": "step_1", "step_type": "query", "description": "...", "depends_on": [], "params": {} } ]
}`;

window.PROMPT_SUMMARY_V202 = `你是一个数据分析结果总结助手。
【用户问题】{{question}}
【分析数据】{{data}}

要求：
1. 用简洁的中文回答用户问题，直接给出结论与关键数字。
2. 涉及排名时，必须以数据中的排序结果为准。
3. 不得编造数据中不存在的数值。`;

// ===================== 调优任务 =====================
window.mkCaseProto = function(o){ return mkCase(o); };
function mkCase(o){ return Object.assign({ beforeFrom:'历史 trace', afterRequestId:'tuning-'+Math.random().toString(16).slice(2,8), elapsedBefore:18, elapsedAfter:17, degradeDecision:'' }, o); }

window.TASKS = [
  // ---- 任务1：分压线损 · 已验证待发布（PASS）----
  { id:1, taskNo:'TN20260929-003', chatId:'c9f1-…-7a21', question:'深圳各区局7月分压线损率排名，哪个区局最高？', errorType:'TABLE_SELECT', status:'VERIFIED', round:1,
    by:'王管理员', at:'2026-09-29 15:02',
    diagnosis:{ errorStepId:'step_1', actualTable:'view_line_loss_substation_fenqu', expectedTable:'view_line_loss_management_view_by_voltage', rootCause:'应选表 description 未包含「分压」「电压等级」关键词，召回未命中（召回 6 表无该表），模型退而选了分区线损表。' },
    impact:{ agents:['深圳供电局运营分析智能体','线损管理智能体'], chats:41, negative:3, regression:9 },
    changes:[
      { id:11, source:'SUGGEST', accepted:true, confidence:'高', assetType:'TABLE_DESC', targetModule:'bi-manager', targetLabel:'view_line_loss_management_view_by_voltage', field:'tb_description', applyStatus:'APPLIED', effect:'写库 → recall 元数据同步',
        reason:'R1 · 应选表不在召回结果中：从原问题抽取描述中缺失的关键词「分压」「电压等级」追加到表描述',
        before:'管理线损_管理视图_分压线损', after:'管理线损_管理视图_分压线损（按电压等级 110kV/10kV/380V 等统计的线损率；问「分压线损率」「各电压等级线损率」查本表）' },
      { id:12, source:'SUGGEST', accepted:true, confidence:'高', assetType:'KNOWLEDGE', targetModule:'chat-server', targetLabel:'深圳供电局运营分析智能体 · 知识库', field:'knowledge_element', applyStatus:'APPLIED', effect:'写库，下次提问生效',
        reason:'R2 · 实际表与应选表语义相近：追加区分规则帮助模型区分',
        before:'', after:'分区线损率按区局（dis_org_name）统计，查 view_line_loss_substation_fenqu；分压线损率按电压等级（voltage_level）统计，查 view_line_loss_management_view_by_voltage；两者不是同一张表。' },
      { id:13, source:'SUGGEST', accepted:false, confidence:'中', assetType:'ENTITY_ALIAS', targetModule:'bi-manager', targetLabel:'#2090 分压线损率 ll_rate', field:'alias', applyStatus:null, effect:'写库 → recall 元数据同步',
        reason:'R5 · 为应选指标追加原问题中的指代词（embedding 相似度 0.83，请确认）',
        before:'ll_rate', after:'ll_rate, 电压等级线损率' },
    ],
    snapshots:[ { id:1, assetType:'TABLE_DESC', label:'view_line_loss_management_view_by_voltage', field:'tb_description', at:'2026-09-29 15:05' }, { id:2, assetType:'KNOWLEDGE', label:'深圳供电局运营分析智能体 · 知识库', field:'knowledge_element（新增条目）', at:'2026-09-29 15:05' } ],
    verify:{ conclusion:'PASS', total:14, done:14, originFixed:true, regTotal:9, regPass:9, degraded:0, improved:3, elapsedBefore:18.4, elapsedAfter:17.9, tokens:186, startedAt:'15:05:12', finishedAt:'15:09:48',
      cases:[
        mkCase({ id:1, caseType:'ORIGIN', sourceChatId:'c9f1-…-7a21', question:'深圳各区局7月分压线损率排名，哪个区局最高？', expected:{ table:'view_line_loss_management_view_by_voltage', metrics:['分压线损率'], dims:['voltage_level'] },
          before:{ table:'view_line_loss_substation_fenqu', metrics:['分区线损率'], dims:['ptdate','dis_org_name'], steps:'query→top_n→summarize', status:'success', answer:'龙岗供电局 4.60% 最高…' },
          after:{ table:'view_line_loss_management_view_by_voltage', metrics:['分压线损率'], dims:['ptdate','dis_org_name','voltage_level'], steps:'query→top_n→summarize', status:'success', answer:'2026年7月各区局分压线损率：交流380V 层级龙岗供电局 4.9% 最高…' },
          beforePass:false, afterPass:true, verdict:'FIXED', judge:{ table:{ok:true,msg:'命中期望表'}, metrics:{ok:true,msg:'分压线损率 ✓'}, dims:{ok:true,msg:'含 voltage_level'}, status:{ok:true,msg:'success'}, answer:{ok:true,msg:'无期望关键词，跳过'} }, llmJudge:'答案与数据自洽，提及电压等级维度' }),
        mkCase({ id:2, caseType:'SIMILAR', sourceChatId:'x1', question:'8月各电压等级线损率同比', expected:{ table:'view_line_loss_management_view_by_voltage', metrics:['分压线损率'] },
          before:{ table:'view_line_loss_substation_fenqu', metrics:['分区线损率'], dims:['ptdate'], steps:'query×2→subtract→divide→summarize', status:'success', answer:'…' }, after:{ table:'view_line_loss_management_view_by_voltage', metrics:['分压线损率'], dims:['ptdate','voltage_level'], steps:'query×2→subtract→divide→summarize', status:'success', answer:'…' },
          beforePass:false, afterPass:true, verdict:'IMPROVED', judge:{ table:{ok:true,msg:'命中'}, metrics:{ok:true,msg:'✓'}, dims:{ok:true,msg:'✓'}, status:{ok:true,msg:'success'}, answer:{ok:true,msg:'跳过'} } }),
        mkCase({ id:3, caseType:'SIMILAR', sourceChatId:'x2', question:'110kV 线路本月线损率是多少', expected:{ table:'view_line_loss_management_view_by_voltage', metrics:['分压线损率'] },
          before:{ table:'view_line_loss_substation_fenqu', metrics:['分区线损率'], dims:['ptdate'], steps:'query→summarize', status:'success', answer:'…' }, after:{ table:'view_line_loss_management_view_by_voltage', metrics:['分压线损率'], dims:['ptdate','voltage_level'], steps:'query→summarize', status:'success', answer:'…' },
          beforePass:false, afterPass:true, verdict:'IMPROVED', judge:{ table:{ok:true,msg:'命中'}, metrics:{ok:true,msg:'✓'}, dims:{ok:true,msg:'✓'}, status:{ok:true,msg:'success'}, answer:{ok:true,msg:'跳过'} } }),
        mkCase({ id:4, caseType:'SIMILAR', sourceChatId:'x3', question:'分压线损和分区线损哪个高', expected:null,
          before:{ table:'view_line_loss_substation_fenqu', metrics:['分区线损率'], dims:['ptdate'], steps:'query→summarize', status:'success', answer:'…' }, after:{ table:'view_line_loss_management_view_by_voltage + view_line_loss_substation_fenqu', metrics:['分压线损率','分区线损率'], dims:['ptdate'], steps:'query×2→join→summarize', status:'success', answer:'…' },
          beforePass:false, afterPass:true, verdict:'IMPROVED', judge:{ table:{ok:true,msg:'无期望，变化检测：新增分压表'}, metrics:{ok:true,msg:'新增分压线损率'}, dims:{ok:true,msg:'—'}, status:{ok:true,msg:'success'}, answer:{ok:true,msg:'跳过'} } }),
        mkCase({ id:5, caseType:'SIMILAR', question:'上月分区线损率超过阈值的区局', expected:null,
          before:{ table:'view_line_loss_substation_fenqu', metrics:['分区线损率','上限阈值'], dims:['dis_org_name'], steps:'query→filter→summarize', status:'success', answer:'…' }, after:{ table:'view_line_loss_substation_fenqu', metrics:['分区线损率','上限阈值'], dims:['dis_org_name'], steps:'query→filter→summarize', status:'success', answer:'…' },
          beforePass:true, afterPass:true, verdict:'UNCHANGED', judge:{ table:{ok:true,msg:'无变化'}, metrics:{ok:true,msg:'无变化'}, dims:{ok:true,msg:'无变化'}, status:{ok:true,msg:'success'}, answer:{ok:true,msg:'跳过'} } }),
      ].concat(['各区局7月分区线损率排名','深圳7月线损率是多少','福田供电局上月分区线损率','分台区线损率最高的台区TOP10','各区局输入电量与输出电量','6月线损率环比','分区线损率连续3月上升的区局','月度线损率趋势','罗湖供电局年累计线损率'].map((q,i)=>mkCase({ id:10+i, caseType:'REGRESSION', question:q, expected:{ table:'view_line_loss_substation_fenqu', metrics:['分区线损率'] },
          before:{ table:'view_line_loss_substation_fenqu', metrics:['分区线损率'], dims:['ptdate','dis_org_name'], steps:'query→summarize', status:'success', answer:'…' }, after:{ table:'view_line_loss_substation_fenqu', metrics:['分区线损率'], dims:['ptdate','dis_org_name'], steps:'query→summarize', status:'success', answer:'…' },
          beforePass:true, afterPass:true, verdict:'PASS', judge:{ table:{ok:true,msg:'命中'}, metrics:{ok:true,msg:'✓'}, dims:{ok:true,msg:'✓'}, status:{ok:true,msg:'success'}, answer:{ok:true,msg:'✓'} } })))
    },
    audit:[ { at:'2026-09-29 15:02', by:'王管理员', text:'保存定位结论并生成 3 条调优建议' }, { at:'2026-09-29 15:05', by:'王管理员', text:'采纳 2 条建议，执行并验证（拍摄 2 个资产快照）', type:'primary' }, { at:'2026-09-29 15:09', by:'系统', text:'验证完成：14 题，原题修复，回归 9/9，退化 0', type:'success' } ],
  },
  // ---- 任务2：月累计供电量 · 已发布 ----
  { id:2, taskNo:'TN20260929-002', chatId:'b3e0-…-19cd', question:'今年以来深圳月累计供电量同比增长多少？', errorType:'DECOMPOSE', status:'PUBLISHED', round:2,
    by:'王管理员', at:'2026-09-29 14:30', publishedAt:'2026-09-29 16:40', observe:{ days:1, negative:0 },
    approval:{ submittedAt:'2026-09-29 16:33', result:'APPROVED', by:'赵超管', at:'2026-09-29 16:40', comment:'退化题已核对为期望值口径问题，同意发布', confirmNote:'退化题「上月供电量」为历史期望值口径歧义，已更新回归集期望为日供电量求和' },
    diagnosis:{ errorStepId:'step_3', rootCause:'monthpowersupply 为 cumulative 类型，模型对 1-9 月每月的月累计值又做了 sum，导致重复累加；且对比期取了 2025 全年而非 1-9 月。' },
    impact:{ agents:['深圳供电局运营分析智能体'], chats:128, negative:5, regression:15 },
    changes:[
      { id:21, source:'SUGGEST', accepted:true, confidence:'高', assetType:'AGG_TYPE', targetModule:'bi-manager', targetLabel:'view_gdl.monthpowersupply 月累计供电量', field:'summary', applyStatus:'PUBLISHED', effect:'写库，下次提问生效', reason:'R8 · trace 中该列 agg_type 为空，根因提到「累计」被 sum', before:'（空）', after:'cumulative' },
      { id:22, source:'SUGGEST', accepted:true, confidence:'高', assetType:'AGG_TYPE', targetModule:'bi-manager', targetLabel:'view_gdl.yearpowersupply 年累计供电量', field:'summary', applyStatus:'PUBLISHED', effect:'写库，下次提问生效', reason:'R8 · 同表同类列一并修正', before:'（空）', after:'cumulative' },
      { id:23, source:'MANUAL', accepted:true, assetType:'PROMPT_DECOMPOSE', targetModule:'System B', targetLabel:'prompt-main', field:'content', applyStatus:'PUBLISHED', effect:'新建版本 v1.0.6 → prompts/switch 激活', reason:'手动：规则十四之二补充「同比对比期区间必须与本期对齐」示例', before:'v1.0.5', after:'v1.0.6', draftVersion:'v1.0.6', diffAdd:4, diffDel:0 },
    ],
    snapshots:[ { id:3, assetType:'AGG_TYPE', label:'view_gdl.monthpowersupply', field:'summary', at:'2026-09-29 16:10' }, { id:4, assetType:'AGG_TYPE', label:'view_gdl.yearpowersupply', field:'summary', at:'2026-09-29 16:10' }, { id:5, assetType:'PROMPT_DECOMPOSE', label:'prompt-main', field:'active_version', version:'v1.0.5', at:'2026-09-29 16:10' } ],
    verify:{ conclusion:'PASS_WITH_WARN', total:22, done:22, originFixed:true, regTotal:15, regPass:14, degraded:1, improved:4, elapsedBefore:24.1, elapsedAfter:22.3, tokens:402, startedAt:'16:10:30', finishedAt:'16:31:02',
      cases:[
        mkCase({ id:1, caseType:'ORIGIN', sourceChatId:'b3e0-…-19cd', question:'今年以来深圳月累计供电量同比增长多少？', expected:{ table:'view_gdl', metrics:['月累计供电量'], answerKeywords:['1-9月','2025年同期'] },
          before:{ table:'view_gdl', metrics:['月累计供电量'], dims:['ptdate'], steps:'query×2→aggregate(sum)→subtract→divide→summarize', status:'success', answer:'2026年1-9月累计 1,203.6 亿千瓦时，同比增长 62.3%' }, after:{ table:'view_gdl', metrics:['年累计供电量'], dims:['ptdate'], steps:'query×2→latest_data→subtract→divide→summarize', status:'success', answer:'2026年1-9月累计供电量 741.2 亿千瓦时，较2025年同期（1-9月）增长 5.8%' },
          beforePass:false, afterPass:true, verdict:'FIXED', judge:{ table:{ok:true,msg:'命中'}, metrics:{ok:true,msg:'改用年累计列取期末值，口径正确'}, dims:{ok:true,msg:'✓'}, status:{ok:true,msg:'success'}, answer:{ok:true,msg:'含「1-9月」「2025年同期」'} }, llmJudge:'增长率 5.8% 与两期数值一致' }),
        mkCase({ id:2, caseType:'REGRESSION', question:'深圳上月供电量是多少', expected:{ table:'view_gdl', metrics:['月累计供电量'], answerKeywords:['8月'] },
          before:{ table:'view_gdl', metrics:['月累计供电量'], dims:['ptdate'], steps:'query→aggregate(sum)→summarize', status:'success', answer:'2026年8月供电量 92.4 亿千瓦时' }, after:{ table:'view_gdl', metrics:['日供电量'], dims:['ptdate'], steps:'query→aggregate(sum)→summarize', status:'success', answer:'2026年8月供电量 92.1 亿千瓦时' },
          beforePass:true, afterPass:false, verdict:'DEGRADED', judge:{ table:{ok:true,msg:'命中'}, metrics:{ok:false,msg:'期望 月累计供电量，实际 日供电量'}, dims:{ok:true,msg:'✓'}, status:{ok:true,msg:'success'}, answer:{ok:true,msg:'含「8月」'} }, llmJudge:'答案数值 92.1 与 sum(日供电量) 一致；与历史期望 92.4 差 0.3%，疑为数据修正差异' }),
      ].concat(Array.from({length:14},(_,i)=>mkCase({ id:20+i, caseType: i<3?'SIMILAR':'REGRESSION', question:['年累计供电量同比','本月供电量环比','各区局年累计供电量','昨天供电量','深圳本周供电量','去年全年供电量','各供电局月供电量排名','9月1-15日供电量','供电量最高的一天','上月各区局供电量','今年供电量趋势','供电量连续增长天数','月累计供电量最高的月份','7月与8月供电量对比'][i], expected:{ table:'view_gdl', metrics:['供电量'] },
          before:{ table:'view_gdl', metrics:['供电量'], dims:['ptdate'], steps:'…', status:'success', answer:'…' }, after:{ table:'view_gdl', metrics:['供电量'], dims:['ptdate'], steps:'…', status:'success', answer:'…' },
          beforePass: i>=3, afterPass:true, verdict: i<3?'IMPROVED':'PASS', judge:{ table:{ok:true,msg:'命中'}, metrics:{ok:true,msg:'✓'}, dims:{ok:true,msg:'✓'}, status:{ok:true,msg:'success'}, answer:{ok:true,msg:'✓'} } })))
    },
    conflict:null,
    audit:[ { at:'2026-09-29 14:30', by:'王管理员', text:'保存定位结论并生成 2 条调优建议' }, { at:'2026-09-29 14:35', by:'王管理员', text:'第 1 轮执行并验证', type:'primary' }, { at:'2026-09-29 15:40', by:'系统', text:'第 1 轮验证：原题未修复（对比期仍取全年）→ FAIL', type:'danger' }, { at:'2026-09-29 16:08', by:'王管理员', text:'修改建议：手动追加拆解提示词 v1.0.6，进入第 2 轮' }, { at:'2026-09-29 16:10', by:'王管理员', text:'第 2 轮执行并验证（拍摄 3 个资产快照）', type:'primary' }, { at:'2026-09-29 16:31', by:'系统', text:'第 2 轮验证：22 题，原题修复，回归 14/15，退化 1 → PASS_WITH_WARN', type:'warning' }, { at:'2026-09-29 16:33', by:'王管理员', text:'提交超级管理员审批（确认说明：退化题「上月供电量」为历史期望值口径歧义，已更新回归集期望为日供电量求和）', type:'primary' }, { at:'2026-09-29 16:40', by:'赵超管', text:'审批通过：退化题已核对为期望值口径问题，同意发布 → 系统自动发布', type:'success' }, { at:'2026-09-29 16:41', by:'系统', text:'线上复验原问题：通过', type:'success' } ],
  },
  // ---- 任务3：总结错误 · 已回退 ----
  { id:3, taskNo:'TN20260927-001', chatId:'40aa-…-8e12', question:'深圳9月日供电量最高的一天是哪天', errorType:'SUMMARY', status:'ROLLED_BACK', round:1,
    by:'王管理员', at:'2026-09-27 17:10', publishedAt:'2026-09-27 18:20', rolledBackAt:'2026-09-28 09:15',
    approval:{ submittedAt:'2026-09-27 17:50', result:'APPROVED', by:'赵超管', at:'2026-09-27 18:20', comment:'', confirmNote:'' }, rollbackReason:'发布后观察期内出现 3 条同类 👎：总结提示词 v2.0.3 过度强调「引用排序首行」，导致多实体问题总结只提第一名。',
    diagnosis:{ errorStepId:'step_3', rootCause:'总结截断规则 head 50 行导致排序后的首行未进入总结上下文；总结模型引用了截断前的行。' },
    impact:{ agents:['深圳供电局运营分析智能体','线损管理智能体'], chats:612, negative:4, regression:20 },
    changes:[
      { id:31, source:'MANUAL', accepted:true, assetType:'PROMPT_SUMMARY', targetModule:'System B', targetLabel:'prompt-summary', field:'content', applyStatus:'ROLLED_BACK', effect:'新建版本 v2.0.3 → prompts/switch 激活', reason:'手动：总结时优先引用 top_n/排序结果的首行', before:'v2.0.2', after:'v2.0.3', draftVersion:'v2.0.3', diffAdd:1, diffDel:0 },
    ],
    snapshots:[ { id:6, assetType:'PROMPT_SUMMARY', label:'prompt-summary', field:'active_version', version:'v2.0.2', at:'2026-09-27 17:30' } ],
    verify:{ conclusion:'PASS', total:12, done:12, originFixed:true, regTotal:10, regPass:10, degraded:0, improved:1, elapsedBefore:12.9, elapsedAfter:13.4, tokens:150, startedAt:'17:30:05', finishedAt:'17:41:50', cases:[
      mkCase({ id:1, caseType:'ORIGIN', sourceChatId:'40aa-…-8e12', question:'深圳9月日供电量最高的一天是哪天', expected:{ table:'view_gdl', metrics:['日供电量'], answerKeywords:['9月5日','4.91'] },
        before:{ table:'view_gdl', metrics:['日供电量'], dims:['ptdate'], steps:'query→top_n→summarize', status:'success', answer:'…9月3日，达 4.82 亿千瓦时' }, after:{ table:'view_gdl', metrics:['日供电量'], dims:['ptdate'], steps:'query→top_n→summarize', status:'success', answer:'…9月5日，达 4.91 亿千瓦时' },
        beforePass:false, afterPass:true, verdict:'FIXED', judge:{ table:{ok:true,msg:'命中'}, metrics:{ok:true,msg:'✓'}, dims:{ok:true,msg:'✓'}, status:{ok:true,msg:'success'}, answer:{ok:true,msg:'含「9月5日」「4.91」'} } }) ] },
    audit:[ { at:'2026-09-27 17:10', by:'王管理员', text:'保存定位结论；总结类错误无自动建议，手动追加提示词变更' }, { at:'2026-09-27 17:30', by:'王管理员', text:'执行并验证', type:'primary' }, { at:'2026-09-27 17:41', by:'系统', text:'验证完成：12 题全部通过', type:'success' }, { at:'2026-09-27 17:50', by:'王管理员', text:'提交超级管理员审批', type:'primary' }, { at:'2026-09-27 18:20', by:'赵超管', text:'审批通过 → 系统自动发布', type:'success' }, { at:'2026-09-28 09:02', by:'系统', text:'观察期告警：24h 内出现 3 条「总结错误」负反馈（阈值 2）', type:'danger' }, { at:'2026-09-28 09:15', by:'王管理员', text:'回退已发布（无需审批）：prompt-summary 切回 v2.0.2', type:'warning' }, { at:'2026-09-28 09:15', by:'系统', text:'已通知超级管理员：TN20260927-001 已回退' } ],
  },
  // ---- 任务5：待审批 ----
  { id:5, taskNo:'TN20260930-002', chatId:'7d4a-…-c0e8', question:'福田供电局昨天的终端覆盖率和覆盖用户数分别是多少？', errorType:'METRIC_DIM.METRIC', status:'PENDING_APPROVAL', round:1,
    by:'李管理员', at:'2026-09-30 10:20',
    approval:{ submittedAt:'2026-09-30 11:05', result:null, by:null, at:null, comment:'', confirmNote:'' },
    diagnosis:{ errorStepId:'step_1', rootCause:'模型把「覆盖用户数」拆成列名 cover_user_cnt，实际列为 cover_user_count，未映射导致 compute 失败。' },
    impact:{ agents:['深圳供电局运营分析智能体'], chats:19, negative:1, regression:5 },
    changes:[
      { id:51, source:'SUGGEST', accepted:true, confidence:'中', assetType:'ENTITY_ALIAS', targetModule:'bi-manager', targetLabel:'#2211 覆盖用户数 cover_user_count', field:'alias', applyStatus:'APPLIED', effect:'写库 → recall 元数据同步', reason:'R7 · 未映射列 cover_user_cnt 作为别名加入最接近实体（相似度 0.91）', before:'cover_user_count', after:'cover_user_count, cover_user_cnt, 覆盖用户数, 终端覆盖用户数' },
    ],
    snapshots:[ { id:9, assetType:'ENTITY_ALIAS', label:'#2211 cover_user_count', field:'alias', at:'2026-09-30 10:25' } ],
    verify:{ conclusion:'PASS', total:8, done:8, originFixed:true, regTotal:5, regPass:5, degraded:0, improved:1, elapsedBefore:31.7, elapsedAfter:19.2, tokens:98, startedAt:'10:25:10', finishedAt:'10:31:40', cases:[
      mkCase({ id:1, caseType:'ORIGIN', sourceChatId:'7d4a-…-c0e8', question:'福田供电局昨天的终端覆盖率和覆盖用户数分别是多少？', expected:{ table:'view_mk_mc_metering_coverage', metrics:['终端覆盖率','覆盖用户数'] },
        before:{ table:'view_mk_mc_metering_coverage', metrics:['终端覆盖率'], dims:['ptdate','org_name'], steps:'query→latest_data(失败)→summarize', status:'partial', answer:'…覆盖用户数数据获取失败' }, after:{ table:'view_mk_mc_metering_coverage', metrics:['终端覆盖率','覆盖用户数'], dims:['ptdate','org_name'], steps:'query→latest_data→aggregate→join→summarize', status:'success', answer:'福田供电局 2026-09-27 终端覆盖率 98.7%，覆盖用户数 412,380 户' },
        beforePass:false, afterPass:true, verdict:'FIXED', judge:{ table:{ok:true,msg:'命中'}, metrics:{ok:true,msg:'两指标均映射成功'}, dims:{ok:true,msg:'✓'}, status:{ok:true,msg:'success'}, answer:{ok:true,msg:'跳过'} } }) ] },
    audit:[ { at:'2026-09-30 10:20', by:'李管理员', text:'保存定位结论并生成 1 条调优建议' }, { at:'2026-09-30 10:25', by:'李管理员', text:'执行并验证', type:'primary' }, { at:'2026-09-30 10:31', by:'系统', text:'验证完成：8 题全部通过', type:'success' }, { at:'2026-09-30 11:05', by:'李管理员', text:'提交超级管理员审批', type:'primary' } ],
  },
  // ---- 任务4：验证中 ----
  { id:4, taskNo:'TN20260930-001', chatId:'91c3-…-2b77', question:'公变客户和专变客户8月用户数各多少，环比如何', errorType:'METRIC_DIM.DIMENSION', status:'VERIFYING', round:1,
    by:'李管理员', at:'2026-09-30 09:40',
    diagnosis:{ errorStepId:'step_1', rootCause:'user_type 枚举值缺「公线专变客户」「公变客户」细分项，模型按字面只筛了「公变客户」一项。' },
    impact:{ agents:['深圳供电局运营分析智能体'], chats:37, negative:2, regression:6 },
    changes:[
      { id:41, source:'SUGGEST', accepted:true, confidence:'高', assetType:'DIM_VALUES', targetModule:'bi-manager', targetLabel:'#560 用户类型 user_type', field:'value_entries', applyStatus:'APPLIED', effect:'写库 → recall 元数据同步', reason:'R6 · filter 值未命中：从源表采集补齐枚举值', before:'公变客户, 专线专变客户, 趸售关口户', after:'公变客户, 公线专变客户, 专线专变客户, 趸售关口户, 趸售用户' },
      { id:42, source:'SUGGEST', accepted:true, confidence:'高', assetType:'KNOWLEDGE', targetModule:'chat-server', targetLabel:'深圳供电局运营分析智能体 · 知识库', field:'knowledge_element', applyStatus:'APPLIED', effect:'写库，下次提问生效', reason:'R10 · 口径说明', before:'', after:'「公变客户」口径包含 公变客户 + 公线专变客户 两类；「专变客户」指 专线专变客户。' },
    ],
    snapshots:[ { id:7, assetType:'DIM_VALUES', label:'#560 user_type', field:'value_entries', at:'2026-09-30 09:45' }, { id:8, assetType:'KNOWLEDGE', label:'知识库（新增条目）', field:'knowledge_element', at:'2026-09-30 09:45' } ],
    verify:{ conclusion:null, total:10, done:6, originFixed:null, regTotal:6, regPass:0, degraded:0, improved:0, cases:[] },
    audit:[ { at:'2026-09-30 09:40', by:'李管理员', text:'保存定位结论并生成 2 条调优建议' }, { at:'2026-09-30 09:45', by:'李管理员', text:'执行并验证', type:'primary' } ],
  },
];
