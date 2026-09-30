import { Message } from 'element-ui';
import {isMobile} from "@/utils/common"
import { Notify, Toast } from 'vant';

/**
 * 统一提示服务（替代原项目 Element Message / Notification / Vant Notify 混用）
 */
const DURATION = 2400;

function notify(type, message, duration) {
  if (!message) return;
  if(isMobile()){
      Notify({
    type,
    message: String(message),
    duration: duration || DURATION,
  });

  }
  else{
    if(type == 'danger') type = 'error'
    if(type == 'primary') type = 'info'
    
    Message({
      type,
      message: String(message),
      duration: duration || DURATION,
    });
  }
}

export const toast = {
  success(message, duration) {
    notify('success', message, duration);
  },
  error(message, duration) {
    notify('danger', message, duration || 2800);
  },
  warn(message, duration) {
    notify('warning', message, duration);
  },
  info(message, duration) {
    notify('primary', message, duration);
  },
  /** 轻量居中提示（不遮挡顶栏） */
  tip(message, duration) {
    Toast({ message: String(message), duration: duration || 1600, position: 'middle' });
  },
  loading(message = '处理中') {
    return Toast.loading({
      message,
      forbidClick: true,
      duration: 0,
      className: 'app-toast-loading',
    });
  },
  clear() {
    Toast.clear();
  },

};

export default toast;
