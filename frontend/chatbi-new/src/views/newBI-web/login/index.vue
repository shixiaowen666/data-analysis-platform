<template>
  <div class="login">
    <div class="login__scroll u-scroll-y u-no-scrollbar">
      <!-- 品牌区 -->
      <header class="login__brand fx-fade-up">
        <div class="login__logo">
          <span class="login__logo-orbit"></span>
          <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="1" y="1" width="30" height="30" rx="9" fill="url(#lgrad)" />
            <path d="M9 19h3.4v5H9z" fill="#fff" fill-opacity=".92" />
            <path d="M14.3 12.4h3.4V24h-3.4z" fill="#fff" />
            <path d="M19.6 15.6H23V24h-3.4z" fill="#fff" fill-opacity=".92" />
            <path d="M9 8.6h14v1.9H9z" fill="#fff" fill-opacity=".62" />
            <defs>
              <linearGradient id="lgrad" x1="1" y1="1" x2="31" y2="31" gradientUnits="userSpaceOnUse">
                <stop stop-color="#3b7bf0" />
                <stop offset="1" stop-color="#22b8cf" />
              </linearGradient>
            </defs>
          </svg>
        </div>

        <h1 class="login__title">智能问数</h1>
        <p class="login__sub">数据分析平台 · Smart Data Query</p>

        <div class="login__tags">
          <span class="login__tag"><app-icon name="zap" :size="12" />智能</span>
          <span class="login__tag"><app-icon name="gauge" :size="12" />高效</span>
          <span class="login__tag"><app-icon name="shield-check" :size="12" />安全</span>
        </div>
      </header>

      <!-- 表单卡 -->
      <section class="login__card fx-fade-up fx-d2" :class="{ 'fx-shake': shaking }">
        <div class="login__cardhead">
          <h2>账号登录</h2>
          <p>用自然语言提问，让数据主动回答</p>
        </div>

        <div class="login__field" :class="{ 'is-focus': focus === 'u', 'is-error': errors.username }">
          <app-icon name="user" :size="17" class="login__ficon" />
          <input
            v-model.trim="form.username"
            class="login__input"
            type="text"
            autocomplete="username"
            placeholder="请输入用户名"
            enterkeyhint="next"
            @focus="focus = 'u'"
            @blur="focus = ''"
            @input="errors.username = ''"
          />
          <button v-if="form.username" class="login__clear u-tap" @click="form.username = ''">
            <app-icon name="close" :size="12" :stroke-width="2.4" />
          </button>
        </div>
        <transition name="fx-collapse">
          <p v-if="errors.username" class="login__err">
            <app-icon name="alert-circle" :size="12" />{{ errors.username }}
          </p>
        </transition>

        <div class="login__field" :class="{ 'is-focus': focus === 'p', 'is-error': errors.password }">
          <app-icon name="lock" :size="17" class="login__ficon" />
          <input
            v-model.trim="form.password"
            class="login__input"
            :type="showPwd ? 'text' : 'password'"
            autocomplete="current-password"
            placeholder="请输入密码"
            enterkeyhint="go"
            @focus="focus = 'p'"
            @blur="focus = ''"
            @input="errors.password = ''"
            @keyup.enter="submit"
          />
          <button class="login__eye u-tap" @click="showPwd = !showPwd">
            <app-icon :name="showPwd ? 'eye-off' : 'eye'" :size="16" />
          </button>
        </div>
        <transition name="fx-collapse">
          <p v-if="errors.password" class="login__err">
            <app-icon name="alert-circle" :size="12" />{{ errors.password }}
          </p>
        </transition>

        <div class="login__row">
          <label class="login__remember" @click="remember = !remember">
            <span class="login__check" :class="{ 'is-on': remember }">
              <app-icon v-if="remember" name="check" :size="11" :stroke-width="3" />
            </span>
            记住用户名
          </label>
          <span class="login__link">忘记密码？</span>
        </div>

        <button class="login__submit fx-press" :class="{ 'is-loading': loading }" @click="submit">
          <tech-loader v-if="loading" :size="20" inline />
          <span>{{ loading ? '登录中…' : '登 录' }}</span>
        </button>

        <!--<div v-if="isMock" class="login__demo">
          <span class="login__demo-label">演示环境</span>
          <button class="login__demo-btn" @click="fillDemo">一键填充演示账号</button>
        </div>-->
      </section>

      <!-- 能力说明 -->
      <section class="login__features fx-fade-up fx-d4">
        <div v-for="f in features" :key="f.title" class="login__feature">
          <span class="login__feicon"><app-icon :name="f.icon" :size="15" /></span>
          <div>
            <strong>{{ f.title }}</strong>
            <em>{{ f.desc }}</em>
          </div>
        </div>
      </section>

      <footer class="login__footer">
        <p>基于数据驱动的智能分析平台 · 洞察趋势 · 优化决策</p>
        <p class="login__copy">© 2026 Smart Data Query Platform</p>
      </footer>
    </div>
  </div>
