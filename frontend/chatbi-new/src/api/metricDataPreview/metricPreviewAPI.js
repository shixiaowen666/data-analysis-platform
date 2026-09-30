import request from '@/utils/request'

// 登录方法
export function getMetricsTreeAPI(data) {
  return request({
    //url: '/report-preview/field-tree',
    url: '/api/v1/report-preview/dim-metric',

    method: 'post',
    data: data
  })
}

//获取指标预览
/*export function getMetricsPreviewAPI(data) {
    return request({
      url: '/report-preview/query',
      headers: {
        isToken: false
      },
      method: 'post',
      data: data
    })
  }*/

//获取指标预览
/*export function getMetricsPreviewAPI(data) {
  return request({
    url: '/api/v1/chat-server/group/preview',

    method: 'POST',
    data: data
  })
}*/

//指标数据预览中的预览
export function getMetricsDataPreviewAPI(data) {
  return request({
    url: '/api/v1/chat-server/getdata',
    // headers: {
    //   isToken: false
    // },
    method: 'POST',
    data: data
  })
}


export const granularityEnum = [
  { value: 'day', name: '日' },
  { value: 'week', name: '周' },
  { value: 'month', name: '月' },
  { value: 'quarter', name: '季' },
  { value: 'year', name: '年' },
];

export const operatorEnum = [
  { value: 2, name: '等于', symbol: '=' },
  { value: 7, name: '不等于', symbol: '≠' },
  { value: 0, name: '大于', symbol: '>' },
  { value: 3, name: '大于等于', symbol: '≥' },
  { value: 1, name: '小于', symbol: '<' },
  { value: 4, name: '小于等于', symbol: '≤' },
  { value: 5, name: '包含于', symbol: 'in' },
  { value: 6, name: '不包含于', symbol: 'not in' },
  { value: 8, name: '模糊匹配', symbol: 'like' },
  { value: 9, name: '模糊排除', symbol: 'not like' },
];


export const chartTypeEnum = {
  1:{value: 1,name: '表格' },
  2: {value: 2,name: '柱状图' },
  3: {value: 3,name: '折线图' },
  4:{value: 4,name: '饼图' },
  //5: {value: 5,name: '指标卡' },
  //6: {value: 6,name: '交叉表' },
}



export const orderEnum = [
  {value: 'desc',name: '倒序DESC'},
  {value: 'asc',name: '正序ASC' },
]

export const filterTypeEnum = {
  'dim': {value: 0,name: '维度'},
  'metric':{value: 1,name: '指标' },
}