</template>

<script>
import { login } from "@/api/login";
import { getRememberedName, setRememberedName, setToken, setUserInfo } from '@/utils/auth';
import toast from '@/utils/toast';
import AppIcon from '@/components/web/AppIcon.vue';
import TechLoader from '@/components/web/TechLoader.vue';
import JSEncrypt from "jsencrypt";
import { RSA_PUBLIC_KEY } from "@/utils/common";

export default {
  name: 'LoginPage',
  components: { AppIcon, TechLoader },
  data() {
    return {
      form: { username: getRememberedName(), password: '' },
      errors: { username: '', password: '' },
      focus: '',
      showPwd: false,
      remember: !!getRememberedName(),
      loading: false,
      shaking: false,
      //isMock: USE_MOCK,
      features: [
        { icon: 'brain', title: '自然语言问数', desc: '像聊天一样查数据，无需写 SQL' },
        { icon: 'network', title: '多步推理执行', desc: '取数 · 计算 · 分析 · 总结全链路可见' },
        { icon: 'sliders', title: '结果可二次改写', desc: '随时调整维度、指标与筛选条件' },
      ],
    };
  },
  methods: {
    fillDemo() {
      this.form.username = '分析师';
      this.form.password = 'demo123456';
      this.errors = { username: '', password: '' };
    },
    validate() {
      this.errors.username = this.form.username ? '' : '请输入用户名';
      this.errors.password = this.form.password
        ? this.form.password.length < 4
          ? '密码长度不足'
          : ''
        : '请输入密码';
      const pass = !this.errors.username && !this.errors.password;
      if (!pass) {
        this.shaking = true;
        setTimeout(() => (this.shaking = false), 400);
      }
      return pass;
    },
    async submit() {
      if (this.loading || !this.validate()) return;

        const encryptor = new JSEncrypt();
        encryptor.setPublicKey(RSA_PUBLIC_KEY);
        const rawText = this.form.password + "|" + Date.now();
        const encryptedPassword = encryptor.encrypt(rawText);

        if (!encryptedPassword) {
          toast.error('密码加密失败')
          return;
        }

      this.loading = true;
      try {
        const res = await login({
          username: this.form.username,
          password: encryptedPassword,
        });
        if (res && (res.code === 1 || res.code === 200)) {
          const payload = res.data || {};
          const token = payload.token || payload.accessToken || payload.access_token || '';
          if (!token) {
            toast.error('登录成功但未获取到凭证，请联系管理员');
            return;
          }
          setToken(token);
          setUserInfo(payload.user || { name: this.form.username });
          setRememberedName(this.remember ? this.form.username : '');
          toast.success('登录成功');
          const redirect = this.$route.query && this.$route.query.redirect;
          this.$router.push({ path: redirect || '/web' }).catch(() => {});
        } else {
          toast.error((res && res.message) || '登录失败');
          this.shaking = true;
          setTimeout(() => (this.shaking = false), 400);
        }
      } catch (e) {
        /* 已由 request 层提示 */
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style lang="scss">
@use "@/styles/web/tokens.scss" as *;
</style>

<style scoped lang="scss">

.login {
  height: 100%;
  display: flex;
  flex-direction: column;

  &__scroll {
    flex: 1;
    min-height: 0;
    padding: calc(var(--safe-top) + 34px) 22px calc(var(--safe-bottom) + 24px);
    display: flex;
    flex-direction: column;
  }

  /* ---------- 品牌区 ---------- */
  &__brand {
    text-align: center;
    margin-bottom: 26px;
  }

  &__logo {
    position: relative;
    width: 60px;
    height: 60px;
    margin: 0 auto 14px;

    svg {
      width: 100%;
      height: 100%;
      position: relative;
      z-index: 1;
      filter: drop-shadow(0 6px 16px rgba(46, 107, 230, 0.26));
    }
  }

  &__logo-orbit {
    position: absolute;
    inset: -9px;
    border-radius: 50%;
    border: 1px dashed rgba(46, 107, 230, 0.28);
    animation: fxSpin 14s linear infinite;

    &::before {
      content: '';
      position: absolute;
      top: -3px;
      left: 50%;
      width: 5px;
      height: 5px;
      margin-left: -2.5px;
      border-radius: 50%;
      background: var(--c-cyan-500);
      box-shadow: 0 0 8px rgba(34, 184, 207, 0.6);
    }
  }

  &__title {
    margin: 0;
    font-size: 26px;
    font-weight: 700;
    letter-spacing: 0.04em;
    background: linear-gradient(135deg, #2158cc 0%, #22b8cf 100%);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  &__sub {
    margin: 6px 0 0;
    font-size: var(--fs-12);
    color: var(--c-ink-400);
    letter-spacing: 0.05em;
  }

  &__tags {
    display: flex;
    justify-content: center;
    gap: 8px;
    margin-top: 14px;
  }

  &__tag {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 25px;
    padding: 0 11px;
    border-radius: var(--r-full);
    font-size: var(--fs-11);
    color: var(--c-brand-600);
    background: rgba(255, 255, 255, 0.8);
    border: 1px solid var(--c-brand-100);
    box-shadow: var(--sh-1);
  }

  /* ---------- 表单卡 ---------- */
  &__card {
    padding: 22px 20px 20px;
    border-radius: var(--r-xl);
    background: rgba(255, 255, 255, 0.82);
    backdrop-filter: saturate(160%) blur(16px);
    -webkit-backdrop-filter: saturate(160%) blur(16px);
    border: 1px solid rgba(255, 255, 255, 0.9);
    box-shadow: var(--sh-4);
    position: relative;
    overflow: hidden;

    /* 顶部高光线 */
    &::before {
      content: '';
      position: absolute;
      top: 0;
      left: 12%;
      right: 12%;
      height: 1px;
      background: linear-gradient(
        90deg,
        transparent,
        rgba(46, 107, 230, 0.4),
        rgba(34, 184, 207, 0.4),
        transparent
      );
    }
  }

  &__cardhead {
    margin-bottom: 18px;
    h2 {
      margin: 0;
      font-size: var(--fs-18);
      font-weight: 600;
      color: var(--c-ink-900);
    }
    p {
      margin: 5px 0 0;
      font-size: var(--fs-12);
      color: var(--c-ink-400);
    }
  }

  &__field {
    display: flex;
    align-items: center;
    gap: 10px;
    height: 52px;
    padding: 0 14px;
    border-radius: var(--r-md);
    background: #fff;
    border: 1.5px solid var(--bd-base);
    transition: border-color var(--dur-fast) var(--ease-out),
      box-shadow var(--dur-fast) var(--ease-out);

    & + & {
      margin-top: 14px;
    }

    &.is-focus {
      border-color: var(--c-brand-400);
      box-shadow: var(--sh-focus);
    }

    &.is-error {
      border-color: rgba(229, 72, 77, 0.6);
    }
  }

  &__ficon {
    color: var(--c-ink-400);
    flex-shrink: 0;
  }

  &__field.is-focus &__ficon {
    color: var(--c-brand-500);
  }

  &__input {
    flex: 1;
    min-width: 0;
    height: 100%;
    border: none;
    outline: none;
    background: transparent;
    font-size: var(--fs-15);
    color: var(--c-ink-900);

    &::placeholder {
      color: var(--c-ink-300);
    }
  }

  &__clear {
    flex-shrink: 0;
    width: 19px;
    height: 19px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: var(--c-ink-200);
    color: #fff;
  }

  &__eye {
    flex-shrink: 0;
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--c-ink-400);
    &:active {
      color: var(--c-brand-500);
    }
  }

  &__err {
    margin: 7px 0 0 4px;
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: var(--fs-11);
    color: var(--c-danger);
  }

  &__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin: 16px 0 18px;
    font-size: var(--fs-12);
  }

  &__remember {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    color: var(--c-ink-600);
    min-height: 30px;
    user-select: none;
  }

  &__check {
    width: 17px;
    height: 17px;
    border-radius: 5px;
    border: 1.5px solid var(--c-ink-300);
    background: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    transition: all var(--dur-fast) var(--ease-out);

    &.is-on {
      background: var(--g-brand);
      border-color: transparent;
    }
  }

  &__link {
    color: var(--c-brand-500);
    min-height: 30px;
    display: inline-flex;
    align-items: center;
  }

  &__submit {
    width: 100%;
    height: 52px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 9px;
    border-radius: var(--r-md);
    font-size: var(--fs-16);
    font-weight: 600;
    letter-spacing: 0.14em;
    color: #fff;
    background: var(--g-brand);
    box-shadow: 0 6px 18px rgba(46, 107, 230, 0.28);

    &.is-loading {
      opacity: 0.82;
      pointer-events: none;
      letter-spacing: 0.04em;
    }
  }

  &__demo {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    margin-top: 14px;
    padding-top: 14px;
    border-top: 1px dashed var(--bd-base);
  }

  &__demo-label {
    font-size: var(--fs-11);
    color: var(--c-cyan-600);
    background: var(--c-cyan-50);
    border: 1px solid var(--c-cyan-100);
    border-radius: var(--r-full);
    padding: 2px 8px;
  }

  &__demo-btn {
    font-size: var(--fs-12);
    color: var(--c-brand-500);
    min-height: 32px;
    text-decoration: underline;
    text-decoration-color: var(--c-brand-200);
    text-underline-offset: 3px;
  }

  /* ---------- 能力说明 ---------- */
  &__features {
    margin-top: 22px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  &__feature {
    display: flex;
    align-items: flex-start;
    gap: 11px;
    padding: 12px 14px;
    border-radius: var(--r-md);
    background: rgba(255, 255, 255, 0.62);
    border: 1px solid rgba(255, 255, 255, 0.82);

    strong {
      display: block;
      font-size: var(--fs-13);
      font-weight: 600;
      color: var(--c-ink-800);
    }
    em {
      display: block;
      margin-top: 2px;
      font-style: normal;
      font-size: var(--fs-11);
      color: var(--c-ink-400);
      line-height: 1.5;
    }
  }

  &__feicon {
    flex-shrink: 0;
    width: 28px;
    height: 28px;
    border-radius: var(--r-sm);
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--c-brand-500);
    background: var(--c-brand-50);
    border: 1px solid var(--c-brand-100);
  }

  /* ---------- 页脚 ---------- */
  &__footer {
    margin-top: auto;
    padding-top: 26px;
    text-align: center;

    p {
      margin: 0;
      font-size: var(--fs-11);
      color: var(--c-ink-400);
      line-height: 1.7;
    }
  }

  &__copy {
    margin-top: 3px !important;
    color: var(--c-ink-300) !important;
  }
}

/* 小屏压缩 */
@media (max-height: 700px) {
  .login__features {
    display: none;
  }
  .login__brand {
    margin-bottom: 18px;
  }
}
</style>
